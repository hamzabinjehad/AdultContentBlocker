import SwiftUI

/// Main window.
///
/// The interface has one job beyond starting a lock: make the state honest.
/// A self-control tool that *looks* armed while the filter is off is worse than
/// no tool, because the person stops being careful. So the banner in
/// `StatusHeader` reports what is actually enforced, not what was requested.
struct ContentView: View {
    @StateObject private var lock = LockManager.shared
    @StateObject private var filter = FilterController.shared

    @State private var duration: LockManager.Duration = .week
    @State private var customAmount = ""
    @State private var customUnit: CustomUnit = .days
    @State private var strict = false
    @State private var error: String?
    @State private var showConfirm = false
    @State private var showLists = false
    @State private var showInspection = false
    @State private var showUserBlocks = false
    @State private var showReleaseConfirm = false

    enum CustomUnit: String, CaseIterable, Identifiable {
        // Minutes exist so a lock can be TRIED. Without them the shortest
        // startable lock is an hour, and an hour that cannot be shortened,
        // cancelled or escaped is a steep price for finding out what the
        // button does — so nobody tries it, and the first real lock someone
        // starts is also the first one they have ever seen run.
        //
        // This does not weaken anything: `LockManager.validated` still refuses
        // anything under `minimumLock`, so the floor is 60 seconds either way.
        case minutes = "minutes"
        case hours = "hours"
        case days = "days"
        case weeks = "weeks"

        var id: String { rawValue }
        var seconds: TimeInterval {
            switch self {
            case .minutes: return 60
            case .hours:   return 3600
            case .days:    return 86400
            case .weeks:   return 7 * 86400
            }
        }
    }

    /// The length the button would start, or nil when there is nothing valid to
    /// start. Nil disables the button rather than substituting a nearby legal
    /// value — a lock cannot be shortened afterwards, so quietly turning a
    /// mistyped 3650 into 365 would commit someone to a year they never chose.
    private var selectedSeconds: TimeInterval? {
        if let preset = duration.seconds { return preset }
        guard let amount = Double(customAmount.trimmingCharacters(in: .whitespaces))
        else { return nil }
        return LockManager.validated(seconds: amount * customUnit.seconds)
    }

    var body: some View {
        VStack(spacing: 0) {
            StatusHeader(lock: lock, filter: filter)
            Divider()
            Group {
                if lock.isLocked { activeLock } else { setup }
            }
            .padding(24)
            Divider()
            // Reachable in both states on purpose. Adding a block and dropping
            // an allowance are the two edits that stay legal during a lock, and
            // they are exactly the ones someone wants at the moment they find a
            // site the list missed.
            HStack(spacing: 16) {
                Button("Your site lists…") { showLists = true }
                    .buttonStyle(.link)
                Button("Content checking…") { showInspection = true }
                    .buttonStyle(.link)
                Button("Words & apps…") { showUserBlocks = true }
                    .buttonStyle(.link)
                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 10)
        }
        .frame(width: 420)
        .task { await filter.reassertIfNeeded() }
        .sheet(isPresented: $showLists) {
            SiteListsSheet(isLocked: lock.isLocked)
        }
        .sheet(isPresented: $showInspection) {
            InspectionSheet(isLocked: lock.isLocked)
        }
        .sheet(isPresented: $showUserBlocks) {
            UserBlocksSheet(isLocked: lock.isLocked)
        }
        .alert("Something went wrong", isPresented: .constant(error != nil)) {
            Button("OK") { error = nil }
        } message: { Text(error ?? "") }
    }

    // MARK: - Not locked

