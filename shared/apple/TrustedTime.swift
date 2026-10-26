import Foundation
import Darwin

/// Time a moved device clock cannot fake: Apple's server time read over HTTPS,
/// carried forward with the monotonic clock. Used by the iPhone app (commitment
/// end, passcode vault) and its monitor extension (budget day boundary).
///
/// Every value it gives is a lower bound: never later than the real time, as long
/// as Apple's server was right when it was read. So it can only keep a lock on
/// longer, never end one early.
enum TrustedTime {
    /// Monotonic seconds since boot, including sleep. Restarts at every reboot.
    static var uptime: TimeInterval {
        guard let scale = timebase else { return .nan }
        return Double(mach_continuous_time()) * scale
    }
    private static let timebase: Double? = {
        var info = mach_timebase_info_data_t()
        guard mach_timebase_info(&info) == KERN_SUCCESS, info.denom > 0 else { return nil }
        return Double(info.numer) / Double(info.denom) / 1_000_000_000
    }()

    /// One reading of Apple's server clock and the uptime when it arrived.
    struct Anchor: Codable, Equatable {
        let server: Date
        let uptime: TimeInterval

        /// The earliest the real time can be at `now` (an uptime). In the same boot
        /// this is exact. After a reboot uptime restarts near zero, so at most the
        /// current boot's uptime is added: that can only under-count, never over-count.
        func lowerBound(uptime now: TimeInterval) -> Date? {
            guard server.timeIntervalSinceReferenceDate.isFinite, uptime.isFinite, uptime >= 0,
                  now.isFinite, now >= 0 else { return nil }
            let elapsed = now >= uptime ? now - uptime : now
            let bound = server.addingTimeInterval(elapsed)
            return bound.timeIntervalSinceReferenceDate.isFinite ? bound : nil
        }

        /// Seconds of this boot since the reading, or nil after a reboot.
        func age(uptime now: TimeInterval) -> TimeInterval? {
            guard uptime.isFinite, now.isFinite, now >= uptime else { return nil }
            return now - uptime
        }
    }

    enum Verdict: Equatable {
        /// The device clock has not reached the date.
        case notYet
        /// The device clock has passed it; Apple's time could not be read.
        case unverified
        /// The device clock has passed it; Apple's time has not.
        case clockAhead
        /// Both have passed it.
        case passed
    }

    /// A reading lags the real time by the `Date` header's whole seconds and the network
    /// delay. Allowing this much keeps an honest midnight or end date from looking like a
    /// moved clock; it can end a lock at most this early, never days early.
    static let readingSkew: TimeInterval = 120

    /// A date counts as passed only on both clocks. Unknown server time never passes.
    static func verdict(for date: Date, deviceNow: Date, trustedNow: Date?,
                        skew: TimeInterval = 0) -> Verdict {
        guard deviceNow >= date else { return .notYet }
        guard let trustedNow else { return .unverified }
        return trustedNow.addingTimeInterval(max(0, skew)) >= date ? .passed : .clockAhead
    }

    /// The latest reading, shared through the app group so the monitor extension
    /// can use it offline. Damaged or missing data is no reading: callers then
    /// keep the lock and try the network.
    struct AnchorStore {
        static let key = "trustedTime.anchor"
        let storage: RuleStorage

        func load() -> Anchor? {
            guard let data = storage.object(forKey: Self.key) as? Data,
                  let anchor = try? JSONDecoder().decode(Anchor.self, from: data),
                  anchor.lowerBound(uptime: anchor.uptime) != nil else { return nil }
            return anchor
        }

        func save(_ anchor: Anchor) throws {
            try storage.write(JSONEncoder().encode(anchor), forKey: Self.key)
        }
    }
}

// MARK: - Apple server time

protocol ServerTimeSource {
    func currentTime() async -> Date?
}

/// The `Date` header of an HTTPS response from Apple. Nothing about the person
/// is sent: a plain HEAD request with no cookies and no cache.
struct AppleServerTime: ServerTimeSource {
    static let url = URL(string: "https://www.apple.com/")!
    var timeout: TimeInterval = 10

    func currentTime() async -> Date? {
        guard let result = try? await URLSession.shared.data(for: Self.request(timeout: timeout)) else { return nil }
        return Self.time(from: result.1)
    }

    /// For the monitor extension, whose callbacks must return synchronously.
    /// Waits at most `seconds`; nil on any failure.
    func currentTime(waitingAtMost seconds: TimeInterval) -> Date? {
        let done = DispatchSemaphore(value: 0)
        let box = ResultBox()
        let task = URLSession.shared.dataTask(with: Self.request(timeout: seconds)) { _, response, _ in
            box.set(Self.time(from: response))
            done.signal()
        }
        task.resume()
        if done.wait(timeout: .now() + seconds) == .timedOut { task.cancel(); return nil }
        return box.value
    }

    static func request(timeout: TimeInterval) -> URLRequest {
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalAndRemoteCacheData,
                                 timeoutInterval: timeout)
        request.httpMethod = "HEAD"
        request.httpShouldHandleCookies = false
        return request
    }

    /// Only an HTTPS response from Apple's host counts; a redirect elsewhere does not.
    static func time(from response: URLResponse?) -> Date? {
        guard let http = response as? HTTPURLResponse, let url = http.url, url.scheme == "https",
              let host = url.host?.lowercased(), host == "apple.com" || host.hasSuffix(".apple.com"),
              let header = http.value(forHTTPHeaderField: "Date") else { return nil }
        return parse(header)
    }

    static func parse(_ header: String) -> Date? {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
        return formatter.date(from: header)
    }

    private final class ResultBox: @unchecked Sendable {
        private let lock = NSLock()
        private var stored: Date?
        func set(_ date: Date?) { lock.lock(); stored = date; lock.unlock() }
        var value: Date? { lock.lock(); defer { lock.unlock() }; return stored }
    }
}

/// Tests and previews never reach the network.
struct UnavailableServerTime: ServerTimeSource {
    func currentTime() async -> Date? { nil }
}
