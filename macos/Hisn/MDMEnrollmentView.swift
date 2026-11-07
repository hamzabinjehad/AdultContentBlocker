import SwiftUI
import AppKit
import UniformTypeIdentifiers

/// What the Settings card says about management.
///
/// This is deliberately not a claim that the app can enroll the Mac. It
/// separates the facts the person needs: the Mac is enrolled and Hisn is
/// managed, the Hisn profile is local, the Mac is enrolled but Hisn is not
/// deployed, or the Mac is not enrolled at all.
enum MDMHandoff {
    enum State: Equatable {
        case enrolled
        case managedLocally
        case enrolledNotManaged
        case notEnrolled
        case unknown
    }

    static func state(for mode: ManagedMode) -> State {
        switch (mode.isManaged, mode.enrollment) {
        case (true, .enrolled):
            return .enrolled
        case (true, .none):
            return .managedLocally
        case (true, .unknown):
            return .unknown
        case (false, .enrolled):
            return .enrolledNotManaged
        case (false, .none):
            return .notEnrolled
        case (false, .unknown):
            return .unknown
        }
    }

    static func status(for mode: ManagedMode) -> String {
        switch state(for: mode) {
        case .enrolled, .enrolledNotManaged:
            return String(localized: "Enrolled in MDM")
        case .managedLocally:
            return String(localized: "Profile installed locally")
        case .notEnrolled:
            return String(localized: "Not enrolled")
        case .unknown:
            return String(localized: "Enrollment unknown")
        }
    }

    static func detail(for mode: ManagedMode) -> String {
        switch state(for: mode) {
        case .enrolled, .managedLocally:
            // The Organization policy sentence already names the organization
            // and whether the profile is MDM-backed.
            return mode.policyDetail
        case .enrolledNotManaged:
            return String(localized: "This Mac is enrolled in MDM, but the Hisn organization profile is not applied. Ask your administrator to deploy it.")
        case .notEnrolled:
            return String(localized: "An organization's MDM can enroll this Mac and keep the protection profile scoped. Hisn cannot enroll it for you.")
        case .unknown:
            return String(localized: "Hisn could not read the enrollment state. Open Device Management to check.")
        }
    }

    static func symbol(for mode: ManagedMode) -> String {
        switch state(for: mode) {
        case .enrolled:           return "checkmark.shield.fill"
        case .managedLocally:     return "person.badge.key"
        case .enrolledNotManaged: return "exclamationmark.shield.fill"
        case .notEnrolled:        return "building.2"
        case .unknown:            return "questionmark.circle"
        }
    }
}

/// The optional Settings card. It is the only place the app explains its own
/// limit: it can open the enrollment UI, not perform enrollment.
struct DeviceManagementSection: View {
    @ObservedObject private var lock = LockManager.shared
    @State private var mode = ManagedMode.current()
    @State private var showingHandoff = false

    private var enrollmentLocked: Bool { lock.isLocked }

