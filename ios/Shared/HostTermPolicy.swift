import Foundation

/// Hisn's hostname keyword layer, shared with the Mac and the DNS resolver on
/// Android. A list can only block a name after someone has written it down; a
/// host that visibly names adult material — `arab-sex-tube`, `sharmota123` — is
/// the "registered this morning" gap this layer closes for every app whose DNS
/// question reaches the proxy.
///
/// Safety is the whole design, because a wrong verdict takes out an entire site:
///   * ambiguous terms are whole hostname tokens only, so `essex.gov.uk` never
///     equals `sex`;
///   * `never_keyword` tokens are removed from the haystack before a substring
///     test, so `milford.ct.us` does not match `milf` while
///     `milford-milfs.example` still does;
///   * `keyword_exempt_hosts` names a site and its subdomains, so JINS eyewear
///     and the island of Kos stay reachable.
/// Nothing here stores or logs what it saw.
struct HostTermPolicy {
    enum PolicyError: Error, Equatable {
        case tooManyTerms
        case emptyTerm
        case unknownKind(String)
        case invalidTerm
    }

    struct Payload: Decodable {
        struct Term: Decodable {
            let t: String
            let kind: String
        }
        let schema: Int
        let version: Int
        let host_terms: [Term]
        let never_keyword: [String]
        let keyword_exempt_hosts: [String]?
    }

    static let maximumTerms = 1_000
    static let maximumHostLength = 253

    private let tokens: Set<String>
    private let substrings: [String]
    private let neverKeyword: Set<String>
    private let exemptHosts: Set<String>

    var tokenCount: Int { tokens.count }
    var substringCount: Int { substrings.count }

    init(payload: Payload) throws {
        guard payload.schema == 1 else { throw PolicyError.invalidTerm }
        var tokens: [String] = []
        var substrings: [String] = []
        for entry in payload.host_terms {
            let term = Self.normalizedTerm(entry.t)
            guard !term.isEmpty, term.utf8.count <= Self.maximumHostLength else {
                throw PolicyError.emptyTerm
            }
            switch entry.kind {
            case "token": tokens.append(entry.t)
            case "substring": substrings.append(entry.t)
            default: throw PolicyError.unknownKind(entry.kind)
            }
        }
        if payload.host_terms.count > Self.maximumTerms { throw PolicyError.tooManyTerms }
        try self.init(tokens: tokens, substrings: substrings,
                      neverKeyword: payload.never_keyword,
                      exemptHosts: payload.keyword_exempt_hosts ?? [])
    }

    init(tokens: [String], substrings: [String], neverKeyword: [String],
         exemptHosts: [String] = []) throws {
        func normalized(_ values: [String]) throws -> [String] {
            var output: [String] = []
            output.reserveCapacity(values.count)
            for value in values {
                let term = Self.normalizedTerm(value)
                guard !term.isEmpty, term.utf8.count <= Self.maximumHostLength else {
                    throw PolicyError.emptyTerm
                }
                output.append(term)
            }
            return output
        }
        let tokenValues = try normalized(tokens)
        let substringValues = try normalized(substrings)
        let neverValues = try normalized(neverKeyword)
        guard tokenValues.count <= Self.maximumTerms,
              substringValues.count <= Self.maximumTerms,
              neverValues.count <= Self.maximumTerms else { throw PolicyError.tooManyTerms }
        self.tokens = Set(tokenValues)
        self.substrings = substringValues
        self.neverKeyword = Set(neverValues)
        var hosts: Set<String> = []
        for value in exemptHosts {
            guard let host = Self.canonicalHost(value) else { throw PolicyError.invalidTerm }
            hosts.insert(host)
        }
        self.exemptHosts = hosts
    }

    /// True when the hostname itself reads as adult material by the reviewed rules.
    func matches(_ hostname: String) -> Bool {
        guard !tokens.isEmpty || !substrings.isEmpty else { return false }
        guard let host = Self.canonicalHost(hostname), !host.isEmpty else { return false }
        if isExempt(host) { return false }

        let cleaned = TextNormalizer.normalize(host)
        guard !cleaned.isEmpty else { return false }
        var found: Set<String> = []
        for token in TextNormalizer.tokenize(cleaned) {
            found.formUnion(TextNormalizer.variants(of: token))
        }
        let innocent = found.intersection(neverKeyword)
        if !found.subtracting(neverKeyword).intersection(tokens).isEmpty { return true }
        var haystack = cleaned
        for word in innocent {
            haystack = haystack.replacingOccurrences(of: word, with: "")
        }
        return substrings.contains { haystack.contains($0) }
    }

    /// A site named with a term (jins.com, koss.com) and its subdomains. The
    /// TLD-only suffix is not exempt: `com` is not a host anyone chose.
    private func isExempt(_ host: String) -> Bool {
        guard !exemptHosts.isEmpty else { return false }
        let labels = host.split(separator: ".", omittingEmptySubsequences: false)
        guard labels.count >= 2 else { return false }
        for index in 0..<(labels.count - 1) {
            let suffix = labels[index...].joined(separator: ".")
            if exemptHosts.contains(suffix) { return true }
        }
        return false
    }

    /// Lowercase, IDN-decoded, trailing dot removed. DNS names are ASCII on the
    /// wire; decoding the punycode label gives the Arabic-script spelling the
    /// signed terms are written in.
    static func canonicalHost(_ value: String) -> String? {
        var host = value.trimmingCharacters(in: .whitespacesAndNewlines)
        while host.hasSuffix(".") { host.removeLast() }
        guard !host.isEmpty, host.utf8.count <= maximumHostLength else { return nil }
        return Punycode.decodeHost(host).lowercased()
    }

    private static func normalizedTerm(_ value: String) -> String {
        TextNormalizer.normalize(value)
    }
}
