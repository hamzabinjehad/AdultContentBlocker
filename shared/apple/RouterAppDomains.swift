import Foundation

/// User-reviewed hostnames, not an inferred app-to-server database.
enum RouterAppDomains {
    enum Invalid: Error { case hostname }
    static func parse(_ text: String) throws -> [String] {
        let lines = text.components(separatedBy: .newlines).map { $0.trimmingCharacters(in: .whitespaces).lowercased() }
            .filter { !$0.isEmpty }
        guard lines.count <= 100 else { throw Invalid.hostname }
        let protected = ["apple.com", "icloud.com", "cloudflare.com", "cloudfront.net", "amazonaws.com",
                         "googleapis.com", "akamai.net", "azureedge.net"]
        for host in lines {
            let parts = host.split(separator: ".", omittingEmptySubsequences: false)
            guard host.utf8.count <= 253, parts.count >= 2, !protected.contains(host),
                  !["apple.com", "icloud.com"].contains(where: { host.hasSuffix("." + $0) }),
                  parts.allSatisfy({ part in
                      !part.isEmpty && part.count <= 63 && part.first != "-" && part.last != "-"
                      && part.utf8.allSatisfy { (97...122).contains($0) || (48...57).contains($0) || $0 == 45 }
                  }), parts.last!.contains(where: { $0.isLetter }) else { throw Invalid.hostname }
        }
        return Set(lines).sorted()
    }
    static func adGuardRules(_ hosts: [String]) -> String {
        "! Hisn: manually reviewed domains; affects EVERY client using this resolver.\n" +
        "! No time schedule or app-usage budget. Not proof of complete app blocking.\n" +
        hosts.map { "||\($0)^" }.joined(separator: "\n") + "\n"
    }
}
