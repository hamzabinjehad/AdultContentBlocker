import Foundation

// A standalone benchmark for the socket filter's two performance-sensitive
// paths: loading a signed list, and answering `shouldBlockFlow` for one new
// flow. It compiles the SHIPPING sources — BlocklistStore, the generated
// WebProtectionPolicy and the shared guards — rather than a copy, so a change
// to the real matcher shows up here.
//
// Run it through `script/bench_macos_blocklist.sh`. It is not part of the test
// suite: timings on a shared CI runner are not evidence, and this file exists
// to make a local before/after honest.

// MARK: - Measurement helpers

private func nowNs() -> UInt64 { DispatchTime.now().uptimeNanoseconds }

private func residentMB() -> Double {
    var info = mach_task_basic_info()
    var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size
                                       / MemoryLayout<integer_t>.size)
    let result = withUnsafeMutablePointer(to: &info) {
        $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
            task_info(mach_task_self_, task_flavor_t(MACH_TASK_BASIC_INFO), $0, &count)
        }
    }
    guard result == KERN_SUCCESS else { return -1 }
    return Double(info.resident_size) / 1_048_576
}

private func median(_ values: [Double]) -> Double {
    values.sorted()[values.count / 2]
}

private func printRow(_ label: String, _ nanoseconds: Double) {
    let width = 46
    let padded = label.count >= width ? label : label + String(repeating: " ",
                                                               count: width - label.count)
    print(String(format: "%@ %10.0f ns", padded, nanoseconds))
}

// MARK: - Fixtures

private struct Fixture {
    let directory: URL
    let artifact: String
    let manifest: Data
    let signature: String
    let domains: Data
    let terms: Data

    static func seed() throws -> Fixture {
        let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
        let directory = root.appendingPathComponent("seed")
        return try Fixture(directory: directory, artifact: "domains_core.txt",
                           manifestName: "manifest.json", domainsName: "domains_core.txt")
    }

    static func full() throws -> Fixture {
        let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
        let directory = URL(fileURLWithPath:
            ProcessInfo.processInfo.environment["HISN_LIST_DIR"] ?? "dist",
            relativeTo: root)
        return try Fixture(directory: directory, artifact: "domains.packed",
                           manifestName: "manifest.json", domainsName: "domains.packed")
    }

    private init(directory: URL, artifact: String,
                 manifestName: String, domainsName: String) throws {
        self.directory = directory
        self.artifact = artifact
        manifest = try Data(contentsOf: directory.appendingPathComponent(manifestName))
        signature = try String(contentsOf: directory.appendingPathComponent("manifest.json.sig"),
                               encoding: .utf8)
        domains = try Data(contentsOf: directory.appendingPathComponent(domainsName))
        terms = try Data(contentsOf: directory.appendingPathComponent("terms.json"))
    }

    private struct ManifestShape: Decodable {
        struct Artifact: Decodable { let sha256: String }
        let artifacts: [String: Artifact]
    }

    var termsSHA256: String? {
        (try? JSONDecoder().decode(ManifestShape.self, from: manifest))?
            .artifacts["terms.json"]?.sha256
    }

    /// The first `limit` domain names, so the workload can include real blocked
    /// hosts without materialising a 135MB String.
    func sampledDomains(limit: Int) -> [String] {
        var names: [String] = []
        names.reserveCapacity(limit)
        domains.withUnsafeBytes { (raw: UnsafeRawBufferPointer) in
            guard let base = raw.baseAddress?.assumingMemoryBound(to: UInt8.self) else { return }
            var start = 0
            while start < raw.count, names.count < limit {
                let lineEnd = UnsafeRawBufferPointer(start: base + start,
                                                     count: raw.count - start)
                let newline = lineEnd.firstIndex(of: 0x0A) ?? (raw.count - start)
                if base[start] != 0x23, newline > 0 {
                    let line = UnsafeBufferPointer(start: base + start, count: newline)
                    names.append(String(decoding: line, as: UTF8.self))
                }
                start += newline + 1
            }
        }
        return names
    }
}

// MARK: - Workload

/// Ordinary allow-allowed browser traffic: a few hosts repeated, because that
/// is what a browser actually opens — dozens of flows to the same handful of
/// CDNs and APIs.
private let commonHosts = [
    "www.google.com", "mail.google.com", "accounts.google.com", "github.com",
    "api.github.com", "raw.githubusercontent.com", "cdn.jsdelivr.net",
    "fonts.gstatic.com", "www.apple.com", "ocsp.apple.com", "gateway.icloud.com",
    "outlook.office365.com", "login.microsoftonline.com", "app.slack.com",
    "www.youtube.com", "i.ytimg.com", "en.wikipedia.org", "upload.wikimedia.org",
    "duckduckgo.com", "www.bing.com", "chatgpt.com", "www.reddit.com",
]

