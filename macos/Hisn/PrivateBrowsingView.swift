import SwiftUI
import AppKit

/// The display form of a browser state. A `LocalizedStringKey` follows the
/// SwiftUI environment, which is what the Arabic/RTL render tests exercise.
extension PrivateBrowsingBrowser {
    var stateKey: LocalizedStringKey {
        switch state {
        case .disabled:         return "Private windows are off"
        case .extensionChecked: return "Checked by the extension"
        case .open:             return "Private windows are open"
        case .unsupported:      return "Not controlled by Hisn"
        }
    }
}

/// The consent and status sheet for private browsing.
///
/// One decision, one button. When the person agrees, Hisn writes a narrow
/// configuration profile and hands it to System Settings; it never installs
/// silently and never touches DNS, guest windows or any other setting. When
/// they decline, nothing changes and the Overview keeps showing the gap.
struct PrivateBrowsingSheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var lock = LockManager.shared
    @State private var managed = ManagedMode.current()
    @State private var status = PrivateBrowsingStatus.current(force: true)
    @State private var phase: Phase = .review
    @State private var confirm = false
    @State private var error: String?
    @State private var notice: String?

    private enum Phase { case review, approve, done }

    private var openBrowsers: [PrivateBrowsingBrowser] { status.open }
    private var controllable: [PrivateBrowsingBrowser] { status.controllable }
    private var unsupported: [PrivateBrowsingBrowser] { status.unsupported }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            header
            switch phase {
            case .review:  ScrollView { review }
            case .approve: approve
            case .done:    done
            }
            if let error {
                Text(error)
                    .font(.callout)
                    .foregroundStyle(Color.red)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(24)
        .frame(width: 600, height: 560)
        .background(HnPalette.canvas)
        .confirmationDialog("Prevent private browsing?", isPresented: $confirm,
                            titleVisibility: .visible) {
            Button("Create the profile") { install() }
            Button("Not now", role: .cancel) { }
        } message: {
            Text("Hisn will create a configuration profile that disables private windows in \(openBrowsers.map(\.name).formatted(.list(type: .and))). It changes nothing else. You approve it in System Settings.")
        }
        .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in
            refresh(force: true)
        }
    }

    private var header: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "eye.slash")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(HnPalette.accent)
                .frame(width: 20, height: 24)
                .accessibilityHidden(true)
            HStack(spacing: 8) {
                Text("Private windows")
                    .font(.system(size: 22, weight: .semibold, design: .serif))
                    .foregroundStyle(HnPalette.ink)
                    .accessibilityAddTraits(.isHeader)
                HnExplain {
                    Text("Private windows skip the browser extension. Disabling them is the one way to close that gap without turning private browsing into a monitored window.")
                }
                Spacer(minLength: 0)
            }
            Spacer(minLength: 0)
            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 17))
                    .foregroundStyle(HnPalette.faint)
            }
            .buttonStyle(.plain)
            .help("Close")
            .accessibilityLabel(Text("Close"))
        }
    }

    private var review: some View {
        VStack(alignment: .leading, spacing: 16) {
            if controllable.isEmpty {
                Text("No Chromium or Firefox browser is installed, so there is no private-window setting Hisn can change here.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(controllable) { browser in
                        browserRow(browser)
                        if browser.id != controllable.last?.id { HnHairline(inset: 32) }
                    }
                }
                .padding(.vertical, 2)
                .background(HnPalette.card,
                            in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(HnPalette.line, lineWidth: 1)
                        .accessibilityHidden(true)
                }

                if !openBrowsers.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        HnButton(title: "Prevent private browsing", systemImage: "eye.slash",
                                 kind: .filled) { confirm = true }
                        if managed.isManaged {
                            Text("Managed by \(managed.displayName). Private windows must stay off on this Mac.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .fixedSize(horizontal: false, vertical: true)
                            if lock.isLocked {
                                Text("A lock is running. This stays as it is until the lock ends.")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        } else {
                            HStack(spacing: 12) {
                                HnButton(title: "Not now", kind: .outline) { dismiss() }
                                if lock.isLocked {
                                    Text("A lock is running. This stays as it is until the lock ends.")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                            }
                        }
                    }
                } else {
                    Label("Private windows are covered in every browser Hisn can control.",
                          systemImage: "checkmark.shield.fill")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(HnPalette.calm)
                    if !status.disabled.isEmpty, lock.isLocked {
                        Text("A lock is running. Hisn will not offer to re-enable private windows until it ends.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    if managed.isManaged {
                        Text("Managed by \(managed.displayName). Only your administrator can remove this policy.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    HStack(spacing: 12) {
                        if !status.disabled.isEmpty && !managed.isManaged {
                            HnButton(title: "Open Device Management", kind: .quiet) { openProfiles() }
                                .disabled(lock.isLocked)
                                .help(lock.isLocked
                                      ? String(localized: "A lock is running. Wait until it ends to change this.")
                                      : String(localized: "Remove the Hisn private-windows profile here."))
                        }
                        HnButton(title: "Done", kind: .filled) { dismiss() }
                    }
                }
            }

            if !unsupported.isEmpty {
                DisclosureGroup("Browsers Hisn cannot disable private windows in") {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(unsupported) { browser in
                            HStack(alignment: .firstTextBaseline, spacing: 8) {
                                Text(browser.name).font(.callout.weight(.medium))
                                Spacer(minLength: 8)
                                Text(browser.stateKey)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        Text("Safari and hardened browsers do not accept the private-browsing policy. Hisn still blocks listed addresses through the system filter, and the browser guard closes unsupported browsers while Hisn runs.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(.top, 8)
                }
                .font(.callout)
            }
        }
    }

    private var approve: some View {
        VStack(alignment: .leading, spacing: 14) {
            Label("Approve the profile in System Settings", systemImage: "checkmark.seal")
                .font(.system(.headline))
                .foregroundStyle(HnPalette.ink)
            Text("System Settings opened with “Hisn private windows disabled” ready to install. Approve it under Device Management, then return here.")
                .font(.hnCaption())
                .foregroundStyle(HnPalette.muted)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 10) {
                HnButton(title: "Open System Settings", kind: .filled) { openProfiles() }
                HnButton(title: "Check again", systemImage: "arrow.clockwise", kind: .quiet) { refresh(force: true) }
                HnButton(title: "Later", kind: .text) { dismiss() }
            }
            if let notice {
                Text(notice)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var done: some View {
        VStack(alignment: .leading, spacing: 14) {
            Label("Private windows are off", systemImage: "checkmark.shield.fill")
                .font(.system(.title3).weight(.semibold))
                .foregroundStyle(HnPalette.calm)
            Text("Hisn re-read the policy. Private windows are now disabled in \(status.disabled.map(\.name).formatted(.list(type: .and))).")
                .font(.hnCaption())
                .foregroundStyle(HnPalette.muted)
                .fixedSize(horizontal: false, vertical: true)
            HnButton(title: "Done", kind: .filled) { dismiss() }
        }
    }

    private func browserRow(_ browser: PrivateBrowsingBrowser) -> some View {
        HStack(spacing: 10) {
            Image(systemName: browser.family == .firefox ? "flame" : "globe")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(browser.isCovered ? HnPalette.calm : HnPalette.ember)
                .frame(width: 22)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 1) {
                Text(browser.name).font(.system(size: 13, weight: .medium))
                Text(browser.stateKey)
                    .font(.hnCaption())
                    .foregroundStyle(browser.isCovered ? HnPalette.muted : HnPalette.ember)
            }
            Spacer(minLength: 8)
            Image(systemName: browser.isCovered ? "checkmark.circle.fill" : "exclamationmark.circle.fill")
                .foregroundStyle(browser.isCovered ? HnPalette.calm : HnPalette.ember)
                .accessibilityHidden(true)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text(browser.name) + Text(verbatim: ": ") + Text(browser.stateKey))
    }

    private func install() {
        do {
            _ = try PrivateWindowsProfile.openForInstallation(browsers: openBrowsers)
            phase = .approve
            notice = String(localized: "Approve “Hisn private windows disabled” in System Settings › Device Management, then check again.")
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func refresh(force: Bool) {
        PrivateBrowsingStatus.invalidateCache()
        let next = PrivateBrowsingStatus.current(force: force)
        status = next
        if phase == .approve {
            if next.open.isEmpty {
                phase = .done
                notice = nil
            } else {
                notice = String(localized: "The policy is not readable yet. Approve the profile in System Settings, then check again.")
            }
        }
    }

    private func openProfiles() {
        if let url = URL(string: "x-apple.systempreferences:com.apple.Profiles-Settings.extension") {
            NSWorkspace.shared.open(url)
        } else {
            NSWorkspace.shared.open(URL(fileURLWithPath: "/System/Applications/System Settings.app"))
        }
    }
}
