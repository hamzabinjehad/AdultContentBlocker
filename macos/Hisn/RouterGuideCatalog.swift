import Foundation

/// Manufacturer instructions researched on 2026-10-03. A family guide is not
/// evidence that a particular model/firmware supports a feature or enforces it.
enum RouterFamily: String, CaseIterable, Identifiable {
    case unknown, tpLink, deco, asus, fritz, netgear, linksys, dlink
    case openWrt, glInet, mikroTik, unifi, eero, googleNest

    var id: String { rawValue }
    var name: String {
        switch self {
        case .unknown: return "Other / I don't know"
        case .tpLink: return "TP-Link Archer / DSL"
        case .deco: return "TP-Link Deco"
        case .asus: return "ASUS"
        case .fritz: return "FRITZ!Box"
        case .netgear: return "NETGEAR / Orbi"
        case .linksys: return "Linksys"
        case .dlink: return "D-Link"
        case .openWrt: return "OpenWrt"
        case .glInet: return "GL.iNet"
        case .mikroTik: return "MikroTik / RouterOS"
        case .unifi: return "Ubiquiti UniFi"
        case .eero: return "eero"
        case .googleNest: return "Google Nest / Google Wifi"
        }
    }

    var usesPhoneApp: Bool { [.deco, .eero, .googleNest].contains(self) }

    var instructionKey: String {
        switch self {
        case .unknown: return "Find the manufacturer and model on the router label or in its official app. An internet provider may restrict DNS settings. Choose the matching guide, or continue protecting this device."
        case .tpLink: return "For Archer wireless routers, look under Advanced > Network > Internet. DSL models may use LAN Settings instead. Use the official guide for your hardware version; Deco has a separate app guide."
        case .deco: return "On your phone, connect to Deco Wi-Fi and open the Deco app with the owner's account. Look under More > Internet Connection > Internet Settings > DNS Address > Manual. Some models use More > Advanced > DHCP Server. Router mode is required."
        case .asus: return "Open the ASUS router page and find WAN > Internet Connection, then the WAN DNS settings. The menus differ between firmware versions; follow the matching method in the official guide."
        case .fritz: return "For upstream DNS, use Internet > Account Information > DNS Server. For a local filtering server, use Home Network > Network > Network Settings and the IPv4/IPv6 DNS advertisement settings. The official guide explains both routes."
        case .netgear: return "In the router's web interface, open Internet and find Domain Name Server (DNS) Address > Use These DNS Servers. Check the official article's model list before following it."
        case .linksys: return "Open the model's Connectivity or network setup page and look for Static DNS fields. Linksys firmware and product families differ; use the official model documentation to locate the fields."
        case .dlink: return "Find your exact D-Link model and hardware revision in the official support center. Use its manual to locate Internet DNS and client DHCP DNS settings; a menu path from another D-Link model may not apply."
        case .openWrt: return "OpenWrt can run AdGuard Home on suitable hardware. Check available memory and storage, preserve existing DNS settings, and follow OpenWrt's installation guide. A separate always-on filtering server is an alternative for smaller routers."
        case .glInet: return "On supported GL.iNet models, open Applications > AdGuard Home, enable it, then open Settings Page to manage filters. Check the official supported-model list first; client DNS handling can affect existing VPN and parental-control policies."
        case .mikroTik: return "Check the installed RouterOS version for IP > DNS > Adlist support. The router can import a local hosts-format file where this feature is available. Check memory, storage, and client DNS routing before importing a large list."
        case .unifi: return "In UniFi Network, open Settings > CyberSecure > Content Filter. Choose the family networks or devices, enable the appropriate adult-content policy and Safe Search, then check the applied scope. Availability depends on the gateway and software version."
        case .eero: return "In the eero app, open Settings > Advanced networking > DNS > Custom DNS. Existing eero Plus or HomeKit settings can affect custom DNS; review the official instructions before changing them."
        case .googleNest: return "In Google Home, select the network's Wi-Fi settings, then Advanced networking > DNS > Custom. Enter the chosen filtering DNS addresses and save. Use Google's guide for the current app layout."
        }
    }