    private var setup: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 6) {
                Text("How long?").font(.headline)
                // The Pro marker moved out of the segments. With a fifth
                // option they no longer fit alongside four repeats of " · Pro",
                // and one line below says the same thing once.
                Picker("", selection: $duration) {
                    ForEach(LockManager.Duration.allCases) { d in
                        Text(d.rawValue).tag(d)
                    }
                }
                .pickerStyle(.segmented)
                .labelsHidden()

                if duration == .custom {
                    HStack(spacing: 8) {
                        TextField("How long", text: $customAmount)
                            .textFieldStyle(.roundedBorder)
                            .frame(width: 80)
                        Picker("", selection: $customUnit) {
                            ForEach(CustomUnit.allCases) { Text($0.rawValue).tag($0) }
                        }
                        .labelsHidden()
                        .frame(width: 100)
                        Spacer()
                    }
                    .padding(.top, 2)
                }

                Text(lengthHint)
                    .font(.caption)
                    .foregroundStyle(isCustomInvalid ? Color.red : Color.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Toggle(isOn: $strict) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Strict mode")
                    Text("Blocks everything except sites you allow. "
                         + "A block list can never cover every site; this can.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            // The honest warning. People who are surprised by a lock fight it;
            // people who chose it with clear eyes keep it.
            GroupBox {
                VStack(alignment: .leading, spacing: 8) {
                    Label("This cannot be undone by you alone",
                          systemImage: "lock.fill")
                        .font(.subheadline.weight(.medium))
                    Text("Once started, you cannot shorten or cancel this. "
                         + "Early release takes 48 hours, or an approval from "
                         + "your accountability partner.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(4)
            }

            Button {
                showConfirm = true
            } label: {
                Text(startButtonTitle)
                    .frame(maxWidth: .infinity)
            }
            .controlSize(.large)
            .buttonStyle(.borderedProminent)
            .disabled(selectedSeconds == nil)
            .confirmationDialog(
                startButtonTitle + "?",
                isPresented: $showConfirm, titleVisibility: .visible
            ) {
                Button("Start the lock", role: .destructive) { start() }
                Button("Not yet", role: .cancel) {}
            } message: {
                // Names the wall-clock moment, not just the length. "90 days"
                // is abstract in a way "18 February" is not, and the point of
                // this dialog is that nobody starts a lock they misjudged.
                Text(selectedSeconds.map {
                    "You will not be able to turn this off until "
                        + Date().addingTimeInterval($0).formatted() + "."
                } ?? "")
            }
        }
    }

    // MARK: - Locked

    private var activeLock: some View {
        VStack(spacing: 18) {
            VStack(spacing: 4) {
                Text(lock.remainingDescription)
                    .font(.system(size: 34, weight: .semibold, design: .rounded))
                    .monospacedDigit()
                Text("until \(lock.state.deadline.formatted(date: .abbreviated, time: .shortened))")
                    .font(.caption).foregroundStyle(.secondary)
            }
            .padding(.vertical, 8)

            if let pending = lock.pendingSelfRelease {
                GroupBox {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Early release requested")
                            .font(.subheadline.weight(.medium))
                        Text("Unlocks \(pending.formatted()). "
                             + "You can cancel this, but you cannot speed it up.")
                            .font(.caption).foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                        Button("Cancel the request") { lock.cancelSelfRelease() }
                            .controlSize(.small)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(4)
                }
            } else {
                // The way out. Without this button the request could not be
                // created at all, and the panel above — which only ever offered
                // to *cancel* one — was showing an exit nobody could reach.
                //
                // Deliberately quiet, and deliberately present. The threat model
                // is explicit that a lock with no exit is not the safer design:
                // it is the one people uninstall pre-emptively, and it is
                // dangerous when someone genuinely needs the web. What keeps it
                // honest is that the exit is slow and cannot be hurried.
                Button("Request early release…") { showReleaseConfirm = true }
                    .buttonStyle(.link)
                    .font(.caption)
                    .confirmationDialog(
                        "Request early release?",
                        isPresented: $showReleaseConfirm, titleVisibility: .visible
                    ) {
                        Button("Request it") { requestRelease() }
                        Button("Never mind", role: .cancel) {}
                    } message: {
                        Text("The lock would end "
                             + Date().addingTimeInterval(LockManager.selfReleaseDelay)
                                 .formatted()
                             + ". You can cancel the request at any time, but you "
                             + "cannot make it arrive sooner.")
                    }
            }

            HStack {
                Button("Add a week") {
                    do { try lock.extend(by: 7 * 86400) }
                    catch { self.error = error.localizedDescription }
                }
                if lock.state.mode != "strict" {
                    Button("Switch to strict") {
                        do { try lock.tightenToStrict() }
                        catch { self.error = error.localizedDescription }
                    }
                }
            }
            .controlSize(.regular)

            Text("Making the lock stronger is always available. "
                 + "Making it weaker is not.")
                .font(.caption2).foregroundStyle(.tertiary)
                .multilineTextAlignment(.center)
        }
    }

    private var startButtonTitle: String {
        guard let seconds = selectedSeconds else { return "Start lock" }
        return "Start \(LockManager.describe(seconds)) lock"
    }

    /// Only once something has been typed. An empty field is a person who has
    /// not finished, not a person who is wrong, and colouring it red says the
    /// opposite.
    private var isCustomInvalid: Bool {
        duration == .custom && !customAmount.isEmpty && selectedSeconds == nil
    }

    private var lengthHint: String {
        if isCustomInvalid {
            return "Enter a length between "
                + "\(LockManager.describe(LockManager.minimumLock)) and "
                + "\(LockManager.describe(LockManager.maximumLock))."
        }
        guard let seconds = selectedSeconds else {
            return "Between \(LockManager.describe(LockManager.minimumLock)) and "
                + "\(LockManager.describe(LockManager.maximumLock))."
        }
        let ends = "Ends \(Date().addingTimeInterval(seconds).formatted(date: .abbreviated, time: .shortened))."
        return LockManager.requiresSubscription(seconds: seconds)
            ? ends + " Longer than 7 days is a Pro plan."
            : ends
    }

    private func start() {
        guard let seconds = selectedSeconds else { return }
        Task {
            do { try await lock.start(seconds: seconds, strict: strict) }
            catch { self.error = error.localizedDescription }
        }
    }

    private func requestRelease() {
        do { _ = try lock.requestSelfRelease() }
        catch { self.error = error.localizedDescription }
    }
}

/// Where the person writes their own two lists.
///
/// Both are edited together and saved together, because the guard that matters
/// is about the pair: while a lock runs you may add blocks and withdraw
/// allowances, and neither of the opposites. Saving them separately would let
/// a refused half leave the other half applied.
struct SiteListsSheet: View {
    let isLocked: Bool

    @Environment(\.dismiss) private var dismiss

    @State private var blockText = ""
    @State private var allowText = ""
    @State private var message: String?
    @State private var isError = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Your site lists").font(.headline)
            Text(isLocked
                 ? "A lock is running. You can add blocks and remove allowances; "
                   + "the reverse waits until it ends."
                 : "One domain per line — a full URL is fine, it becomes just the "
                   + "domain. Subdomains are included automatically, so "
                   + "example.com also covers cdn.example.com.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            editor("Always block these",
                   subtitle: "On top of the published list.",
                   text: $blockText,
                   placeholder: "reddit.com")

            editor("Allowed in strict mode",
                   subtitle: "In strict mode nothing else is reachable.",
                   text: $allowText,
                   placeholder: "github.com")

            if let message {
                Text(message)
                    .font(.caption)
                    .foregroundStyle(isError ? Color.red : Color.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            HStack {
                Spacer()
                Button("Cancel") { dismiss() }
                Button("Save") { save() }
                    .keyboardShortcut(.defaultAction)
            }
        }
        .padding(20)
        .frame(width: 460)
        .onAppear {
            blockText = SiteLists.customBlocks().joined(separator: "\n")
            allowText = SiteLists.allowlist().joined(separator: "\n")
        }
    }

    private func editor(_ title: String, subtitle: String,
                        text: Binding<String>, placeholder: String) -> some View {
        // Counted as you type, from the same parser Save uses, so the number
        // here and the outcome on Save can never disagree. A dropped line is
        // the failure this exists to prevent: it looks identical to a saved one
        // and is only discovered when the page you meant to block opens.
        let summary = Self.summarize(text.wrappedValue)
        return VStack(alignment: .leading, spacing: 3) {
            Text(title).font(.subheadline.weight(.medium))
            Text(subtitle).font(.caption2).foregroundStyle(.secondary)
            ZStack(alignment: .topLeading) {
                if text.wrappedValue.isEmpty {
                    Text(placeholder)
                        .font(.system(.body, design: .monospaced))
                        .foregroundStyle(.tertiary)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 8)
                        .allowsHitTesting(false)
                }
                TextEditor(text: text)
                    .font(.system(.body, design: .monospaced))
                    .scrollContentBackground(.hidden)
            }
            .frame(height: 96)
            .overlay(RoundedRectangle(cornerRadius: 6)
                .stroke(summary.hasProblem ? Color.orange.opacity(0.7)
                                           : Color.secondary.opacity(0.35)))
            Text(summary.text)
                .font(.caption2)
                .foregroundStyle(summary.hasProblem ? Color.orange : Color.secondary)
                .frame(minHeight: 12, alignment: .leading)
        }
    }

    /// Live one-line summary of a domain editor's contents.
    static func summarize(_ text: String) -> (text: String, hasProblem: Bool) {
        let r = SiteLists.parse(text)
        let n = r.domains.count
        if r.ignored == 0 {
            return (n == 0 ? "" : "\(n) domain\(n == 1 ? "" : "s")", false)
        }
        return ("\(n) valid · \(r.ignored) line\(r.ignored == 1 ? "" : "s") "
                + "not a domain, will be skipped", true)
    }

    private func save() {
        let blocks = SiteLists.parse(blockText)
        let allows = SiteLists.parse(allowText)
        do {
            try SiteLists.save(customBlocks: blocks.domains,
                               allowlist: allows.domains,
                               locked: isLocked)
        } catch {
            isError = true
            message = error.localizedDescription
            return
        }

        // Saved. Close only if everything typed was understood — a line that
        // was silently dropped looks exactly like one that saved, and someone
        // who mistyped a domain would otherwise walk away believing it is
        // blocked.
        let ignored = blocks.ignored + allows.ignored
        guard ignored > 0 else { return dismiss() }
        isError = false
        message = "Saved \(blocks.domains.count + allows.domains.count) domains. "
            + (ignored == 1 ? "1 line was" : "\(ignored) lines were")
            + " not a domain and had to be left out."
    }
}

/// The content-inspection settings.
///
/// Only layers that actually run appear here. A switch for a feature that is
/// not built would be the "looks protected, is not" failure the threat model
/// rates as worse than being switched off — so there is no image control until
/// there is image classification.
///
/// The copy under each control says WHERE it applies, because the two layers
/// have genuinely different reach and a user who assumes otherwise is being
/// misled: keyword matching runs in the system filter and therefore covers
/// every browser and app, while page-text checking exists only inside the
/// Chrome extension.
private struct InspectionSheet: View {
    let isLocked: Bool

    @Environment(\.dismiss) private var dismiss

    @State private var settings = Inspection.Settings.default
    @State private var message: String?
    @State private var isError = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Content checking").font(.headline)
            Text(isLocked
                 ? "A lock is running. You can turn checks on and raise "
                   + "sensitivity; the reverse waits until it ends."
                 : "These run on top of the site lists, and catch pages the "
                   + "lists have never seen.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            Toggle(isOn: $settings.hostKeywords) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Match keywords in addresses")
                    Text("Catches a site registered today, before any list has "
                         + "it. Works in every app on this Mac.")
                        .font(.caption).foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            Divider()

            Toggle(isOn: $settings.text) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Check the words on a page")
                    Text("Reads the page itself, so it catches explicit content "
                         + "on an ordinary site. Chrome and Edge only.")
                        .font(.caption).foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text("How strict").font(.subheadline)
                    Spacer()
                    Text("\(settings.textSensitivity)")
                        .font(.caption).monospacedDigit()
                        .foregroundStyle(.secondary)
                }
                Slider(value: Binding(
                    get: { Double(settings.textSensitivity) },
                    set: { settings.textSensitivity = Int($0) }
                ), in: 0...100, step: 5)
                .disabled(!settings.text)
                Text("Higher catches more, and blocks more pages that turn out "
                     + "to be innocent. Medical and reference sites are exempt "
                     + "from this check at any setting.")
                    .font(.caption2).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if let message {
                Text(message)
                    .font(.caption)
                    .foregroundStyle(isError ? Color.red : Color.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            HStack {
                Spacer()
                Button("Cancel") { dismiss() }
                Button("Save") { save() }
                    .keyboardShortcut(.defaultAction)
            }
        }
        .padding(20)
        .frame(width: 440)
        .onAppear { settings = Inspection.read() }
    }

    private func save() {
        do {
            try Inspection.save(settings, locked: isLocked)
            dismiss()
        } catch {
            isError = true
            message = error.localizedDescription
        }
    }
}

