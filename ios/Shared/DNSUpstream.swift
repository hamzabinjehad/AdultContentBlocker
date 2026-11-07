import Foundation

/// One HTTP POST, injectable so the resolver's fallback and validation are
/// testable without a network.
protocol DNSHTTPTransport {
    func post(_ request: URLRequest) async throws -> (status: Int, contentType: String?, body: Data)
}

struct URLSessionDNSHTTPTransport: DNSHTTPTransport {
    let session: URLSession

    init(timeout: TimeInterval = 5) {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = timeout
        configuration.timeoutIntervalForResource = timeout + 2
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.urlCache = nil
        configuration.httpMaximumConnectionsPerHost = 4
        configuration.waitsForConnectivity = false
        session = URLSession(configuration: configuration)
    }

    func post(_ request: URLRequest) async throws -> (status: Int, contentType: String?, body: Data) {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            return (0, nil, Data())
        }
        return (http.statusCode, http.value(forHTTPHeaderField: "Content-Type"), data)
    }
}

/// DNS over HTTPS to Cloudflare Families, the same two endpoints Android's
/// resolver uses. Addresses are literals on purpose: the extension must not
/// need a DNS lookup to reach the resolver that is answering DNS.
///
/// The certificate for `1.1.1.3` and `1.0.0.3` carries those IP addresses as
/// subject alternative names (and `family.cloudflare-dns.com`), so TLS
/// validation works with the literal host and nothing is sent in a TLS SNI
/// that a network could use to filter the request. Responses are returned only
/// after the caller's validator accepts them; an invalid reply from the first
/// endpoint is retried once on the second, never returned to the client.
struct DoHResolver {
    static let endpoints = ["https://1.1.1.3/dns-query", "https://1.0.0.3/dns-query"]
    static let maximumResponseBytes = 65_535

    let transport: DNSHTTPTransport

    init(transport: DNSHTTPTransport = URLSessionDNSHTTPTransport()) {
        self.transport = transport
    }

    func resolve(_ query: Data, isValid: (Data) -> Bool) async -> Data? {
        guard !query.isEmpty, query.count <= 4_096 else { return nil }
        for endpoint in Self.endpoints {
            guard let url = URL(string: endpoint) else { continue }
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/dns-message", forHTTPHeaderField: "Content-Type")
            request.setValue("application/dns-message", forHTTPHeaderField: "Accept")
            request.httpBody = query
            guard let result = try? await transport.post(request) else { continue }
            guard result.status == 200,
                  result.contentType?.lowercased()
                    .hasPrefix("application/dns-message") == true,
                  !result.body.isEmpty,
                  result.body.count <= Self.maximumResponseBytes,
                  isValid(result.body) else { continue }
            return result.body
        }
        return nil
    }
}