/// Hosts that reach the keyword layer rather than the published list: one
/// innocent-token rewrite, one elongation, one substring, and the two shapes
/// that deliberately take the Unicode path.
private let keywordHosts = [
    "analytics.google.com", "tasks.office.com", "assets.example.com",
    "essex.gov.uk", "essex-porn.com", "sharmota123.example",
    "3ahira-arab.example", "bestpornsite.example",
]

private func fail(_ message: String) -> Never {
    FileHandle.standardError.write(Data((message + "\n").utf8))
    exit(1)
}

// MARK: - Measurement

// Warm caches, then take the median of five passes.
private func measure(_ store: BlocklistStore, _ hosts: [String],
                     passes: Int = 5) -> Double {
    var samples: [Double] = []
    for _ in 0..<passes {
        let start = nowNs()
        for host in hosts {
            _ = store.shouldBlockFlow(host: host, sourceAppIdentifier: { nil })
        }
        let end = nowNs()
        samples.append(Double(end - start) / Double(hosts.count))
    }
    return median(samples)
}

// MARK: - Main

@main
struct BenchBlocklist {
    static func main() {
        let mode = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "seed"
        guard mode == "seed" || mode == "full" else {
            fail("usage: \(CommandLine.arguments[0]) [seed|full]   "
                 + "(full reads dist/ or $HISN_LIST_DIR)")
        }

        let fixture: Fixture
        do {
            fixture = mode == "seed" ? try Fixture.seed() : try Fixture.full()
        } catch {
            fail("cannot read the \(mode) fixture: \(error)")
        }

        print("Hisn list benchmark — \(mode)")
        print("  domains artifact: \(fixture.artifact), "
              + "\(String(format: "%.1f", Double(fixture.domains.count) / 1_048_576)) MB")

        let store = BlocklistStore()

        let loadStart = nowNs()
        do {
            try store.load(manifestData: fixture.manifest,
                           signatureHex: fixture.signature,
                           domainsData: fixture.domains,
                           artifact: fixture.artifact)
        } catch {
            fail("load refused the fixture: \(error)")
        }
        let loadEnd = nowNs()
        print(String(format: "  load: %.1f ms, %d domains, resident %.1f MB",
                     Double(loadEnd - loadStart) / 1e6, store.domainCount, residentMB()))

        if let expected = fixture.termsSHA256 {
            do {
                try store.loadHostTerms(termsData: fixture.terms,
                                        expectedSHA256: expected)
            } catch {
                fail("keyword layer refused: \(error)")
            }
            print("  keyword layer: \(store.hostTermCount) terms")
        }

        // Workload: ordinary repeated flows, real list entries and a subdomain
        // of each, the keyword shapes, and a few deep parent walks.
        var workload = [String]()
        workload.reserveCapacity(12_000)
        for (index, host) in commonHosts.enumerated() {
            for offset in 0..<160 {
                workload.append(offset % 5 == 0
                                ? "s\(index)-\(offset).cdn.example.com" : host)
            }
        }
        let sampled = fixture.sampledDomains(limit: 1_500)
        for host in sampled {
            workload.append(host)
            workload.append("www.\(host)")
            workload.append("static.cdn.\(host)")
        }
        workload.append(contentsOf: keywordHosts)
        workload.append(contentsOf: ["a.b.c.d.google.com", "x.y.z.icloud.com",
                                     "deep.parent.walk.apple.com"])
        workload.shuffle()

        for host in workload.prefix(2_000) {
            _ = store.shouldBlockFlow(host: host, sourceAppIdentifier: { nil })
        }

        print(String(format: "  mixed workload: %.0f ns/flow (%d flows)",
                     measure(store, workload), workload.count))

        // A breakdown, so a regression points at one layer rather than the total.
        printRow("allowed host (list miss, keyword miss)", measure(store, commonHosts))
        let blocked = Array(sampled.prefix(500))
        printRow("blocked by the published list", measure(store, blocked))
        printRow("subdomain of a blocked host",
                 measure(store, blocked.map { "static.cdn.\($0)" }))
        printRow("keyword layer, ASCII host", measure(store, ["analytics.google.com",
                                                             "tasks.office.com",
                                                             "essex-porn.com",
                                                             "sharmota123.example"]))
        printRow("keyword layer, Unicode path (IDN / multi-innocent)",
                 measure(store, ["xn--mgbh0fb.example", "essex.analytics.tasks.com"]))

        store.setNetworkPolicy(strict: true, allowlist: ["apple.com"],
                               customBlocks: [], blockedApps: [])
        printRow("strict mode, allowed host", measure(store, ["www.apple.com",
                                                             "api.apple.com"]))
        printRow("strict mode, denied host", measure(store, ["www.google.com",
                                                            "api.github.com"]))

        print(String(format: "  resident after workload: %.1f MB", residentMB()))
    }
}