/// Reports what is genuinely being enforced right now.
///
/// "Enforced" is three separate facts, and the header is wrong unless it checks
/// all of them: a lock is running, the filter is on, *and* the filter holds a
/// list. A filter running with no list blocks nothing while every other signal
/// still reads green — which the threat model rates worse than being switched
/// off, because the person stops being careful.
private struct StatusHeader: View {
    @ObservedObject var lock: LockManager
    @ObservedObject var filter: FilterController

    /// Written by the extension after it loads a list. The app and the filter
    /// are separate processes with separate stores, so this is the only
    /// honest source; anything else would be the app reporting on itself.
    private var filterDomainCount: Int {
        UserDefaults(suiteName: LockStore.appGroup)?
            .integer(forKey: "filterDomainCount") ?? 0
    }

    /// When the browser extension last reached `HisnBridge`. Written by the
    /// bridge on every heartbeat; nil means it has never once connected.
    private var extensionLastSeen: Date? {
        UserDefaults(suiteName: LockStore.appGroup)?
            .object(forKey: "extensionLastSeen") as? Date
    }

    /// The extension polls once a minute, so a gap this long means it stopped
    /// rather than that we caught it between beats.
    private var extensionIsLive: Bool {
        guard let seen = extensionLastSeen else { return false }
        return Date().timeIntervalSince(seen) < 5 * 60
    }

