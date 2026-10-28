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

    /// This boot's identifier (`kern.bootsessionuuid`), new at every restart. nil when iOS
    /// does not give it: every saved reading then counts as from another boot, which can
    /// only under-count.
    static let bootSession: String? = {
        var size = 0
        guard sysctlbyname("kern.bootsessionuuid", nil, &size, nil, 0) == 0, size > 1, size <= 256 else { return nil }
        var buffer = [CChar](repeating: 0, count: size)
        guard sysctlbyname("kern.bootsessionuuid", &buffer, &size, nil, 0) == 0 else { return nil }
        let value = String(cString: buffer)
        return value.isEmpty ? nil : value
    }()

    /// One reading of Apple's server clock and the uptime when it arrived.
    struct Anchor: Codable, Equatable {
        let server: Date
        let uptime: TimeInterval
        /// The boot whose monotonic clock `uptime` is on. nil (a reading saved before this
        /// was recorded, or no identifier) counts as another boot.
        var boot: String?
        /// A lower bound carried over a reboot rather than read in `boot`: it is short by
        /// however long the phone was off, so it is never fresh and never exact.
        var carried: Bool?

        init(server: Date, uptime: TimeInterval, boot: String? = nil, carried: Bool? = nil) {
            self.server = server; self.uptime = uptime; self.boot = boot; self.carried = carried
        }

        /// `uptime` is on the current boot's clock: the same boot identifier, and that clock
        /// has not gone back. A longer-running later boot is still another boot.
        func sameBoot(uptime now: TimeInterval, boot current: String?) -> Bool {
            guard let boot, let current, boot == current, uptime.isFinite, now.isFinite else { return false }
            return now >= uptime
        }

        /// The earliest the real time can be at `now` (an uptime). In the same boot this is
        /// exact. After a reboot only the current boot's uptime is added: that can only
        /// under-count, never over-count.
        func lowerBound(uptime now: TimeInterval, boot current: String?) -> Date? {
            guard server.timeIntervalSinceReferenceDate.isFinite, uptime.isFinite, uptime >= 0,
                  now.isFinite, now >= 0 else { return nil }
            let elapsed = sameBoot(uptime: now, boot: current) ? now - uptime : now
            let bound = server.addingTimeInterval(elapsed)
            return bound.timeIntervalSinceReferenceDate.isFinite ? bound : nil
        }

        /// Seconds since Apple's time was read in this boot; nil for a reading from another
        /// boot or one carried over a reboot, which callers treat as stale.
        func age(uptime now: TimeInterval, boot current: String?) -> TimeInterval? {
            guard carried != true, sameBoot(uptime: now, boot: current) else { return nil }
            return now - uptime
        }

        /// The same lower bound restated at `now`, so a later reboot counts from here rather
        /// than from the original reading. It stays fresh only if this one was.
        func rebased(uptime now: TimeInterval, boot current: String?) -> Anchor? {
            guard let bound = lowerBound(uptime: now, boot: current) else { return nil }
            return Anchor(server: bound, uptime: now, boot: current,
                          carried: age(uptime: now, boot: current) == nil ? true : nil)
        }
    }

    /// A new reading of Apple's time, checked against the saved one before it is kept.
    ///
    /// Hisn does not pin Apple's certificate: the reading is as trustworthy as the system's
    /// HTTPS trust, so a root certificate the person installed and trusted (an
    /// HTTPS-inspecting proxy, say) could forge the `Date` header. Within one boot a fresh
    /// saved reading already gives the time to within its network delay, so a new reading
    /// may only lower it: one that is ahead is not taken, and nothing a forged header says
    /// can move the time forward until the next restart. A reading in a new boot has
    /// nothing to be checked against and is taken as it is.
    static func reconcile(reading: Date, uptime: TimeInterval, boot: String?, with saved: Anchor?) -> Anchor {
        let fresh = Anchor(server: reading, uptime: uptime, boot: boot)
        guard let saved, saved.age(uptime: uptime, boot: boot) != nil,
              let bound = saved.lowerBound(uptime: uptime, boot: boot), bound < reading else { return fresh }
        return Anchor(server: bound, uptime: uptime, boot: boot)
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
    /// delay. The monitor extension settles a budget day in one check at midnight, so it
    /// allows this much to keep an honest midnight from looking like a moved clock. The
    /// commitment end and the layer-alert window never use it: the app re-checks every
    /// minute, and a commitment must not end even seconds early.
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
                  anchor.lowerBound(uptime: anchor.uptime, boot: anchor.boot) != nil else { return nil }
            return anchor
        }

        func save(_ anchor: Anchor) throws {
            try storage.write(JSONEncoder().encode(anchor), forKey: Self.key)
        }

        /// Saves a restated lower bound unless the saved one is already later at `uptime`,
        /// so a check never moves the shared time backwards. At the same bound the
        /// restatement wins (it counts from later after a reboot), unless it would replace a
        /// reading made in this boot with one carried over a reboot.
        func advance(to candidate: Anchor, uptime: TimeInterval, boot: String?) throws {
            guard let next = candidate.lowerBound(uptime: uptime, boot: boot) else { return }
            if let saved = load(), let current = saved.lowerBound(uptime: uptime, boot: boot), current >= next {
                let losesFreshness = saved.age(uptime: uptime, boot: boot) != nil
                    && candidate.age(uptime: uptime, boot: boot) == nil
                guard current == next, !losesFreshness, candidate.server > saved.server else { return }
            }
            try save(candidate)
        }
    }
}

// MARK: - Apple server time

protocol ServerTimeSource {
    func currentTime() async -> Date?
}

/// The `Date` header of an HTTPS response from Apple. Nothing about the person
/// is sent: a plain HEAD request with no cookies and no cache.
///
/// Default system trust, no certificate pinning: a root certificate the person trusted
/// can show a false time. Callers keep a reading through `TrustedTime.reconcile`, which
/// never lets one move the time forward within a boot that already has a reading.
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
