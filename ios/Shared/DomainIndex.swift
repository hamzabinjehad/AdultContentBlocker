import Foundation

/// The signed seed's core domain tier as a sorted, newline-separated file.
///
/// The file is generated at build time from the signature-verified seed (see
/// `prepare_rules.py --dns`) and its SHA-256 is checked before this index is
/// built. Lookups binary-search the bytes and compare without allocating a
/// String for each entry, so a 236,000-domain list costs a few megabytes and no
/// per-query allocations beyond the candidate suffixes.
struct DomainIndex {
    enum LoadError: Error, Equatable {
        case empty
        case tooLarge
        case invalidDomain(line: Int)
        case unsorted(line: Int)
        case tooMany
    }

    static let maximumBytes = 16 << 20
    static let maximumCount = 1_000_000
    static let maximumDomainLength = 253

    private let bytes: [UInt8]
    /// Start offset of each line, plus a sentinel at `bytes.count`.
    private let starts: [Int]

    var count: Int { starts.count - 1 }

    init(fileData: Data) throws {
        guard fileData.count <= Self.maximumBytes else { throw LoadError.tooLarge }
        let bytes = [UInt8](fileData)
        var starts: [Int] = []
        var lineStart = 0
        var previous: Range<Int>?
        for index in 0...bytes.count {
            let atEnd = index == bytes.count
            if atEnd || bytes[index] == 0x0A {
                // A trailing newline produces one empty final segment; it is not a line.
                if lineStart == index && atEnd { break }
                guard index > lineStart else { throw LoadError.invalidDomain(line: starts.count + 1) }
                try Self.validate(bytes, lineStart..<index, line: starts.count + 1)
                if let previous, !Self.less(bytes, previous, lineStart..<index) {
                    throw LoadError.unsorted(line: starts.count + 1)
                }
                starts.append(lineStart)
                guard starts.count <= Self.maximumCount else { throw LoadError.tooMany }
                previous = lineStart..<index
                lineStart = index + 1
            }
        }
        guard !starts.isEmpty else { throw LoadError.empty }
        starts.append(bytes.count)
        self.bytes = bytes
        self.starts = starts
    }

    /// True when the host, or any of its parent names, is listed. The seed's
    /// core tier holds apex domains; a listed apex covers its subdomains.
    func blocks(host: String) -> Bool {
        guard var name = Self.canonicalHost(host) else { return false }
        while true {
            if contains(name) { return true }
            guard let dot = name.firstIndex(of: ".") else { return false }
            name = String(name[name.index(after: dot)...])
            // A single label is never a listed domain (the file requires a dot).
            if !name.contains(".") { return false }
        }
    }

    func contains(_ domain: String) -> Bool {
        let query = [UInt8](domain.utf8)
        guard !query.isEmpty, query.count <= Self.maximumDomainLength else { return false }
        var low = 0
        var high = count - 1
        while low <= high {
            let middle = (low + high) / 2
            switch compare(query, line: middle) {
            case 0: return true
            case ..<0: high = middle - 1
            default: low = middle + 1
            }
        }
        return false
    }

    /// Lowercase ASCII host without a trailing dot. Anything a DNS query cannot
    /// contain is rejected here rather than normalised into a match.
    static func canonicalHost(_ raw: String) -> String? {
        var host = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        while host.hasSuffix(".") { host.removeLast() }
        guard !host.isEmpty, host.count <= maximumDomainLength else { return nil }
        var scalars = String.UnicodeScalarView()
        for scalar in host.unicodeScalars {
            let value = scalar.value
            switch value {
            case 0x41...0x5A: // uppercase A-Z
                scalars.append(Unicode.Scalar(value + 0x20)!)
            case 0x61...0x7A, 0x30...0x39, 0x2D, 0x5F, 0x2E: // a-z 0-9 - _ .
                scalars.append(scalar)
            default:
                return nil
            }
        }
        let canonical = String(scalars)
        guard canonical.contains("."),
              !canonical.hasPrefix("."), !canonical.hasSuffix("."),
              !canonical.contains("..") else { return nil }
        return canonical
    }

    // MARK: - Bytes

    private func compare(_ query: [UInt8], line: Int) -> Int {
        let start = starts[line]
        var end = starts[line + 1]
        if end > start, bytes[end - 1] == 0x0A { end -= 1 }
        var index = start
        var queryIndex = 0
        while index < end && queryIndex < query.count {
            let left = bytes[index]
            let right = query[queryIndex]
            // Compare the query to the line, not the line to the query: the
            // binary search treats a negative result as "go left".
            if left != right { return left < right ? 1 : -1 }
            index += 1
            queryIndex += 1
        }
        // The query is a prefix of the line: the line is greater.
        if index < end { return -1 }
        // The line is a prefix of the query: the query is greater.
        if queryIndex < query.count { return 1 }
        return 0
    }

    private static func less(_ bytes: [UInt8], _ left: Range<Int>, _ right: Range<Int>) -> Bool {
        var index = left.lowerBound
        var other = right.lowerBound
        while index < left.upperBound && other < right.upperBound {
            if bytes[index] != bytes[other] { return bytes[index] < bytes[other] }
            index += 1
            other += 1
        }
        return left.count < right.count
    }

    private static let dot = UInt8(ascii: ".")
    private static let hyphen = UInt8(ascii: "-")

    private static func validate(_ bytes: [UInt8], _ range: Range<Int>, line: Int) throws {
        guard range.count >= 3, range.count <= maximumDomainLength else {
            throw LoadError.invalidDomain(line: line)
        }
        var labelLength = 0
        var sawDot = false
        for index in range {
            let byte = bytes[index]
            if byte == dot {
                guard labelLength > 0, labelLength <= 63,
                      bytes[index - 1] != hyphen else {
                    throw LoadError.invalidDomain(line: line)
                }
                labelLength = 0
                sawDot = true
                continue
            }
            let isLetter = byte >= 0x61 && byte <= 0x7A
            let isDigit = byte >= 0x30 && byte <= 0x39
            let isHyphen = byte == hyphen
            guard isLetter || isDigit || isHyphen else {
                throw LoadError.invalidDomain(line: line)
            }
            if labelLength == 0, isHyphen { throw LoadError.invalidDomain(line: line) }
            labelLength += 1
        }
        guard sawDot, labelLength > 0, labelLength <= 63,
              bytes[range.upperBound - 1] != hyphen else {
            throw LoadError.invalidDomain(line: line)
        }
    }
}
