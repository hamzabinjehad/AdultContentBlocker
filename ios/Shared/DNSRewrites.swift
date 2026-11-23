import Foundation

/// Reads the original site out of a proxy hostname that spells it in one label.
///
/// Google Translate's website proxy serves `www.example.com` at
/// `www-example-com.translate.goog`, and the AMP cache serves it at
/// `www-example-com.cdn.ampproject.org`. Both write the original name with each
/// `-` doubled and each `.` turned into `-`, and wrap a label whose third and
/// fourth characters would read `--` as `0-…-0`. A blocked site opened through
/// either proxy therefore resolves a name no list contains. Decoding the label
/// lets the same layers decide the proxied copy, and only it: the proxies' own
/// names and every site that is not blocked stay reachable.
///
/// This mirrors `android/core/.../ProxiedHost.java`. Two names are not read
/// back and are decided on their own name, as before: one too long for a label
/// (the proxy replaces it with a hash) and a Unicode original carried as a
/// punycode label, which iOS has no public IDNA encoder to turn back into the
/// list's ASCII form.
enum ProxiedHost {
    static let suffixes = [".translate.goog", ".cdn.ampproject.org"]

    /// The lowercased original hostname, or nil when this is not a readable proxy name.
    static func original(_ host: String) -> String? {
        let name = host.lowercased()
        for suffix in suffixes where name.hasSuffix(suffix) {
            let label = String(name.dropLast(suffix.count))
            // Exactly one label: a deeper name is the proxy's own infrastructure.
            guard !label.isEmpty, !label.contains(".") else { return nil }
            return decode(label)
        }
        return nil
    }

    static func decode(_ label: String) -> String? {
        guard !label.hasPrefix("xn--") else { return nil }
        var text = Array(label)
        if text.count > 5, text[0] == "0", text[1] == "-",
           text[text.count - 2] == "-", text[text.count - 1] == "0",
           text[4] == "-", text[5] == "-" {
            text = Array(text[2..<(text.count - 2)])
        }
        var host = ""
        host.reserveCapacity(text.count)
        var index = 0
        while index < text.count {
            let character = text[index]
            if character != "-" {
                host.append(character)
            } else if index + 1 < text.count, text[index + 1] == "-" {
                host.append("-")
                index += 1
            } else {
                host.append(".")
            }
            index += 1
        }
        // A name must have at least two labels, none empty, each a valid
        // hostname label: "-.translate.goog" or "a-.translate.goog" decode to
        // something no list can hold, so they are left to their own name.
        let labels = host.split(separator: ".", omittingEmptySubsequences: false)
        guard labels.count >= 2, host.count <= 253,
              labels.allSatisfy({ label in
                  !label.isEmpty && label.count <= 63 && label.first != "-" && label.last != "-"
                      && label.allSatisfy { $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-" || $0 == "_") }
              }) else { return nil }
        return host
    }
}

/// Search hostnames the DNS filter answers with a restricted CNAME.
///
/// A DNS server forces SafeSearch by answering the engine's own name with a
/// CNAME to the engine's restricted host; the engine then serves only filtered
/// results. This is the same exact list Android rewrites
/// (`android/core/.../SafeSearch.java`): Google's 187 published search domains,
/// Bing, DuckDuckGo, Yandex family search and Pixabay, each checked on
/// 23 November 2026 to serve a certificate for the original name from the
/// target's address. YouTube's documented Restricted Mode names are their own
/// group because Restricted Mode also hides comments and some ordinary videos,
/// so the person chooses it separately. Names that failed that certificate check
/// are left alone: a wrong rewrite breaks the site instead of filtering it.
enum SafeSearchTargets {
    static let google = "forcesafesearch.google.com"
    static let bing = "strict.bing.com"
    static let duckDuckGo = "safe.duckduckgo.com"
    static let youtube = "restrict.youtube.com"
    static let yandex = "familysearch.yandex.ru"
    static let pixabay = "safesearch.pixabay.com"

