import Foundation

/// Proof that a second person authorised ending a lock early.
///
/// Only `PartnerService` can produce one, and it only does so after the server
/// has confirmed the approval. Passing this value around is how the rest of the
/// codebase proves, at the type level, that an early release was authorised —
/// there is no way to construct one from inside the app.
public struct PartnerApproval {
    public let id: String
    public let approvedBy: String
    public let approvedAt: Date

    fileprivate init(id: String, approvedBy: String, approvedAt: Date) {
        self.id = id
        self.approvedBy = approvedBy
        self.approvedAt = approvedAt
    }
}

/// Accountability partner integration.
///
/// This is the part of the product that does the work the operating system
/// cannot. Every technical control on a Mac has the same ceiling: the person
/// sitting at it owns it, and can eventually reinstall the OS. What a partner
/// adds is not another technical barrier — it is a social cost, applied at the
/// exact moment technical barriers are weakest.
///
/// Two rules keep this honest:
///
///  * **Approval is verified server-side.** A code the app can check locally is
///    a code that can be read out of the binary or intercepted in the debugger.
///  * **Nothing about browsing is ever sent.** The partner learns that a
///    release was requested and when. They do not learn what was visited,
///    because a log of blocked adult domains is one breach away from being the
///    most damaging thing this company could hold. See docs/PRIVACY.md.
public final class PartnerService {

    public static let shared = PartnerService()

    private let baseURL = URL(string: "https://api.hisn.app/v1")!
    private let session: URLSession

    public enum PartnerError: LocalizedError {
        case notConfigured
        case rejected(String)
        case network(String)
        case replayed

        public var errorDescription: String? {
            switch self {
            case .notConfigured:
                return "No accountability partner is set up for this device."
            case let .rejected(reason):
                return "The approval was not accepted: \(reason)"
            case let .network(detail):
                return "Could not reach the approval service: \(detail)"
            case .replayed:
                return "That approval code has already been used."
            }
        }
    }

    private init() {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 20
        config.httpCookieStorage = nil
        config.urlCache = nil
        session = URLSession(configuration: config)
    }

    // MARK: - Requests

    /// Ask the partner to approve ending the current lock.
    ///
    /// Returns immediately; the partner is notified out of band. The app then
    /// waits for them to hand over a code, which keeps the conversation between
    /// two people rather than between a person and a dialog box.
    public func requestRelease(reason: String) async throws {
        struct Body: Encodable { let reason: String; let requestedAt: Date }
        _ = try await post("release-requests",
                           body: Body(reason: reason, requestedAt: Date()))
    }

    /// Verify a partner-issued release code.
    ///
    /// - Returns: a `PartnerApproval` on success — the only token that unlocks
    ///   `LockStore.clearWithPartnerApproval`.
    @discardableResult
    public func verifyRelease(token: String,
                              lockStartedAt: Date) async throws -> PartnerApproval {
        struct Body: Encodable { let token: String; let lockStartedAt: Date }
        struct Reply: Decodable {
            let approved: Bool
            let approvalId: String?
            let approvedBy: String?
            let approvedAt: Date?
            let reason: String?
        }

        let reply: Reply = try await post("release-approvals",
                                          body: Body(token: token,
                                                     lockStartedAt: lockStartedAt))

        guard reply.approved,
              let id = reply.approvalId,
              let by = reply.approvedBy,
              let at = reply.approvedAt else {
            throw PartnerError.rejected(reply.reason ?? "unknown reason")
        }

        return PartnerApproval(id: id, approvedBy: by, approvedAt: at)
    }

    // MARK: - Transport

    private func post<Body: Encodable, Reply: Decodable>(
        _ path: String, body: Body
    ) async throws -> Reply {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        request.httpBody = try encoder.encode(body)

        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else {
                throw PartnerError.network("no HTTP response")
            }
            if http.statusCode == 409 { throw PartnerError.replayed }
            guard (200..<300).contains(http.statusCode) else {
                throw PartnerError.rejected("HTTP \(http.statusCode)")
            }
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            return try decoder.decode(Reply.self, from: data)
        } catch let error as PartnerError {
            throw error
        } catch {
            throw PartnerError.network(error.localizedDescription)
        }
    }

    private func post<Body: Encodable>(_ path: String, body: Body) async throws -> Bool {
        let ack: Ack = try await post(path, body: body)
        return ack.ok
    }
}

/// Declared at file scope because Swift forbids nesting a type inside a generic
/// function, and the acknowledgement-only `post` overload is generic over Body.
private struct Ack: Decodable { let ok: Bool }
