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
            Text("Set up network protection").font(.headline)
            Text("Start here. Hisn can find a local gateway address and guide you; it cannot upload lists to every router automatically.")
                .foregroundStyle(.secondary)
            Toggle("I own this network or have its owner's permission", isOn: $authorized)
                .onChange(of: authorized) { _ in
                    candidate = nil; attempted = false; openFailed = false
                }
            if usesPhoneApp {
                Text("This router family uses its phone app for DNS settings. Open that app and follow the family guide above, then return here for the filtering DNS addresses and checks.")
                Button("Show the DNS setup steps", action: showDNSGuide)
                    .disabled(!authorized)
            } else {
              Button("Find my router") {
                candidate = RouterDiscovery.discover()
                attempted = true
                openFailed = false
            }
            .buttonStyle(.borderedProminent)
            .disabled(!authorized)
            }
            if authorized, attempted {
                if let candidate {
                    Text("Gateway address found — protection is not configured yet.")
                    Text(verbatim: candidate.address).monospaced().textSelection(.enabled)
                        .environment(\.layoutDirection, .leftToRight)
                    Text("This may be your router's page. Confirm the address in your router's documentation. Enter its password only in the router's own page; Hisn does not collect it.")
                        .foregroundStyle(.secondary)
                    Button("Open router settings") { open(candidate, secure: true) }
                    Button("My router only supports HTTP") { confirmHTTP = true }
                        .confirmationDialog("Open an unencrypted router page?", isPresented: $confirmHTTP) {
                            Button("Open local HTTP page") { open(candidate, secure: false) }
                        } message: {
                            Text("HTTP does not encrypt the administrator password. Use it only on a trusted home network if your router documentation requires it. Do not bypass unexpected certificate warnings.")
                        }
                } else {
                    Text("No supported local gateway address was found. This can happen with a VPN, an IPv6-only network, or a router managed through its own app. Use the router's official app or continue protecting this device.")
                }
                if openFailed {
                    Text("The network changed or the page could not be opened. Find your router again, or use its official app.")
                        .foregroundStyle(.secondary)
                }
                Text("Before changing anything, save the current router settings. In its page, look for Internet or DNS. If those settings are unavailable, continue with device protection.")
                Button("Show the DNS setup steps", action: showDNSGuide)
            }
            Button("Continue with device protection", action: continueOnDevice)
            Button("Set up Hisn's own router list", action: showListGuide)
                .disabled(!authorized)
            Text("Finding a router or opening its page does not verify blocking. After setup, use the DNS check below and test each device separately.")
                .font(.caption).foregroundStyle(.secondary)
        }
        .onChange(of: usesPhoneApp) { _ in
            candidate = nil; attempted = false; openFailed = false
        }
        .font(.callout)
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