    /// The part after "google." of each domain in google.com/supported_domains.
    static let googleSuffixes: Set<String> = Set((
        "ad ae al am as at az ba be bf bg bi bj bs bt by ca cat cd cf cg ch ci cl cm cn co.ao co.bw "
        + "co.ck co.cr co.id co.il co.in co.jp co.ke co.kr co.ls co.ma co.mz co.nz co.th co.tz co.ug "
        + "co.uk co.uz co.ve co.vi co.za co.zm co.zw com com.af com.ag com.ar com.au com.bd com.bh "
        + "com.bn com.bo com.br com.bz com.co com.cu com.cy com.do com.ec com.eg com.et com.fj com.gh "
        + "com.gi com.gt com.hk com.jm com.kh com.kw com.lb com.ly com.mm com.mt com.mx com.my com.na "
        + "com.ng com.ni com.np com.om com.pa com.pe com.pg com.ph com.pk com.pr com.py com.qa com.sa "
        + "com.sb com.sg com.sl com.sv com.tj com.tr com.tw com.ua com.uy com.vc com.vn cv cz de dj dk "
        + "dm dz ee es fi fm fr ga ge gg gl gm gr gy hn hr ht hu ie im iq is it je jo kg ki kz la li lk "
        + "lt lu lv md me mg mk ml mn mu mv mw ne nl no nr nu pl pn ps pt ro rs ru rw sc se sh si sk sm "
        + "sn so sr st td tg tl tm tn to tt vu ws").split(separator: " ").map(String.init))

    static let search: [String: String] = {
        var names: [String: String] = [:]
        for name in ["bing.com", "www.bing.com"] { names[name] = bing }
        for name in ["duckduckgo.com", "www.duckduckgo.com"] { names[name] = duckDuckGo }
        names["images.google.com"] = google
        for name in ["yandex.ru", "yandex.com", "yandex.com.tr", "yandex.by", "yandex.kz", "yandex.uz", "ya.ru"] {
            names[name] = yandex
            names["www." + name] = yandex
        }
        for name in ["pixabay.com", "www.pixabay.com"] { names[name] = pixabay }
        return names
    }()

    /// support.google.com/a/answer/6214622 names these for DNS Restricted Mode,
    /// including the youtubei API the YouTube app itself uses.
    static let youtubeNames: Set<String> = [
        "youtube.com", "www.youtube.com", "m.youtube.com",
        "youtubei.googleapis.com", "youtube.googleapis.com", "www.youtube-nocookie.com",
    ]

    /// The restricted target for an exact hostname under the chosen options, or nil to forward.
    static func target(for host: String, options: DNSRewriteOptions) -> String? {
        var name = host.lowercased()
        if name.hasSuffix(".") { name.removeLast() }
        if options.youtubeRestricted, youtubeNames.contains(name) { return youtube }
        guard options.safeSearch else { return nil }
        if let exact = search[name] { return exact }
        let rest: Substring?
        if name.hasPrefix("www.google.") {
            rest = name.dropFirst("www.google.".count)
        } else if name.hasPrefix("google.") {
            rest = name.dropFirst("google.".count)
        } else {
            rest = nil
        }
        if let rest, googleSuffixes.contains(String(rest)) { return google }
        return nil
    }

    /// Every rewritten name a test or the Python parity check can compare.
    static var allNames: [String] {
        Array(search.keys) + Array(youtubeNames)
            + googleSuffixes.flatMap { ["google." + $0, "www.google." + $0] }
    }
}

/// Which DNS rewrites the person chose. The app writes this into the App Group;
/// the DNS proxy reads it on a short cache. A missing record is the default:
/// SafeSearch on (an adult blocker should never return explicit image search
/// results) and YouTube Restricted Mode off (it also hides comments and
/// ordinary videos, which is the person's call).
struct DNSRewriteOptions: Codable, Equatable {
    var safeSearch: Bool
    var youtubeRestricted: Bool

    static let standard = DNSRewriteOptions(safeSearch: true, youtubeRestricted: false)
    static let none = DNSRewriteOptions(safeSearch: false, youtubeRestricted: false)
    static let key = "hisn.dns.rewrites"

    /// Damaged data falls back to the standard choice, never to no rewrites:
    /// a corrupted record must not quietly turn SafeSearch off.
    static func read(defaults: UserDefaults?) -> DNSRewriteOptions {
        guard let data = defaults?.data(forKey: key),
              let options = try? JSONDecoder().decode(DNSRewriteOptions.self, from: data) else {
            return .standard
        }
        return options
    }

    func write(defaults: UserDefaults?) {
        guard let defaults, let data = try? JSONEncoder().encode(self) else { return }
        defaults.set(data, forKey: Self.key)
    }

    /// During a commitment a rewrite may be turned on but never off, the same
    /// tighten-only rule as every other saved setting.
    static func permits(current: DNSRewriteOptions, proposed: DNSRewriteOptions, locked: Bool) -> Bool {
        guard locked else { return true }
        return (proposed.safeSearch || !current.safeSearch)
            && (proposed.youtubeRestricted || !current.youtubeRestricted)
    }
}
