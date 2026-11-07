import SwiftUI

/// First-run explanation, in the order the app actually works.
///
/// Three screens, each with one decision. The layer explanations live in
/// `Explain` popovers, so the first screen is a short list rather than an
/// essay; the optional extra blocks give the person a decision they can make
/// immediately; the detailed Setup page stays available afterwards.
struct WelcomeView: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage("hisn.welcome.completed") private var welcomeCompleted = false
    @ObservedObject private var lock = LockManager.shared
    @State private var step = 0
    @State private var showPrivateBrowsing = false
    @State private var privateStatus = PrivateBrowsingStatus.current(force: true)
    @State private var managed = ManagedMode.current()
    @State private var extraBlocks: [String] = []
    @State private var extraError: String?

    private let stepCount = 3

    init(initialStep: Int = 0) {
        _step = State(initialValue: min(max(initialStep, 0), 2))
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                HnMark(size: 24)
                Text("Hisn")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(HnPalette.ink)
                Text(verbatim: "حصن")
                    .font(.system(size: 10.5))
                    .foregroundStyle(HnPalette.muted)
                Spacer()
                Button("Skip") { dismiss() }
                    .buttonStyle(.borderless)
                    .font(.system(.caption).weight(.medium))
                    .foregroundStyle(HnPalette.muted)
            }
            .padding(.horizontal, 24)
            .padding(.top, 20)

            stepBar
                .padding(.horizontal, 24)
                .padding(.top, 12)

            ScrollView {
                Group {
                    switch step {
                    case 0:  howItWorks
                    case 1:  extraSites
                    default: privateWindows
                    }
                }
                .frame(maxWidth: .infinity, alignment: .topLeading)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(.horizontal, 24)
            .padding(.top, 16)

            footer
                .padding(.horizontal, 24)
                .padding(.bottom, 20)
        }
        .frame(width: 600, height: 560)
        .background(HnPalette.canvas)
        .sheet(isPresented: $showPrivateBrowsing) {
            PrivateBrowsingSheet()
        }
        .onAppear {
            extraBlocks = SiteLists.customBlocks()
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

    private var stepBar: some View {
        HStack(spacing: 4) {
            ForEach(0..<stepCount, id: \.self) { index in
                Capsule()
                    .fill(index <= step ? HnPalette.accent : HnPalette.soft)
                    .frame(height: 3)
            }
        }
        .animation(.easeInOut(duration: 0.16), value: step)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Step \(step + 1) of \(stepCount)"))
    }

    // MARK: Step 1 — what the app does

    private var howItWorks: some View {
        VStack(alignment: .leading, spacing: 18) {
            HnPageHeader(eyebrow: "First run", title: "How Hisn works",
                         caption: "Three layers, all on this Mac.")

            HnCard(padding: 0) {
                VStack(spacing: 0) {
                    layerRow("checkmark.shield.fill", "Every app",
                             "Blocks known adult sites on this Mac, not only in one browser.",
                             HnPalette.calm)
                    HnHairline(inset: 44)
                    layerRow("puzzlepiece.extension.fill", "Your browser",
                             "Reads page text the lists have never seen. Chrome, Helium and other Chromium browsers.",
                             HnPalette.accent)
                    HnHairline(inset: 44)
                    layerRow("lock.fill", "A lock",
                             "Makes protection harder to turn off when you need a commitment.",
                             HnPalette.ember)
                }
            }

            HStack(spacing: 6) {
                Image(systemName: "lock.shield")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(HnPalette.faint)
                Text("Matching stays on this Mac. There is no account and no browsing log.")
                    .font(.hnCaption())
                    .foregroundStyle(HnPalette.muted)
            }
        }
    }

    // MARK: Step 2 — optional distraction blocks

    /// A decision the person can make immediately, in the same list the Rules
    /// page edits. It is optional and says so: adult-content protection never
    /// depends on it.
    private var extraSites: some View {
        VStack(alignment: .leading, spacing: 16) {
            HnPageHeader(eyebrow: "First run", title: "Block distractions too?",
                         caption: "Optional. Tap sites to add them to your block list. Adult content stays covered.")

            HnCard {
                ExtraSitesPicker(blocks: extraBlocks,
                                 isLocked: lock.isLocked || managed.isManaged,
                                 showsHeader: false) { domain in
                    toggleSuggestion(domain)
                }
            }

            if let extraError {
                HnNotice(title: nil, verbatim: extraError,
                         systemImage: "exclamationmark.triangle.fill", level: .caution)
            }

            Text("You can change these later in Blocking Rules.")
                .font(.hnCaption())
                .foregroundStyle(HnPalette.muted)
        }
    }

    /// First run writes each tap straight through the same guarded save the
    /// list editor uses. There is no hidden draft: a chip that looks selected
    /// is in the list the filter reads.
    private func toggleSuggestion(_ domain: String) {
        let next = ExtraSites.toggling(domain, in: extraBlocks)
        guard next != extraBlocks else { return }
        do {
            try SiteLists.save(customBlocks: next, allowlist: SiteLists.allowlist(),
                               locked: lock.isLocked || managed.isManaged)
            extraBlocks = next
            extraError = nil
            FilterSync.soon()
        } catch {
            extraError = error.localizedDescription
        }
    }

    // MARK: Step 3 — the private-window decision

    private var privateWindows: some View {
        VStack(alignment: .leading, spacing: 16) {
            HnPageHeader(eyebrow: "First run", title: "One gap to close",
                         caption: "Private windows skip the extension.")

            if privateStatus.controllable.isEmpty {
                Text("No Chromium or Firefox browser is installed, so there is nothing to disable here.")
                    .font(.hnBody())
                    .foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                HnCard(padding: 0) {
                    VStack(spacing: 0) {
                        ForEach(privateStatus.controllable) { browser in
                            HStack(spacing: 9) {
                                Image(systemName: browser.family == .firefox ? "flame" : "globe")
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundStyle(browser.isCovered ? HnPalette.calm : HnPalette.ember)
                                    .frame(width: 18)
                                    .accessibilityHidden(true)
                                Text(browser.name).font(.system(size: 13, weight: .medium))
                                Spacer(minLength: 8)
                                Text(browser.stateKey)
                                    .font(.hnCaption())
                                    .foregroundStyle(browser.isCovered ? HnPalette.muted : HnPalette.ember)
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            if browser.id != privateStatus.controllable.last?.id {
                                HnHairline(inset: 43)
                            }
                        }
                    }
                }

                if privateStatus.open.isEmpty {
                    HStack(spacing: 6) {
                        Image(systemName: "checkmark.shield.fill")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(HnPalette.calm)
                        Text("Private windows are covered in every browser Hisn can control.")
                            .font(.hnCallout())
                            .foregroundStyle(HnPalette.calm)
                    }
                } else {
                    HStack(spacing: 10) {
                        HnButton(title: "Prevent private browsing", systemImage: "eye.slash") {
                            showPrivateBrowsing = true
                        }
                        if managed.isManaged {
                            Text("Managed by \(managed.displayName).")
                                .font(.hnCaption())
                                .foregroundStyle(HnPalette.muted)
                        } else {
                            HnButton(title: "Not now", kind: .quiet) { finish() }
                        }
                        Spacer(minLength: 0)
                    }
                }
            }
        }
    }

    // MARK: Footer

    private var footer: some View {
        HStack(spacing: 10) {
            Spacer()
            if step > 0 {
                HnButton(title: "Back", kind: .quiet) { step -= 1 }
            }
            if step == stepCount - 1 {
                HnButton(title: "Finish", kind: .filled) { finish() }
                    .keyboardShortcut(.defaultAction)
            } else {
                HnButton(title: "Continue", systemImage: "arrow.forward") {
                    step += 1
                }
                .keyboardShortcut(.defaultAction)
            }
        }
    }

    private func finish() {
        dismiss()
        AppNavigation.shared.open(.setup)
    }

    private func layerRow(_ symbol: String, _ title: LocalizedStringKey,
                          _ detail: LocalizedStringKey, _ tint: Color) -> some View {
        HStack(alignment: .top, spacing: 11) {
            Image(systemName: symbol)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(tint)
                .frame(width: 20, height: 20)
                .padding(.top, 1)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(HnPalette.ink)
                Text(detail)
                    .font(.hnCaption())
                    .foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 11)
    }
}
