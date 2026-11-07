import SwiftUI

struct RouterIdentityView: View {
    @Binding var profile: RouterProfile

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Text("Which router does your family use?")
                    .font(.system(.callout).weight(.semibold))
                    .foregroundStyle(HnPalette.ink)
                HnExplain {
                    Text("Find the model on the device label or in its official app. Do not enter passwords or serial numbers. These details stay in this setup screen.")
                }
                Spacer(minLength: 0)
            }
            TextField("Router name or model, for example Deco X50", text: $profile.model)
                .hnField()
                .onChange(of: profile.model) { value in profile.model = RouterProfile.clean(value) }
            HStack(spacing: 8) {
                Picker("Router family", selection: $profile.family) {
                    ForEach(RouterFamily.allCases) { family in
                        if family == .unknown { Text("Other / I don't know").tag(family) }
                        else { Text(verbatim: family.name).tag(family) }
                    }
                }
                .pickerStyle(.menu)
                .labelsHidden()
                Spacer(minLength: 0)
            }
            if let suggestion = RouterFamily.suggestion(for: profile.model), suggestion != profile.family {
                HnButton(title: "Use the guide for \(suggestion.name)", kind: .quiet) {
                    profile.family = suggestion
                }
            }
            DisclosureGroup("Hardware and firmware version (optional)") {
                TextField("Version shown in the router's own settings", text: $profile.firmware)
                    .hnField()
                    .onChange(of: profile.firmware) { value in profile.firmware = RouterProfile.clean(value) }
                    .padding(.top, 4)
            }
            .font(.hnCaption())
        }
        .fixedSize(horizontal: false, vertical: true)
    }
}

struct RouterFamilyInstructions: View {
    let profile: RouterProfile
    private var family: RouterFamily { profile.family }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Instructions for this router family")
                .font(.system(.callout).weight(.semibold))
                .foregroundStyle(HnPalette.ink)
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
        .font(.hnCaption())
        .foregroundStyle(HnPalette.muted)
        .fixedSize(horizontal: false, vertical: true)
    }
}
