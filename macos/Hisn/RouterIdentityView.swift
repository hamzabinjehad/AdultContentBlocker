import SwiftUI

struct RouterIdentityView: View {
    @Binding var profile: RouterProfile

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Which router does your family use?").font(.headline)
            TextField("Router name or model, for example Deco X50", text: $profile.model)
                .textFieldStyle(.roundedBorder)
                .onChange(of: profile.model) { value in profile.model = RouterProfile.clean(value) }
            Text("Find the model on the device label or in its official app. Do not enter passwords or serial numbers. These details stay in this setup screen.")
                .font(.caption).foregroundStyle(.secondary)
            Picker("Router family", selection: $profile.family) {
                ForEach(RouterFamily.allCases) { family in
                    if family == .unknown { Text("Other / I don't know").tag(family) }
                    else { Text(verbatim: family.name).tag(family) }
                }
            }
            .pickerStyle(.menu)
            if let suggestion = RouterFamily.suggestion(for: profile.model), suggestion != profile.family {
                Button {
                    profile.family = suggestion
                } label: {
                    Text("Use the guide for \(suggestion.name)")
                }
            }
            DisclosureGroup("Hardware and firmware version (optional)") {
                TextField("Version shown in the router's own settings", text: $profile.firmware)
                    .textFieldStyle(.roundedBorder)
                    .onChange(of: profile.firmware) { value in profile.firmware = RouterProfile.clean(value) }
            }
            if profile.family != .unknown {
                Text("Guide selected from your details. Check that the official instructions cover your model and version before changing settings.")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
        .font(.callout)
        .fixedSize(horizontal: false, vertical: true)
    }
}

struct RouterFamilyInstructions: View {
    let profile: RouterProfile
    private var family: RouterFamily { profile.family }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Instructions for this router family").font(.headline)
            if profile.excludesBuiltInAdguard {
                Text("GL.iNet lists this model as unsupported for built-in AdGuard Home. Use filtering DNS or a separate always-on filtering server instead.")
            } else {
                Text(LocalizedStringKey(family.instructionKey))
            }
            Link("Open the official router guide", destination: family.sourceURL)
            DisclosureGroup("How to use Hisn's own blocking list") {
                Text(LocalizedStringKey(family.listInstructionKey))
                    .padding(.top, 4)
                Link("AdGuard Home project and supported filter rules", destination:
                    URL(string: "https://github.com/AdguardTeam/AdGuardHome/wiki/Hosts-Blocklists")!)
            }
        }
        .font(.callout)
        .fixedSize(horizontal: false, vertical: true)
    }
}
