import AppKit
import SwiftUI
import UniformTypeIdentifiers

/// What a typed or pasted line turns into for one list. Pure, so the add
/// fields can be tested without a window.
enum BlockListEntry {
    struct Result: Equatable {
        var list: [String]
        var added: [String] = []
        var rejected: [String] = []
        var duplicates = 0
    }

    /// Sites: one or many, separated by spaces, commas or new lines. A full
    /// address becomes just its domain.
    static func addingSites(_ input: String, to list: [String]) -> Result {
        var result = Result(list: list)
        for piece in input.split(whereSeparator: { $0.isWhitespace || $0 == "," || $0 == ";" }) {
            guard let domain = SiteLists.normalize(String(piece)) else { result.rejected.append(String(piece)); continue }
            if result.list.contains(domain) { result.duplicates += 1; continue }
            result.list.append(domain)
            result.added.append(domain)
        }
        return result
    }

    /// Words and phrases: one or many, separated by commas or new lines, so a
    /// phrase keeps its spaces.
    static func addingWords(_ input: String, to list: [String]) -> Result {
        var result = Result(list: list)
        let parsed = UserBlocks.parseTerms(input.replacingOccurrences(of: ",", with: "\n"))
        result.rejected = parsed.tooShort
        for term in parsed.terms {
            if result.list.contains(term) { result.duplicates += 1; continue }
            guard result.list.count < UserBlocks.maximumTerms else { result.rejected.append(term); continue }
            result.list.append(term)
            result.added.append(term)
        }
        return result
    }
}

/// Unrot's Block lists: the person's own additions to adult protection, and
/// nothing else. Each list has one home and every change applies at once, so
/// there is no Save to forget. Distracting apps and sites are not here: those
/// are rules, made from New rule.
struct UnrotBlockListsPage: View {
    let isLocked: Bool
    let managed: ManagedMode

    @State private var blocks: [String] = []
    @State private var allows: [String] = []
    @State private var words: [String] = []
    @State private var apps: [String] = []
    @State private var newBlock = ""
    @State private var newAllow = ""
    @State private var newWord = ""
    @State private var message: String?
    @State private var isError = false

