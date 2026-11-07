import SwiftUI

/// Permission and discovery are deliberately transient. Neither is evidence of
/// filtering, and opening a router page never marks setup as complete.
struct RouterSetupAssistant: View {
    @Environment(\.openURL) private var openURL
    @State private var authorized = false
    @State private var attempted = false
    @State private var candidate: RouterDiscovery.Candidate?
    @State private var openFailed = false
    @State private var confirmHTTP = false
    var showDNSGuide: () -> Void
    var continueOnDevice: () -> Void
    var showListGuide: () -> Void
    var usesPhoneApp = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 6) {
                Text("Set up network protection")
                    .font(.system(.callout).weight(.semibold))
                    .foregroundStyle(HnPalette.ink)
                HnExplain {
                    Text("Start here. Hisn can find a local gateway address and guide you; it cannot upload lists to every router automatically.")
                    Text("This may be your router's page. Confirm the address in your router's documentation. Enter its password only in the router's own page; Hisn does not collect it.")
                    Text("Before changing anything, save the current router settings. In its page, look for Internet or DNS. If those settings are unavailable, continue with device protection.")
                    Text("Finding a router or opening its page does not verify blocking. After setup, use the DNS check below and test each device separately.")
                }
                Spacer(minLength: 0)
            }
            Toggle("I own this network or have its owner's permission", isOn: $authorized)
                .font(.hnCallout())
                .onChange(of: authorized) { _ in
                    candidate = nil; attempted = false; openFailed = false
                }
            if usesPhoneApp {
                Text("This router family uses its phone app for DNS settings. Open that app and follow the family guide above, then return here for the filtering DNS addresses and checks.")
                    .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                HnButton(title: "Show the DNS setup steps", systemImage: "arrow.forward",
                         kind: .quiet, action: showDNSGuide)
                    .disabled(!authorized)
            } else {
                HnButton(title: "Find my router", systemImage: "magnifyingglass", kind: .quiet) {
                    candidate = RouterDiscovery.discover()
                    attempted = true
                    openFailed = false
                }
                .disabled(!authorized)
            }
            if authorized, attempted {
                if let candidate {
                    Text("Gateway address found — protection is not configured yet.")
                        .font(.hnCallout())
                    Text(verbatim: candidate.address)
                        .font(.system(.callout, design: .monospaced))
                        .foregroundStyle(HnPalette.ink)
                        .padding(.horizontal, 9).padding(.vertical, 5)
                        .background(HnPalette.raised, in: RoundedRectangle(cornerRadius: 7))
                        .textSelection(.enabled)
                        .environment(\.layoutDirection, .leftToRight)
                    HStack(spacing: 8) {
                        HnButton(title: "Open router settings", kind: .quiet) { open(candidate, secure: true) }
                        HnButton(title: "My router only supports HTTP", kind: .outline) { confirmHTTP = true }
                    }
                        .confirmationDialog("Open an unencrypted router page?", isPresented: $confirmHTTP) {
                            Button("Open local HTTP page") { open(candidate, secure: false) }
                        } message: {
                            Text("HTTP does not encrypt the administrator password. Use it only on a trusted home network if your router documentation requires it. Do not bypass unexpected certificate warnings.")
                        }
                } else {
                    Text("No supported local gateway address was found. This can happen with a VPN, an IPv6-only network, or a router managed through its own app. Use the router's official app or continue protecting this device.")
                        .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                }
                if openFailed {
                    Text("The network changed or the page could not be opened. Find your router again, or use its official app.")
                        .font(.hnCaption()).foregroundStyle(HnPalette.ember)
                }
                HnButton(title: "Show the DNS setup steps", systemImage: "arrow.forward",
                         kind: .quiet, action: showDNSGuide)
            }
            HStack(spacing: 8) {
                HnButton(title: "Continue with device protection", kind: .quiet, action: continueOnDevice)
                HnButton(title: "Set up Hisn's own router list", kind: .outline, action: showListGuide)
                    .disabled(!authorized)
            }
        }
        .onChange(of: usesPhoneApp) { _ in
            candidate = nil; attempted = false; openFailed = false
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    private func open(_ found: RouterDiscovery.Candidate, secure: Bool) {
        guard authorized, RouterDiscovery.discover() == found else {
            candidate = nil
            openFailed = true
            return
        }
        openURL(secure ? found.secureURL : found.localHTTPURL) { accepted in
            openFailed = !accepted
        }
    }
}
