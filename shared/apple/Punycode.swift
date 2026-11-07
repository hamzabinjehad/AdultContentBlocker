import Foundation

/// Minimal RFC 3492 punycode decoder.
///
/// WHY THIS FILE EXISTS
/// --------------------
/// The keyword layer matches Arabic terms against hostnames. An Arabic-script
/// domain travels the wire as an `xn--`-prefixed ASCII label, which shares not
/// one character with any Arabic term — so without decoding, every IDN domain
/// is invisible to the entire keyword mechanism. Not blocked, not allowed:
/// simply never examined.
///
/// Foundation has no punycode decoder. `URL` and `URLComponents` will *encode*
/// a Unicode host, and `CFStringConvertHostnameToUnicode` exists on macOS but
/// is unavailable to a sandboxed network extension in the flow path. Hence
/// forty lines of RFC 3492.
///
/// Decode only. Nothing here ever needs to produce punycode.
///
/// The JavaScript side gets this free — `new URL(...)` decodes IDN hosts — and
/// the Python side uses the stdlib `punycode` codec. All three are asserted
/// against `blocklist/terms/host_cases.json`.
enum Punycode {

    private static let base = 36
    private static let tmin = 1
    private static let tmax = 26
    private static let skew = 38
    private static let damp = 700
    private static let initialBias = 72
    private static let initialN = 128

    /// Decode every `xn--` label in a hostname, leaving the rest untouched.
    ///
    /// A label that fails to decode is kept verbatim rather than dropped. A
    /// corrupt label is not a reason to stop examining the rest of the name —
    /// dropping it would turn a malformed hostname into a free pass.
    static func decodeHost(_ host: String) -> String {
        guard host.contains("xn--") else { return host }   // the common case
        return host.split(separator: ".", omittingEmptySubsequences: false)
            .map { label -> Substring in
                guard label.hasPrefix("xn--"),
                      let decoded = decodeLabel(String(label.dropFirst(4)))
                else { return label }
                return Substring(decoded)
            }
            .joined(separator: ".")
    }

    private static func digit(_ c: Character) -> Int? {
        guard let ascii = c.asciiValue else { return nil }
        switch ascii {
        case 0x30...0x39: return Int(ascii) - 0x30 + 26      // 0-9  → 26...35
        case 0x61...0x7A: return Int(ascii) - 0x61           // a-z  → 0...25
        case 0x41...0x5A: return Int(ascii) - 0x41           // A-Z  → 0...25
        default: return nil
        }
    }

    private static func adapt(_ delta: Int, _ numPoints: Int, _ firstTime: Bool) -> Int {
        var delta = firstTime ? delta / damp : delta / 2
        delta += delta / numPoints
        var k = 0
        while delta > ((base - tmin) * tmax) / 2 {
            delta /= (base - tmin)
            k += base
        }
        return k + (((base - tmin + 1) * delta) / (delta + skew))
    }

    private static func decodeLabel(_ input: String) -> String? {
        guard !input.isEmpty else { return nil }

        var output: [UnicodeScalar] = []
        var extended = Substring(input)

        // Everything before the last delimiter is literal ASCII.
        if let delimiter = input.lastIndex(of: "-") {
            let basic = input[input.startIndex..<delimiter]
            for c in basic {
                guard let ascii = c.asciiValue,
                      let scalar = UnicodeScalar(UInt32(ascii)) else { return nil }
                output.append(scalar)
            }
            extended = input[input.index(after: delimiter)...]
        }

        var n = initialN
        var i = 0
        var bias = initialBias
        var index = extended.startIndex

        while index < extended.endIndex {
            let oldI = i
            var w = 1
            var k = base

            while true {
                guard index < extended.endIndex,
                      let d = digit(extended[index]) else { return nil }
                index = extended.index(after: index)

                // Overflow guards. These are not decoration: a hostile hostname
                // is attacker-controlled input arriving on the flow path, and
                // an arithmetic trap here would crash the network extension —
                // which on this product means the filter stops filtering.
                let (mul, mulOverflow) = d.multipliedReportingOverflow(by: w)
                guard !mulOverflow else { return nil }
                let (sum, addOverflow) = i.addingReportingOverflow(mul)
                guard !addOverflow else { return nil }
                i = sum

                let t = k <= bias ? tmin : (k >= bias + tmax ? tmax : k - bias)
                if d < t { break }

                let (nextW, wOverflow) = w.multipliedReportingOverflow(by: base - t)
                guard !wOverflow else { return nil }
                w = nextW
                k += base
            }

            let outLen = output.count + 1
            bias = adapt(i - oldI, outLen, oldI == 0)

            let (nDelta, nOverflow) = n.addingReportingOverflow(i / outLen)
            guard !nOverflow else { return nil }
            n = nDelta
            i %= outLen

            guard i <= output.count,
                  let scalar = UnicodeScalar(UInt32(n)) else { return nil }
            output.insert(scalar, at: i)
            i += 1
        }

        var view = String.UnicodeScalarView()
        view.append(contentsOf: output)
        return String(view)
    }
}