    private var restricted: Bool { isLocked || managed.isManaged }

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            VStack(alignment: .leading, spacing: 3) {
                Text("Block lists").font(.hnDisplay()).foregroundStyle(HnPalette.ink)
                    .accessibilityAddTraits(.isHeader)
                Text("Your own additions to adult protection. Changes apply at once.")
                    .font(.system(size: 13)).foregroundStyle(HnPalette.muted)
            }
            if managed.isManaged {
                HnNotice(title: nil,
                         verbatim: String(localized: "Managed by \(managed.displayName). Your organization controls these protections."),
                         systemImage: "building.2.fill", level: .info)
            } else if isLocked {
                HnNotice(title: nil,
                         message: "A lock is running. Add blocks and remove allowances; the reverse waits.",
                         systemImage: "lock.fill", level: .info)
            }
            if let message {
                HStack(spacing: 8) {
                    Image(systemName: isError ? "exclamationmark.triangle.fill" : "checkmark.circle.fill")
                        .foregroundStyle(isError ? HnPalette.ember : HnPalette.calm).accessibilityHidden(true)
                    Text(verbatim: message).font(.system(size: 13)).foregroundStyle(HnPalette.ink)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 14).padding(.vertical, 10)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(isError ? HnPalette.emberSoft : HnPalette.calmSoft))
            }

            section("Sites", emoji: "🚫", count: blocks.count,
                    caption: "Always blocked, with every subdomain. Paste several at once.") {
                if blocks.isEmpty { empty("No extra sites. The published list still applies.") }
                ForEach(blocks, id: \.self) { domain in
                    row(removable: !restricted, label: domain, remove: { saveSites(blocks.filter { $0 != domain }, allows) }) {
                        TargetBadge(target: .site(domain))
                        Text(verbatim: domain).font(.system(size: 14)).foregroundStyle(HnPalette.ink)
                            .lineLimit(1).truncationMode(.middle)
                    }
                }
                addRow(placeholder: "example.com", text: $newBlock, label: "Sites") {
                    let result = BlockListEntry.addingSites(newBlock, to: blocks)
                    guard report(result, sites: true) else { return }
                    if saveSites(result.list, allows, keepMessage: true) { newBlock = "" }
                }
            }

            section("Words", emoji: "💬", count: words.count,
                    caption: "A page is blocked when these appear often in it, so avoid common words.") {
                if words.isEmpty { empty("No words yet.") }
                ForEach(words, id: \.self) { term in
                    row(removable: !restricted, label: term, remove: { saveBlocks(words.filter { $0 != term }, apps) }) {
                        Text(verbatim: term).font(.system(size: 14)).foregroundStyle(HnPalette.ink).lineLimit(1)
                    }
                }
                addRow(placeholder: String(localized: "A word or a phrase"), text: $newWord, label: "Words") {
                    let result = BlockListEntry.addingWords(newWord, to: words)
                    guard report(result, sites: false) else { return }
                    if saveBlocks(result.list, apps, keepMessage: true) { newWord = "" }
                }
            }

            section("Apps without internet", emoji: "📵", count: apps.count,
                    caption: "The app still opens, but it cannot reach the internet.") {
                if apps.isEmpty { empty("No apps blocked.") }
                ForEach(apps, id: \.self) { id in
                    row(removable: !restricted, label: AppCatalog.name(for: id),
                        remove: { saveBlocks(words, apps.filter { $0 != id }) }) {
                        TargetBadge(target: .app(id))
                        Text(verbatim: AppCatalog.name(for: id)).font(.system(size: 14)).foregroundStyle(HnPalette.ink)
                            .lineLimit(1)
                    }
                }
                Button(action: pickApp) {
                    HStack(spacing: 8) {
                        Image(systemName: "plus").font(.system(size: 12, weight: .bold)).foregroundStyle(HnPalette.accentText)
                        Text("Add app…").font(.system(size: 14, weight: .medium)).foregroundStyle(HnPalette.ink)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 16).frame(height: 44).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }

            section("Never blocked", emoji: "✅", count: allows.count,
                    caption: "These always open, even when a list or a word would block them.") {
                if allows.isEmpty { empty("Nothing is let through.") }
                ForEach(allows, id: \.self) { domain in
                    row(removable: true, label: domain, remove: { saveSites(blocks, allows.filter { $0 != domain }) }) {
                        TargetBadge(target: .site(domain))
                        Text(verbatim: domain).font(.system(size: 14)).foregroundStyle(HnPalette.ink)
                            .lineLimit(1).truncationMode(.middle)
                    }
                }
                if restricted {
                    empty("Adding here waits until the lock ends.")
                } else {
                    addRow(placeholder: "example.com", text: $newAllow, label: "Never blocked") {
                        let result = BlockListEntry.addingSites(newAllow, to: allows)
                        guard report(result, sites: true) else { return }
                        if saveSites(blocks, result.list, keepMessage: true) { newAllow = "" }
                    }
                }
            }

            Button {
                AppNavigation.shared.page = .today
                AppNavigation.shared.newRuleRequested = true
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "hourglass").font(.system(size: 12, weight: .semibold))
                    Text("Apps and sites that only distract you are closed with a rule.").font(.system(size: 13))
                    Image(systemName: "chevron.forward").font(.system(size: 9, weight: .semibold))
                }
                .foregroundStyle(HnPalette.muted).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .onAppear(perform: load)
    }

    // MARK: Pieces

    private func section<Content: View>(_ title: LocalizedStringKey, emoji: String, count: Int,
                                        caption: LocalizedStringKey,
                                        @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(verbatim: emoji).font(.system(size: 14)).accessibilityHidden(true)
                Text(title).font(.system(size: 14, weight: .medium)).foregroundStyle(HnPalette.ink)
                    .accessibilityAddTraits(.isHeader)
                if count > 0 {
                    Text(verbatim: "\(count)").font(.system(size: 12, weight: .medium)).foregroundStyle(HnPalette.muted)
                        .monospacedDigit()
                }
                Spacer(minLength: 8)
            }
            VStack(spacing: 0) { content() }
                .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white.opacity(0.06)))
                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.white.opacity(0.08), lineWidth: 0.5))
            Text(caption).font(.system(size: 12)).foregroundStyle(HnPalette.faint)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func empty(_ text: LocalizedStringKey) -> some View {
        VStack(spacing: 0) {
            HStack {
                Text(text).font(.system(size: 13)).foregroundStyle(HnPalette.muted)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16).frame(minHeight: 44)
            divider
        }
    }

    private var divider: some View {
        Rectangle().fill(Color.white.opacity(0.07)).frame(height: 0.5).padding(.leading, 16)
    }

    private func row<Label: View>(removable: Bool, label: String, remove: @escaping () -> Void,
                                  @ViewBuilder content: () -> Label) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                content()
                Spacer(minLength: 8)
                if removable {
                    Button(action: remove) {
                        Image(systemName: "xmark").font(.system(size: 10, weight: .bold)).foregroundStyle(HnPalette.muted)
                            .frame(width: 24, height: 24).background(Circle().fill(Color.white.opacity(0.08)))
                    }
                    .buttonStyle(.plain)
                    .help("Remove")
                    .accessibilityLabel(Text("Remove") + Text(verbatim: " \(label)"))
                } else {
                    Image(systemName: "lock.fill").font(.system(size: 10)).foregroundStyle(HnPalette.faint)
                        .frame(width: 24, height: 24)
                        .help("Cannot remove while a lock is running")
                }
            }
            .padding(.horizontal, 16).frame(minHeight: 44)
            divider
        }
    }

    private func addRow(placeholder: String, text: Binding<String>, label: LocalizedStringKey,
                        add: @escaping () -> Void) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "plus").font(.system(size: 12, weight: .bold)).foregroundStyle(HnPalette.accentText)
                .accessibilityHidden(true)
            TextField(placeholder, text: text)
                .textFieldStyle(.plain).font(.system(size: 14))
                .onSubmit(add)
                .accessibilityLabel(label)
            if !text.wrappedValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Button(action: add) {
                    Text("Add").font(.system(size: 12, weight: .semibold)).foregroundStyle(HnPalette.ink)
                        .padding(.horizontal, 12).frame(height: 26)
                        .background(Capsule().fill(Color.white.opacity(0.12)))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16).frame(height: 44)
    }

    // MARK: Changes

    private func load() {
        blocks = SiteLists.customBlocks()
        allows = SiteLists.allowlist()
        words = UserBlocks.terms()
        apps = UserBlocks.apps()
    }

    /// Says what was left out. False when nothing new came of the entry.
    private func report(_ result: BlockListEntry.Result, sites: Bool) -> Bool {
        if !result.rejected.isEmpty {
            isError = true
            let label = sites ? String(localized: "Not a site") : String(localized: "Too short")
            message = label + ": " + result.rejected.prefix(4).joined(separator: ", ")
        } else if result.added.isEmpty {
            isError = false
            message = result.duplicates > 0 ? String(localized: "Already on the list.") : nil
        } else {
            message = nil
        }
        return !result.added.isEmpty
    }

    @discardableResult private func saveSites(_ newBlocks: [String], _ newAllows: [String], keepMessage: Bool = false) -> Bool {
        do {
            try SiteLists.save(customBlocks: newBlocks, allowlist: newAllows, locked: restricted)
            FilterSync.soon()
            if !keepMessage { message = nil; isError = false }
            load()
            return true
        } catch {
            isError = true
            message = error.localizedDescription
            return false
        }
    }

    @discardableResult private func saveBlocks(_ newWords: [String], _ newApps: [String], keepMessage: Bool = false) -> Bool {
        do {
            try UserBlocks.save(terms: newWords, apps: newApps, locked: restricted)
            FilterSync.soon()
            if !keepMessage { message = nil; isError = false }
            load()
            return true
        } catch {
            isError = true
            message = error.localizedDescription
            return false
        }
    }

    private func pickApp() {
        let panel = NSOpenPanel()
        panel.allowedContentTypes = [.application]
        panel.allowsMultipleSelection = true
        panel.directoryURL = URL(fileURLWithPath: "/Applications")
        panel.prompt = String(localized: "Block")
        guard panel.runModal() == .OK else { return }
        var next = apps
        var failed: [String] = []
        for url in panel.urls {
            if let id = UserBlocks.bundleIdentifier(forAppAt: url) {
                if !next.contains(id) { next.append(id) }
            } else {
                failed.append(FileManager.default.displayName(atPath: url.path))
            }
        }
        next.sort()
        if next != apps { saveBlocks(words, next) }
        if !failed.isEmpty {
            isError = true
            message = String(localized: "Could not read an identifier for \(failed.formatted(.list(type: .and))).")
        }
    }
}
