import SwiftUI

/// First-run explanation, in the order the app actually works.
///
/// The window used to open on a twelve-step diagnostic page before the person
/// knew what the app was. This is the shortest honest sequence: what the
/// layers do, the one gap a browser extension cannot close by itself, and
/// where to go next. Each screen asks for one decision; the detailed Setup
/// page stays available, but it is no longer the front door.
struct WelcomeView: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage("hisn.welcome.completed") private var welcomeCompleted = false
    @State private var step = 0
    @State private var showPrivateBrowsing = false
    @State private var privateStatus = PrivateBrowsingStatus.current(force: true)
    @State private var managed = ManagedMode.current()

    private let stepCount = 3

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Label("Hisn", systemImage: "shield.lefthalf.filled")
                    .font(.headline)
                    .foregroundStyle(Color.accentColor)
                Spacer()
                Button("Skip") { dismiss() }
                    .buttonStyle(.plain)
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 26)
            .padding(.top, 22)

            ScrollView {
                Group {
                    switch step {
                    case 0:  howItWorks
                    case 1:  privateWindows
                    default: ready
                    }
                }
                .frame(maxWidth: .infinity, alignment: .topLeading)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(.horizontal, 26)
            .padding(.top, 18)

            footer
                .padding(.horizontal, 26)
                .padding(.bottom, 22)
        }
        .frame(width: 640, height: 560)
        .background(Color(nsColor: .windowBackgroundColor))
        .sheet(isPresented: $showPrivateBrowsing) {
            PrivateBrowsingSheet()
        }
        .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in
            PrivateBrowsingStatus.invalidateCache()
            privateStatus = PrivateBrowsingStatus.current(force: true)
        }
        .onDisappear {
            // The render tests host the app; closing their throwaway window
            // must not mark the real first run as complete.
            if !AppDelegate.isHostingTests { welcomeCompleted = true }
        }
    }

    // MARK: Step 1 — what the app does

    private var howItWorks: some View {
        VStack(alignment: .leading, spacing: 22) {
            VStack(alignment: .leading, spacing: 6) {
                Text("How Hisn works")
                    .font(.largeTitle.weight(.semibold))
                    .accessibilityAddTraits(.isHeader)
                Text("Three layers, each covering a different way around the others.")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            VStack(spacing: 14) {
                layerRow("checkmark.shield.fill", "Every app",
                         "Blocks known adult sites on this Mac, not only in one browser.", .green)
                layerRow("puzzlepiece.extension.fill", "Your browser",
                         "Reads page text the lists have never seen. Chrome, Helium and other Chromium browsers.", .blue)
                layerRow("lock.fill", "A lock",
                         "Makes protection harder to turn off when you need a commitment.", .orange)
            }

            Text("Matching stays on this Mac. There is no account and no browsing log.")
                .font(.callout)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: Step 2 — the private-window decision

    private var privateWindows: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 6) {
                Text("One gap to close")
                    .font(.largeTitle.weight(.semibold))
                    .accessibilityAddTraits(.isHeader)
                Text("Private windows skip the browser extension. Hisn can disable them in the browsers below.")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if privateStatus.controllable.isEmpty {
                Text("No Chromium or Firefox browser is installed, so there is nothing to disable here.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(privateStatus.controllable) { browser in
                        HStack(spacing: 10) {
                            Image(systemName: browser.family == .firefox ? "flame" : "globe")
                                .foregroundStyle(browser.isCovered ? Color.green : Color.orange)
                                .frame(width: 22)
                                .accessibilityHidden(true)
                            Text(browser.name).font(.callout.weight(.medium))
                            Spacer(minLength: 8)
                            Text(browser.stateKey)
                                .font(.caption)
                                .foregroundStyle(browser.isCovered ? Color.secondary : Color.orange)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 9)
                        if browser.id != privateStatus.controllable.last?.id {
                            Divider().padding(.leading, 36)
                        }
                    }
                }
                .background(Color(nsColor: .controlBackgroundColor),
                            in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(Color(nsColor: .separatorColor), lineWidth: 1)
                        .accessibilityHidden(true)
                }

                if privateStatus.open.isEmpty {
                    Label("Private windows are covered in every browser Hisn can control.",
                          systemImage: "checkmark.shield.fill")
                        .font(.callout.weight(.medium))
                        .foregroundStyle(Color.green)
                } else {
                    HStack(spacing: 12) {
                        Button("Prevent private browsing") { showPrivateBrowsing = true }
                            .buttonStyle(.borderedProminent)
                            .controlSize(.large)
                        if managed.isManaged {
                            Text("Managed by \(managed.displayName). Private windows must stay off on this Mac.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .fixedSize(horizontal: false, vertical: true)
                        } else {
                            Button("Not now") { step = 2 }
                                .controlSize(.large)
                        }
                    }
                }
            }
        }
    }

    // MARK: Step 3 — where to go

    private var ready: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 6) {
                Text("What happens next")
                    .font(.largeTitle.weight(.semibold))
                    .accessibilityAddTraits(.isHeader)
                Text("The Overview shows what is running and the one next step. Setup holds the detailed checks whenever you want them.")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if !privateStatus.isCovered {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "eye.slash.fill")
                        .foregroundStyle(Color.orange)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Private windows are still open in \(privateStatus.openNames).")
                            .font(.callout.weight(.medium))
                        Text("You can change that later in Setup or from the Overview.")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
    }

    // MARK: Footer

    private var footer: some View {
        HStack(spacing: 12) {
            HStack(spacing: 6) {
                ForEach(0..<stepCount, id: \.self) { index in
                    Circle()
                        .fill(index == step ? Color.accentColor : Color.secondary.opacity(0.25))
                        .frame(width: 7, height: 7)
                }
            }
            .accessibilityHidden(true)
            Spacer()
            if step > 0 {
                Button("Back") { step -= 1 }
                    .controlSize(.large)
            }
            if step < stepCount - 1 {
                Button("Continue") { step += 1 }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .keyboardShortcut(.defaultAction)
            } else {
                Button("Finish setup") {
                    dismiss()
                    AppNavigation.shared.open(.setup)
                }
                .controlSize(.large)
                Button("Open Overview") { dismiss() }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .keyboardShortcut(.defaultAction)
            }
        }
    }

    private func layerRow(_ symbol: String, _ title: LocalizedStringKey,
                          _ detail: LocalizedStringKey, _ tint: Color) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: symbol)
                .font(.title3)
                .foregroundStyle(tint)
                .frame(width: 38, height: 38)
                .background(tint.opacity(0.12), in: Circle())
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.headline)
                Text(detail)
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
    }
}