    var body: some View {
        HnGroup(
            title: "Device management",
            subtitle: "Optional. An organization's MDM enrolls the Mac and keeps the protection profile in place; Hisn cannot enroll it for you."
        ) {
            HnGroupRow {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Image(systemName: MDMHandoff.symbol(for: mode))
                            .foregroundStyle(HnPalette.muted)
                            .accessibilityHidden(true)
                        Text(MDMHandoff.status(for: mode))
                            .font(.hnHeadline())
                            .foregroundStyle(HnPalette.ink)
                        Spacer(minLength: 8)
                        if mode.isManaged {
                            Text("Managed by \(mode.displayName)")
                                .font(.hnCaption())
                                .foregroundStyle(HnPalette.muted)
                        }
                    }
                    Text(MDMHandoff.detail(for: mode))
                        .font(.hnBody())
                        .foregroundStyle(HnPalette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                    if let summary = mode.profileSummary {
                        Text(summary)
                            .font(.hnCaption())
                            .foregroundStyle(HnPalette.faint)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    HStack(spacing: 8) {
                        HnButton(
                            title: mode.isMDMEnrolled ? "Review enrollment…" : "Set up with your organization…",
                            systemImage: "building.2",
                            kind: .filled
                        ) { showingHandoff = true }
                        .disabled(enrollmentLocked)
                        HnButton(title: "Check again", kind: .quiet) { check() }
                        Spacer(minLength: 0)
                    }
                    if enrollmentLocked {
                        Text("A lock is running. MDM enrollment waits until it ends.")
                            .font(.hnCaption())
                            .foregroundStyle(HnPalette.ember)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
        .accessibilityIdentifier("settings.deviceManagement")
        .task { check() }
        .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in
            // The sheet runs its own check when the person returns from
            // System Settings; do not start a second probe underneath it.
            if !showingHandoff { check() }
        }
        .sheet(isPresented: $showingHandoff, onDismiss: { check() }) {
            MDMEnrollmentSheet(mode: $mode)
        }
    }

    private func check() {
        MDMEnrollmentProbe.reset()
        mode = ManagedMode.current(probingEnrollment: true)
    }
}

/// The guided handoff. It opens the system UI or a profile the organization
/// supplied; the person approves it there. The app cannot accept it, and this
/// sheet says so rather than implying a one-click enrollment.
struct MDMEnrollmentSheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var lock = LockManager.shared
    @Binding var mode: ManagedMode
    @State private var checking = false
    @State private var message: String?
    @State private var error: String?

    private var enrollmentLocked: Bool { lock.isLocked }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 14) {
                Image(systemName: "building.2.fill")
                    .font(.title2)
                    .foregroundStyle(Color.accentColor)
                    .frame(width: 42, height: 42)
                    .background(Color.accentColor.opacity(0.12), in: Circle())
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 4) {
                    if mode.isMDMEnrolled {
                        Text("Review enrollment…")
                            .font(.title2.weight(.semibold))
                            .accessibilityAddTraits(.isHeader)
                    } else {
                        Text("Set up with your organization…")
                            .font(.title2.weight(.semibold))
                            .accessibilityAddTraits(.isHeader)
                    }
                    Text("Enrollment is run by your organization's MDM server. Hisn cannot enroll this Mac or approve the profile for you.")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }

            Divider()

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Image(systemName: MDMHandoff.symbol(for: mode))
                            .foregroundStyle(.secondary)
                            .accessibilityHidden(true)
                        Text(MDMHandoff.status(for: mode))
                            .font(.headline)
                        Spacer(minLength: 8)
                        if mode.isManaged {
                            Text("Managed by \(mode.displayName)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    Text(MDMHandoff.detail(for: mode))
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)

                    if let summary = mode.profileSummary {
                        Text(summary)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    Text("1. Get the enrollment profile from your organization.\n2. Open it, or open Device Management, and approve it in System Settings.\n3. Return here and check again.")
                        .font(.callout)
                        .fixedSize(horizontal: false, vertical: true)

                    Text("Only install a profile from an organization you trust. A management profile can change settings on this Mac.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)

                    if enrollmentLocked {
                        Text("A lock is running. MDM enrollment waits until it ends.")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    if let error {
                        Text(error)
                            .font(.callout)
                            .foregroundStyle(.red)
                            .fixedSize(horizontal: false, vertical: true)
                    } else if let message {
                        Text(message)
                            .font(.callout)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }

            HStack(spacing: 12) {
                Button("Choose Enrollment Profile…") { chooseProfile() }
                    .controlSize(.large)
                    .disabled(enrollmentLocked)
                Button("Open Device Management") { DeviceManagementSettings.open() }
                    .disabled(enrollmentLocked)
                Spacer(minLength: 8)
                if checking { ProgressView().controlSize(.small) }
                Button("Check again") { check() }
                    .disabled(checking)
                Button("Done") { dismiss() }
                    .keyboardShortcut(.defaultAction)
            }
        }
        .padding(24)
        .frame(width: 640, height: 560)
        .background(Color(nsColor: .windowBackgroundColor))
        .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in
            check()
        }
    }

    private func check() {
        guard !checking else { return }
        checking = true
        error = nil
        // Let the spinner render before the subprocess runs.
        DispatchQueue.main.async {
            MDMEnrollmentProbe.reset()
            let updated = ManagedMode.current(probingEnrollment: true)
            mode = updated
            checking = false
            switch MDMHandoff.state(for: updated) {
            case .enrolled:
                message = String(localized: "This Mac is enrolled in MDM.")
            case .enrolledNotManaged:
                message = nil
            case .unknown:
                message = String(localized: "Hisn could not read the enrollment state yet. If you just approved the profile, wait a moment and check again.")
            case .notEnrolled, .managedLocally:
                message = String(localized: "Still not enrolled. The profile may be waiting for approval in System Settings.")
            }
        }
    }

    private func chooseProfile() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        if let type = UTType(filenameExtension: "mobileconfig") {
            panel.allowedContentTypes = [type]
        }
        panel.message = String(localized: "Choose the enrollment profile your organization sent.")
        panel.prompt = String(localized: "Open")
        guard panel.runModal() == .OK, let url = panel.url else { return }
        guard url.pathExtension.lowercased() == "mobileconfig" else {
            error = String(localized: "The profile could not be opened. Open it from Finder or ask your organization for a new copy.")
            return
        }
        if NSWorkspace.shared.open(url) {
            error = nil
            message = String(localized: "System Settings opened with the profile. Review it there and approve it, then return here.")
        } else {
            error = String(localized: "The profile could not be opened. Open it from Finder or ask your organization for a new copy.")
        }
    }
}

enum DeviceManagementSettings {
    static func open() {
        let pane = "x-apple.systempreferences:com.apple.Profiles-Settings.extension"
        if let url = URL(string: pane), NSWorkspace.shared.open(url) { return }
        NSWorkspace.shared.open(URL(fileURLWithPath: "/System/Applications/System Settings.app"))
    }
}