    private var problem: String? {
        guard lock.isLocked else { return nil }
        if !filter.isEnabled { return "The system filter is off — reopening it now" }
        if filterDomainCount == 0 {
            return "The filter has no block list — nothing is being blocked"
        }
        return nil
    }

    /// What is actually enforcing anything, right now, whether or not a lock is
    /// running.
    ///
    /// The header used to answer only "is a lock running", and suppressed every
    /// diagnostic when one was not — `problem` returns nil immediately in the
    /// unlocked state. That left the honest-status promise half kept: a machine
    /// where the system extension had never been approved and the browser
    /// extension had never been loaded showed a green dot and the word "Not
    /// locked", which reads as *nothing is wrong* when the truth is *nothing
    /// would work if you started a lock right now*. Both layers can be absent
    /// silently and independently, so both are named individually rather than
    /// summarised into one word.
    struct Layer: Identifiable {
        let name: String
        let detail: String
        let ok: Bool
        var id: String { name }
    }

    private var layers: [Layer] {
        [
            Layer(name: "System filter", detail:
                  filter.isEnabled
                    ? (filterDomainCount > 0
                        ? "\(filterDomainCount.formatted()) domains"
                        : "running, no list")
                    : "not running",
                  ok: filter.isEnabled && filterDomainCount > 0),
            Layer(name: "Browser extension",
                  detail: extensionIsLive
                    ? "connected"
                    : (extensionLastSeen == nil ? "never connected" : "not responding"),
                  ok: extensionIsLive),
        ]
    }

