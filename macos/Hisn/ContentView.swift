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

    enum CustomUnit: String, CaseIterable, Identifiable {
        case hours = "hours"
        case days = "days"
        case weeks = "weeks"

        var id: String { rawValue }
        var seconds: TimeInterval {
            switch self {
            case .hours: return 3600
            case .days:  return 86400
            case .weeks: return 7 * 86400
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
            HStack {
                Button("Your site lists…") { showLists = true }
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
}

/// Where the person writes their own two lists.
///
/// Both are edited together and saved together, because the guard that matters
/// is about the pair: while a lock runs you may add blocks and withdraw
/// allowances, and neither of the opposites. Saving them separately would let
/// a refused half leave the other half applied.
private struct SiteListsSheet: View {
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
                 : "One domain per line. Subdomains are included automatically, "
                   + "so example.com also covers cdn.example.com.")
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
        VStack(alignment: .leading, spacing: 3) {
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
                .stroke(Color.secondary.opacity(0.35)))
        }
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

    private var problem: String? {
        guard lock.isLocked else { return nil }
        if !filter.isEnabled { return "The system filter is off — reopening it now" }
        if filterDomainCount == 0 {
            return "The filter has no block list — nothing is being blocked"
        }
        return nil
    }

    var body: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(problem == nil ? Color.green : Color.orange)
                .frame(width: 9, height: 9)
            VStack(alignment: .leading, spacing: 1) {
                Text(lock.isLocked ? "Protected" : "Not locked")
                    .font(.subheadline.weight(.semibold))
                if let problem {
                    Text(problem)
                        .font(.caption2).foregroundStyle(.orange)
                }
            }
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
    }
}
