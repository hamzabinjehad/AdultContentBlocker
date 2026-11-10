import Foundation

/// The adult-TLD rail: a name under a sponsored adult TLD is blocked even
/// before any list has heard of it.
///
/// `blocklist/build.py` already treats these TLDs as a *selection signal* for
/// the bundled core tier. That is not enforcement: a domain registered after
/// the last build is not on any list, so the socket filter would allow it. The
/// four TLDs below are adult-sponsored at the registry (ICM Registry operates
/// `.xxx`, `.porn`, `.sex` and `.adult`), which makes the final label itself a
/// reliable signal rather than a guess about a name.
///
/// `.cam` is deliberately absent. It is a generic TLD with ordinary camera and
/// webcam uses, and blocking it whole would take out sites that are not adult
/// content. The keyword layer still catches a `.cam` host whose own labels name
/// adult material.
///
/// This is a policy constant, not a downloaded artifact, so it works when the
/// list is missing and cannot be rolled back by serving an older generation.
/// `SiteCheckRules` names it separately from the published list, and the
/// browser extension carries the same set in `rules/adult_tlds.json`.
enum AdultTldGuard {
    /// Keep in step with `extension/lib/policy.js` (`ADULT_TLDS`) and
    /// `android/core/.../AdultTldPolicy.java`.
    static let tlds: Set<String> = ["adult", "porn", "sex", "xxx"]

    /// The same labels as raw UTF-8, so the flow path can compare bytes instead
    /// of lowercasing and allocating a String for the final label.
    private static let tldBytes: [[UInt8]] = tlds.map { Array($0.utf8) }.sorted { $0.count < $1.count }

    /// True when the host's final label is one of `tlds`.
    ///
    /// The host is already canonicalised by `BlocklistStore.blocked` (lowercase,
    /// trailing dot removed); trimming here keeps the call safe from the site
    /// checker and from tests that pass raw input. A bare label is accepted,
    /// because `xxx/` is a valid URL host even if it is unusual.
    ///
    /// This is on the flow path for every new socket, so it reads UTF-8 bytes
    /// directly: no `lowercased()`, no `trimmingCharacters`, no allocation.
    static func matches(host: String) -> Bool {
        // `String.withUTF8` is mutating; the copy keeps the parameter immutable.
        var host = host
        return host.withUTF8 { buffer in
            var start = 0, end = buffer.count
            while start < end, isTrimByte(buffer[start]) { start += 1 }
            while end > start, isTrimByte(buffer[end - 1]) { end -= 1 }
            guard start < end else { return false }

            // The final label starts after the LAST dot, if there is one.
            var labelStart = start
            for i in start..<end where buffer[i] == 0x2E { labelStart = i + 1 }
            return tldBytes.contains { equalsIgnoringASCIICase(buffer, labelStart..<end, $0) }
        }
    }

    private static func isTrimByte(_ byte: UInt8) -> Bool {
        byte == 0x2E || byte == 0x20 || byte == 0x09 || byte == 0x0A || byte == 0x0D
    }

    @inline(__always)
    private static func equalsIgnoringASCIICase(_ buffer: UnsafeBufferPointer<UInt8>,
                                                _ range: Range<Int>,
                                                _ ascii: [UInt8]) -> Bool {
        guard range.count == ascii.count else { return false }
        var i = range.lowerBound
        for byte in ascii {
            var candidate = buffer[i]
            if candidate >= 0x41, candidate <= 0x5A { candidate += 0x20 }
            if candidate != byte { return false }
            i += 1
        }
        return true
    }
}