    private var enforcingCount: Int { layers.filter(\.ok).count }

    /// The headline word. A lock being *recorded* is not the same as protection
    /// being *enforced* — the countdown can be running while the filter never
    /// activated (no signed extension) or is running with no list. Saying
    /// "Protected" in that state is the one dishonesty this header exists to
    /// prevent, so a lock with an unresolved `problem` reads "Not protected"
    /// even though `lock.isLocked` is true. The countdown below still shows the
    /// lock is real; this line is only about whether anything is being blocked.
    private var headline: String {
        if !lock.isLocked { return "Not locked" }
        return problem == nil ? "Protected" : "Not protected"
    }

    /// Grey rather than green when unlocked. Green is a claim that something is
    /// working, and when no lock is running nothing is — reserving it for the
    /// enforcing state is the difference between a status light and decoration.
    private var dotColour: Color {
        if !lock.isLocked { return enforcingCount == 0 ? .orange : .secondary }
        return problem == nil ? .green : .orange
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Circle()
                    .fill(dotColour)
                    .frame(width: 9, height: 9)
                Text(headline)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(problem == nil ? Color.primary : Color.orange)
                Spacer()
            }

            if let problem {
                Text(problem)
                    .font(.caption2)
                    .foregroundStyle(.orange)
                    .padding(.leading, 19)
            }

