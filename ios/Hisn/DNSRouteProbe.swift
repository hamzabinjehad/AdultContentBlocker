import Foundation

/// A blocking lookup for one IPv4 address through the system resolver.
///
/// This is the app's half of the DNS route check: ask for a random name under
/// `hisn.invalid`, which no real resolver answers, and see whether the address
/// Hisn's proxy answers comes back. Without the proxy the name fails to
/// resolve; with it, the app gets `203.0.113.7` from the extension itself.
///
/// It proves the app's resolver path reaches Hisn. It is not proof that every
/// app, every request or every page was filtered, and the app says so.
enum DNSRouteProbe {
    /// A fresh name each time, so a cached negative answer from before the
    /// filter was turned on cannot be mistaken for a failed route.
    static func freshName() -> String {
        "self-check-\(UUID().uuidString.lowercased()).hisn.invalid"
    }

    static func lookup(_ host: String) -> [UInt8]? {
        var hints = addrinfo()
        hints.ai_family = AF_INET
        hints.ai_socktype = SOCK_STREAM
        var result: UnsafeMutablePointer<addrinfo>?
        guard getaddrinfo(host, nil, &hints, &result) == 0, let head = result else {
            return nil
        }
        defer { freeaddrinfo(result) }
        var entry: UnsafeMutablePointer<addrinfo>? = head
        while let current = entry {
            if let address = current.pointee.ai_addr,
               address.pointee.sa_family == sa_family_t(AF_INET) {
                let socket = address.withMemoryRebound(to: sockaddr_in.self, capacity: 1) {
                    $0.pointee.sin_addr
                }
                var network = socket
                return withUnsafeBytes(of: &network) { Array($0.prefix(4)) }
            }
            entry = current.pointee.ai_next
        }
        return nil
    }

    static func passes() -> Bool {
        lookup(freshName()) == DNSMessage.selfCheckAddress
    }
}
