import Foundation

/// Publishes the person's saved blocked sites to the App Group for the DNS
/// extension. The Screen Time filter keeps its own copy in app-local storage;
/// this one is only the DNS layer's view, re-read by the extension on a short
/// cache so an edit takes effect without restarting the tunnel.
protocol DNSCustomSitesPublishing: AnyObject {
    func publish(_ sites: [String])
}

final class DNSCustomSitesShare: DNSCustomSitesPublishing {
    private let defaults: UserDefaults?

    init(defaults: UserDefaults?) {
        self.defaults = defaults
    }

    /// nil in the hosted tests: a test must not write into the real App Group.
    static func deviceLocal(isTest: Bool) -> DNSCustomSitesShare {
        DNSCustomSitesShare(defaults: isTest ? nil : UserDefaults(suiteName: DNSFilterAssets.group))
    }

    func publish(_ sites: [String]) {
        guard let defaults, let data = try? JSONEncoder().encode(sites) else { return }
        defaults.set(data, forKey: DNSFilterAssets.customSitesKey)
    }
}