            // The enforcement layers, always. Naming them individually is the
            // point: each can be absent on its own, and "Not locked" alone
            // never said so.
            VStack(alignment: .leading, spacing: 3) {
                ForEach(layers) { layer in
                    HStack(spacing: 6) {
                        Image(systemName: layer.ok
                              ? "checkmark.circle.fill" : "exclamationmark.circle")
                            .font(.caption2)
                            .foregroundStyle(layer.ok ? Color.green : Color.orange)
                        Text(layer.name)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        Text(layer.detail)
                            .font(.caption2)
                            .foregroundStyle(layer.ok ? Color.secondary : Color.orange)
                        Spacer()
                    }
                }
            }
            .padding(.leading, 19)

            if !lock.isLocked && enforcingCount == 0 {
                Text("Nothing would be enforced if you started a lock now.")
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(.orange)
                    .padding(.leading, 19)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Words and apps the person blocks by hand.
///
/// Separate from `SiteListsSheet` because the two ask different questions. A
/// site list is "which addresses", answered from memory. This is "which words"
/// and "which apps" — the first needs a warning about how easily a bad word
/// blocks the wrong thing, the second needs a file picker, and neither belongs
/// crammed into a sheet about domains.
struct UserBlocksSheet: View {
    let isLocked: Bool

    @Environment(\.dismiss) private var dismiss

    @State private var wordsText = ""
    @State private var apps: [String] = []
    @State private var message: String?
    @State private var isError = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Your words and apps").font(.headline)
            Text(isLocked
                 ? "A lock is running. You can add words and apps; removing "
                   + "them waits until it ends."
                 : "Blocked on top of the built-in lists.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            // --- words ---------------------------------------------------
            VStack(alignment: .leading, spacing: 3) {
                Text("Words").font(.subheadline.weight(.medium))
                Text("One per line. A page is blocked when these appear often "
                     + "enough in it — so a common word blocks ordinary pages "
                     + "too. Minimum \(UserBlocks.minimumTermLength) letters.")
                    .font(.caption2).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                ZStack(alignment: .topLeading) {
                    if wordsText.isEmpty {
                        Text("gambling\nbetting odds")
                            .font(.system(.body, design: .monospaced))
                            .foregroundStyle(.tertiary)
                            .padding(.horizontal, 5).padding(.vertical, 8)
                            .allowsHitTesting(false)
                    }
                    TextEditor(text: $wordsText)
                        .font(.system(.body, design: .monospaced))
                        .scrollContentBackground(.hidden)
                }
                .frame(height: 88)
                .overlay(RoundedRectangle(cornerRadius: 6)
                    .stroke(wordsSummary.hasProblem ? Color.orange.opacity(0.7)
                                                    : Color.secondary.opacity(0.35)))
                // Live, from the same parser Save uses. Catches the too-short
                // words and the 200-word cap here, where they can be fixed,
                // rather than as a refusal after Save.
                Text(wordsSummary.text)
                    .font(.caption2)
                    .foregroundStyle(wordsSummary.hasProblem ? Color.orange : Color.secondary)
                    .frame(minHeight: 12, alignment: .leading)
            }

            // --- apps ----------------------------------------------------
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text("Apps").font(.subheadline.weight(.medium))
                    Spacer()
                    Button("Add app…") { pickApp() }
                        .controlSize(.small)
                }
                // Said plainly rather than discovered later. Someone who blocks
                // a note-taking app and finds it still opens will conclude the
                // feature is broken, when it is working exactly as designed.
                Text("Blocks the app's internet access. It still opens, and "
                     + "still works offline — stopping a launch needs Screen "
                     + "Time, which no app can do for you.")
                    .font(.caption2).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)

                if apps.isEmpty {
                    Text("No apps blocked.")
                        .font(.caption).foregroundStyle(.tertiary)
                        .padding(.vertical, 6)
                } else {
                    VStack(alignment: .leading, spacing: 2) {
                        ForEach(apps, id: \.self) { id in
                            HStack(spacing: 6) {
                                Text(displayName(for: id)).font(.caption)
                                Text(id).font(.caption2).foregroundStyle(.tertiary)
                                Spacer()
                                Button {
                                    apps.removeAll { $0 == id }
                                } label: {
                                    Image(systemName: "minus.circle")
                                }
                                .buttonStyle(.borderless)
                                .disabled(isLocked)
                                .help(isLocked
                                      ? "Cannot remove while a lock is running"
                                      : "Remove")
                            }
                        }
                    }
                    .padding(.vertical, 2)
                }
            }

