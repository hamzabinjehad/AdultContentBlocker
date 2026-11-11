import Foundation

/// Bounds-checks an upstream DNS reply and follows the aliases it can carry:
/// CNAME, DNAME and the HTTPS/SVCB `TargetName`. Android applies the same rule
/// in `DnsReplyGuard.java`; without it a first-party name can CNAME to a listed
/// domain and the allowed question would forward the listed content.
///
/// Only the names an answer actually refers to are followed. Address records
/// are length-checked but not turned into verdicts: Hisn filters names, not
/// addresses. A malformed reply is an error, so the caller fails closed with
/// SERVFAIL instead of forwarding something it could not check.
enum DNSReplyGuard {
    enum Invalid: Error { case structure }

    private struct Name {
        let value: String
        let end: Int
    }

    static func blocks(_ reply: Data, isBlocked: (String) -> Bool) throws -> Bool {
        let bytes = [UInt8](reply)
        guard bytes.count >= 12, bytes.count <= 65_535, u16(bytes, 4) == 1 else { throw Invalid.structure }
        let question = try name(bytes, from: 12)
        var cursor = question.end + 4
        guard cursor <= bytes.count else { throw Invalid.structure }
        let count = Int(u16(bytes, 6)) + Int(u16(bytes, 8)) + Int(u16(bytes, 10))
        guard count <= 256 else { throw Invalid.structure }

        var aliases: [String: String] = [:]
        var subtrees: [String: String] = [:]
        var services: [String: Set<String>] = [:]
        for _ in 0..<count {
            let owner = try name(bytes, from: cursor)
            cursor = owner.end
            guard cursor + 10 <= bytes.count else { throw Invalid.structure }
            let type = u16(bytes, cursor), clazz = u16(bytes, cursor + 2)
            let length = Int(u16(bytes, cursor + 8))
            cursor += 10
            let end = cursor + length
            guard end <= bytes.count else { throw Invalid.structure }
            if clazz == 1, type == 5 || type == 39 {
                let target = try name(bytes, from: cursor)
                guard target.end == end, !target.value.isEmpty, !owner.value.isEmpty else {
                    throw Invalid.structure
                }
                if type == 39 {
                    if let old = subtrees[owner.value], old != target.value { throw Invalid.structure }
                    subtrees[owner.value] = target.value
                } else {
                    if let old = aliases[owner.value], old != target.value { throw Invalid.structure }
                    aliases[owner.value] = target.value
                }
            } else if clazz == 1, type == 64 || type == 65 {
                guard length >= 3 else { throw Invalid.structure }
                let target = try name(bytes, from: cursor + 2)
                guard target.end <= end else { throw Invalid.structure }
                if u16(bytes, cursor) == 0, !target.value.isEmpty {
                    if let old = aliases[owner.value], old != target.value { throw Invalid.structure }
                    aliases[owner.value] = target.value
                } else if u16(bytes, cursor) > 0, !target.value.isEmpty,
                          target.value != owner.value {
                    // ServiceMode can supply connection hints before any A/AAAA
                    // lookup of TargetName. Check every alternative.
                    services[owner.value, default: []].insert(target.value)
                }
            } else if clazz == 1, (type == 1 && length != 4) || (type == 28 && length != 16) {
                throw Invalid.structure
            }
            cursor = end
        }
        guard cursor == bytes.count else { throw Invalid.structure }
        return try blocksChain(question.value, aliases, subtrees, services, isBlocked)
    }

    private static func blocksChain(_ start: String, _ aliases: [String: String],
                                    _ subtrees: [String: String],
                                    _ services: [String: Set<String>]?,
                                    _ isBlocked: (String) -> Bool) throws -> Bool {
        var seen = Set<String>()
        var current = start
        for _ in 0..<32 {
            guard seen.insert(current).inserted else { throw Invalid.structure }
            if isBlocked(current) { return true }
            if let endpoints = services?[current] {
                for endpoint in endpoints {
                    // An endpoint resolves A/AAAA and its aliases, not a second
                    // SVCB service; the nil keeps endpoint fan-out bounded.
                    if try blocksChain(endpoint, aliases, subtrees, nil, isBlocked) { return true }
                }
            }
            var next = aliases[current]
            if next == nil, current.contains(".") {
                let labels = current.split(separator: ".", omittingEmptySubsequences: false)
                // Prefer the longest DNAME owner suffix, as Android's guard does.
                if labels.count > 1 {
                    for index in stride(from: labels.count - 1, through: 1, by: -1) {
                        let suffix = labels[index...].joined(separator: ".")
                        if let replacement = subtrees[suffix] {
                            let prefix = labels[..<index].joined(separator: ".")
                            next = prefix + "." + replacement
                            break
                        }
                    }
                }
            }
            guard let following = next else { return false }
            guard following.count <= 253 else { throw Invalid.structure }
            current = following
        }
        throw Invalid.structure
    }

    private static func name(_ data: [UInt8], from start: Int) throws -> Name {
        var cursor = start
        var end = -1
        var wireLength = 1
        var labels: [String] = []
        for _ in 0..<128 {
            guard cursor >= 0, cursor < data.count else { throw Invalid.structure }
            let size = Int(data[cursor])
            if size & 0xC0 == 0xC0 {
                guard cursor + 1 < data.count else { throw Invalid.structure }
                let target = ((size & 63) << 8) | Int(data[cursor + 1])
                guard target >= 12, target < cursor else { throw Invalid.structure }
                if end < 0 { end = cursor + 2 }
                cursor = target
            } else {
                cursor += 1
                if size == 0 {
                    return Name(value: labels.joined(separator: ".").lowercased(),
                                end: end < 0 ? cursor : end)
                }
                wireLength += size + 1
                guard size <= 63, wireLength <= 255, cursor + size <= data.count else {
                    throw Invalid.structure
                }
                var label = ""
                for _ in 0..<size {
                    let value = Int(data[cursor])
                    cursor += 1
                    guard value >= 33, value <= 126, value != 46 else { throw Invalid.structure }
                    label.append(Character(UnicodeScalar(value)!))
                }
                labels.append(label)
            }
        }
        throw Invalid.structure
    }

    private static func u16(_ data: [UInt8], _ offset: Int) -> UInt16 {
        guard offset >= 0, offset + 1 < data.count else { return 0 }
        return (UInt16(data[offset]) << 8) | UInt16(data[offset + 1])
    }
}
