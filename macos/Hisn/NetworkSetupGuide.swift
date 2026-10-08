import SwiftUI

/// A capability-based guide for any network. Choosing a route describes the
/// user's setup; it never changes router settings or becomes enforcement proof.
struct NetworkSetupGuide: View {
    enum Route: String, CaseIterable, Identifiable {
        case unknown, routerDNS, localResolver, unavailable

        var id: String { rawValue }
        var title: LocalizedStringKey {
            switch self {
            case .unknown: return "I don't know my router's capabilities yet"
            case .routerDNS: return "I can change the router's DNS"
            case .localResolver: return "I have a filtering DNS server"
            case .unavailable: return "I cannot change this network"
            }
        }
    }

    @State private var route: Route
    @State private var routerProfile: RouterProfile
    private let initialDNSResult: NetworkDNSProbe.Result?

    init(initialRoute: Route = .unknown, initialDNSResult: NetworkDNSProbe.Result? = nil,
         initialRouterProfile: RouterProfile = RouterProfile()) {
        _route = State(initialValue: initialRoute)
        _routerProfile = State(initialValue: initialRouterProfile)
        self.initialDNSResult = initialDNSResult
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Start with your network").font(.title3.weight(.semibold))
            Label("Network protection not verified", systemImage: "network")
                .font(.callout.weight(.medium))
            Text("Choose what your network allows, regardless of the router brand or internet provider. Hisn does not configure routers automatically yet.")
                .font(.callout).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            Text("Self-control comes first: you can set up these layers yourself. A trusted person is optional reinforcement against changing administrator or router settings, not a requirement for starting a commitment.")
                .font(.callout).foregroundStyle(.secondary)
            if route != .unavailable {
                RouterIdentityView(profile: $routerProfile)
                if routerProfile.family != .unknown {
                    RouterFamilyInstructions(profile: routerProfile)
                }
            }
            if route == .unknown {
                RouterSetupAssistant(showDNSGuide: { route = .routerDNS },
                                     continueOnDevice: { route = .unavailable },
                                     showListGuide: { route = .localResolver },
                                     usesPhoneApp: routerProfile.family.usesPhoneApp)
            }
            DisclosureGroup("Other network setup options") {
              Picker("Network setup", selection: $route) {
                ForEach(Route.allCases) { option in
                    Text(option.title).tag(option)
                }
            }
            .pickerStyle(.menu)
            .labelsHidden()
            .accessibilityLabel(Text("Network setup"))
            }
            instructions
                .font(.callout)
                .fixedSize(horizontal: false, vertical: true)
            Text("Then protect this Mac and each phone. Other Wi-Fi networks and cellular connections need their own device protection.")
                .font(.callout).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder private var instructions: some View {
        switch route {
        case .unknown:
            EmptyView()
        case .routerDNS:
            VStack(alignment: .leading, spacing: 10) {
                Text("Save the current settings with your trusted administrator. One option is Cloudflare Families, which filters adult content and malware:")
                addresses("IPv4", "1.1.1.3\n1.0.0.3")
                addresses("IPv6", "2606:4700:4700::1113\n2606:4700:4700::1003")
                Text("Set the router's DNS and the DNS it advertises to clients, where supported. Every fallback must use the same filtering policy. Reconnect devices, then test blocking and an ordinary website from each device over IPv4 and IPv6.")
                Text("This uses the provider's categories. To use Hisn's signed domain list, choose a filtering DNS server instead.")
                    .foregroundStyle(.secondary)
                Text("No list can guarantee that every blocked site is adult content. Category mistakes can affect ordinary sites. Keep a recovery path and review incorrect blocks; a signed list proves its origin, not that every classification is correct.")
                    .foregroundStyle(.secondary)
                Link("Cloudflare router instructions", destination: URL(string: "https://developers.cloudflare.com/1.1.1.1/setup/router/")!)
                NetworkDNSProbeView(initialResult: initialDNSResult)
                bypassGuidance
            }
        case .localResolver:
            VStack(alignment: .leading, spacing: 10) {
              if routerProfile.family == .mikroTik {
                Text(LocalizedStringKey(routerProfile.family.listInstructionKey))
              } else {
                Text("Have your administrator import Hisn's verified signed rules into AdGuard Home on a gateway or an always-on DNS server. Point the router and its clients to that server for IPv4 and IPv6.")
              }
                Text("Verify a listed domain, a subdomain, and an ordinary website in the resolver's query log from each device. Check again after a list update. Importing a file alone does not prove that clients use it.")
                bypassGuidance
            }
        case .unavailable:
            Text("Continue with this Mac's protection below. For an ISP-locked router, ask the provider whether custom DNS is supported, or use a gateway you administer. Public Wi-Fi stays outside your network control.")
        }
    }

    private var bypassGuidance: some View {
        Text("If the gateway supports firewall rules, your administrator can restrict outside DNS separately for IPv4 and IPv6. DNS settings alone do not prevent encrypted DNS or VPN bypass. Keep the router password with your trusted person and plan recovery before restricting access.")
            .foregroundStyle(.secondary)
    }

    private func addresses(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Text(verbatim: label).frame(width: 42, alignment: .leading)
            Text(verbatim: value)
                .monospaced()
                .textSelection(.enabled)
        }
        .environment(\.layoutDirection, .leftToRight)
    }
}