            if let message {
                Text(message)
                    .font(.caption)
                    .foregroundStyle(isError ? Color.red : Color.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            HStack {
                Spacer()
                Button("Cancel") { dismiss() }
                Button("Save") { save() }.keyboardShortcut(.defaultAction)
            }
        }
        .padding(20)
        .frame(width: 460)
        .onAppear {
            wordsText = UserBlocks.terms().joined(separator: "\n")
            apps = UserBlocks.apps()
        }
    }

    private var wordsSummary: (text: String, hasProblem: Bool) {
        Self.summarizeWords(wordsText)
    }

    /// Live one-line summary of the words box: valid count, and any reason a
    /// line will not be used (too short, unusable, or over the cap). Static and
    /// pure so it is testable without standing up the view.
    static func summarizeWords(_ text: String) -> (text: String, hasProblem: Bool) {
        let r = UserBlocks.parseTerms(text)
        var problems: [String] = []
        if !r.tooShort.isEmpty {
            problems.append("\(r.tooShort.count) too short "
                + "(min \(UserBlocks.minimumTermLength) letters)")
        }
        if r.ignored > 0 { problems.append("\(r.ignored) unusable") }
        if r.terms.count > UserBlocks.maximumTerms {
            problems.append("over the \(UserBlocks.maximumTerms)-word limit")
        }
        if problems.isEmpty {
            let n = r.terms.count
            return (n == 0 ? "" : "\(n) word\(n == 1 ? "" : "s")", false)
        }
        return ("\(r.terms.count) valid · " + problems.joined(separator: ", "), true)
    }

    private func displayName(for bundleID: String) -> String {
        guard let url = NSWorkspace.shared
            .urlForApplication(withBundleIdentifier: bundleID) else { return bundleID }
        return FileManager.default.displayName(atPath: url.path)
    }

    /// Pick the app rather than type its identifier. The filter compares a
    /// signing identifier, which is not something anyone knows by heart, and a
    /// typo produces a rule that silently never matches.
    private func pickApp() {
        let panel = NSOpenPanel()
        panel.allowedContentTypes = [.application]
        panel.allowsMultipleSelection = true
        panel.directoryURL = URL(fileURLWithPath: "/Applications")
        panel.prompt = "Block"
        guard panel.runModal() == .OK else { return }

        var added = 0, failed: [String] = []
        for url in panel.urls {
            if let id = UserBlocks.bundleIdentifier(forAppAt: url) {
                if !apps.contains(id) { apps.append(id); added += 1 }
            } else {
                failed.append(FileManager.default.displayName(atPath: url.path))
            }
        }
        apps.sort()
        isError = !failed.isEmpty
        message = failed.isEmpty
            ? (added == 0 ? "Already on the list." : nil)
            : "Could not read an identifier for \(failed.joined(separator: ", "))."
    }

    private func save() {
        let parsed = UserBlocks.parseTerms(wordsText)
        do {
            try UserBlocks.save(terms: parsed.terms, apps: apps, locked: isLocked)
        } catch {
            isError = true
            message = error.localizedDescription
            return
        }

        // Stay open when something was dropped. A word silently left out looks
        // identical to one that saved, and the person finds out only when the
        // page they expected to be blocked opens normally.
        var notes: [String] = []
        if !parsed.tooShort.isEmpty {
            notes.append("too short to use: \(parsed.tooShort.joined(separator: ", "))")
        }
        if parsed.ignored > 0 {
            notes.append("\(parsed.ignored) line(s) were not usable words")
        }
        guard !notes.isEmpty else { return dismiss() }
        isError = false
        message = "Saved \(parsed.terms.count) words and \(apps.count) apps. "
            + notes.joined(separator: "; ") + "."
    }
}
