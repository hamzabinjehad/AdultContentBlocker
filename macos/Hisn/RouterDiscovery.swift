import Darwin
import Foundation
import SystemConfiguration

/// Reads local routing metadata only: no subnet scan, passwords, HTTP requests,
/// router writes or claims about firmware capabilities.
enum RouterDiscovery {
    struct Candidate: Equatable {
        let address: String
        let interface: String

        var secureURL: URL { URL(string: "https://\(address)/")! }
        var localHTTPURL: URL { URL(string: "http://\(address)/")! }
    }

    static func discover() -> Candidate? {
        guard let store = SCDynamicStoreCreate(nil, "Hisn router setup" as CFString, nil, nil),
              let value = SCDynamicStoreCopyValue(store, "State:/Network/Global/IPv4" as CFString)
                as? [String: Any] else { return nil }
        guard let found = candidate(router: value["Router"] as? String,
                                    interface: value["PrimaryInterface"] as? String),
              let link = SCDynamicStoreCopyValue(store,
                "State:/Network/Interface/\(found.interface)/IPv4" as CFString) as? [String: Any],
              isOnLocalSubnet(found, addresses: link["Addresses"] as? [String] ?? [],
                              masks: link["SubnetMasks"] as? [String] ?? []) else { return nil }
        return found
    }

    /// Reject stale gateway metadata, this device's own address, and subnet
    /// network/broadcast addresses. A private address alone is not enough.
    static func isOnLocalSubnet(_ gateway: Candidate, addresses: [String], masks: [String]) -> Bool {
        func ipv4(_ value: String) -> UInt32? {
            var parsed = in_addr()
            guard value.withCString({ inet_pton(AF_INET, $0, &parsed) }) == 1 else { return nil }
            return UInt32(bigEndian: parsed.s_addr)
        }
        guard addresses.count == masks.count, let router = ipv4(gateway.address),
              !addresses.contains(where: { ipv4($0) == router }) else { return false }
        return zip(addresses, masks).contains { address, subnet in
            guard let local = ipv4(address), let mask = ipv4(subnet) else { return false }
            let hostMask = ~mask
            // Require a contiguous mask and at least two usable host addresses.
            guard mask != 0, hostMask >= 3, hostMask & (hostMask &+ 1) == 0 else { return false }
            let network = local & mask
            return router != local && router & mask == network &&
                router != network && router != (network | hostMask)
        }
    }

    /// Deliberately conservative. A public, tunnel, IPv6-only, or unrecognized
    /// gateway uses the manual guide instead of guessing an administrator URL.
    static func candidate(router: String?, interface: String?) -> Candidate? {
        guard let router, let interface,
              interface.range(of: #"^(en|bridge)[0-9]+\z"#, options: .regularExpression) != nil,
              !router.utf8.contains(0), router == router.trimmingCharacters(in: .whitespacesAndNewlines)
        else { return nil }
        var address = in_addr()
        guard router.withCString({ inet_pton(AF_INET, $0, &address) }) == 1 else { return nil }
        let bytes = router.split(separator: ".").compactMap { UInt8($0) }
        guard bytes.count == 4,
              bytes.map(String.init).joined(separator: ".") == router,
              bytes[0] == 10 || (bytes[0] == 172 && (16...31).contains(bytes[1])) ||
                (bytes[0] == 192 && bytes[1] == 168) else { return nil }
        return Candidate(address: router, interface: interface)
    }
}