    var sourceURL: URL {
        let value: String
        switch self {
        case .unknown: value = "https://developers.cloudflare.com/1.1.1.1/setup/router/"
        case .tpLink: value = "https://www.tp-link.com/us/support/faq/1712/"
        case .deco: value = "https://www.tp-link.com/us/support/faq/1855/"
        case .asus: value = "https://www.asus.com/global/support/faq/1045253/"
        case .fritz: value = "https://fritz.com/en/apps/knowledge-base/fritz-box-5490/165_Configuring-different-DNS-servers-in-the-FRITZ-Box"
        case .netgear: value = "https://kb.netgear.com/30510/How-do-I-set-static-Domain-Name-System-servers-on-my-NETGEAR-router"
        case .linksys: value = "https://support.linksys.com/kb/article/119-en/?section_id=162"
        case .dlink: value = "https://support.dlink.com/"
        case .openWrt: value = "https://openwrt.org/docs/guide-user/services/dns/adguard-home"
        case .glInet: value = "https://docs.gl-inet.com/router/en/4/interface_guide/adguardhome/"
        case .mikroTik: value = "https://manual.mikrotik.com/docs/network-management/dns/"
        case .unifi: value = "https://help.ui.com/hc/en-us/articles/12568927589143-Content-and-Domain-Filtering-in-UniFi"
        case .eero: value = "https://eero.com/support/articles/how-do-i-set-up-custom-dns-servers-with-eero"
        case .googleNest: value = "https://support.google.com/googlehome/answer/6274141?hl=en"
        }
        return URL(string: value)!
    }

    var listInstructionKey: String {
        switch self {
        case .openWrt, .glInet:
            return "To use Hisn's list, import the verified adguard.txt rules into AdGuard Home's DNS blocklists. The filtering server must remain online, and family devices must use it. Check one listed domain, a subdomain, and an ordinary site after import."
        case .mikroTik:
            return "To use Hisn's list with RouterOS Adlist, publish the signed bundle in hosts format and import the verified hosts.txt file through the supported local-file workflow. Test both address families and subdomains; hosts-format export does not promise wildcard coverage."
        case .unifi:
            return "UniFi's built-in category filter uses its provider's classification. The official guide does not establish bulk import of Hisn's full signed list. For that list, assess a separate filtering DNS server and its interaction with UniFi content filtering."
        default:
            return "Changing DNS uses the chosen provider's categories. To use Hisn's own list, point the router and its clients to an always-on filtering server that imports Hisn's verified rules. This guide does not establish direct list upload on your model."
        }
    }

    /// A hint from a user-entered name, always requiring confirmation. Common
    /// IP addresses and ambiguous speed labels deliberately yield no suggestion.
    static func suggestion(for text: String) -> RouterFamily? {
        let name = RouterProfile.clean(text).precomposedStringWithCompatibilityMapping.lowercased()
        let patterns: [(String, RouterFamily)] = [
            (#"\bopenwrt\b"#, .openWrt), (#"\bdeco\b"#, .deco),
            (#"\bfritz[!\s-]*(box)?\b"#, .fritz),
            (#"\bgl[.-](inet|(?:mt|mg|ax|axt|ar|xe|x|sft|e|b)[0-9]+[a-z0-9-]*)\b"#, .glInet),
            (#"\b(mikrotik|routeros)\b"#, .mikroTik), (#"\b(unifi|ubiquiti)\b"#, .unifi),
            (#"\beero\b"#, .eero), (#"\b(nest wifi|google wifi)\b"#, .googleNest),
            (#"\b(asus|rt-ax[0-9]+[a-z0-9-]*|rt-ac[0-9]+[a-z0-9-]*)\b"#, .asus),
            (#"\b(tp[ -]?link|archer)\b"#, .tpLink),
            (#"\b(netgear|nighthawk|orbi)\b"#, .netgear),
            (#"\blinksys\b"#, .linksys), (#"\bd[ -]?link\b"#, .dlink)
        ]
        var matches = Set(patterns.filter {
            name.range(of: $0.0, options: .regularExpression) != nil
        }.map { $0.1 })
        // Deco belongs to TP-Link; its app-specific guide takes precedence.
        if matches.contains(.deco) { matches.remove(.tpLink) }
        // Firmware and manufacturer may both be named. Let the user choose
        // rather than silently selecting a possibly incompatible setup route.
        return matches.count == 1 ? matches.first : nil
    }
}

struct RouterProfile: Equatable {
    var family: RouterFamily = .unknown
    var model = ""
    var firmware = ""

    // Models explicitly excluded by GL.iNet's researched AdGuard Home guide.
    // An unrecognized model remains unverified rather than becoming supported.
    var excludesBuiltInAdguard: Bool {
        guard family == .glInet else { return false }
        let excluded: Set<String> = ["GL-MG1300", "GL-SFT1200", "GL-MT1300", "GL-E750",
            "GL-E750V2", "GL-AR750S", "GL-XE300", "GL-X750", "GL-MT300N-V2",
            "GL-B1300", "GL-X300B"]
        let tokens = model.uppercased().split { !$0.isLetter && !$0.isNumber && $0 != "-" }
        return tokens.contains { excluded.contains(String($0)) || $0.hasPrefix("GL-AR300M") }
    }

    static func clean(_ input: String) -> String {
        String(input.unicodeScalars.filter { !CharacterSet.controlCharacters.contains($0) }.prefix(80))
    }
}
