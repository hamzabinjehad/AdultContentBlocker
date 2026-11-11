import AppKit
import SwiftUI

/// Hisn runs on macOS 12 and newer. SwiftUI grew several of the controls the
/// window uses in macOS 13, so the constructs that matter live here in one
/// place, with the macOS 12 spelling beside the newer one rather than scattered
/// through the pages.
///
/// `HnCompat.forceLegacy` exists so the layout tests can render the macOS 12
/// path on a newer Mac. The fallbacks then cost a capture assertion instead of
/// a bug that only appears on a machine this project does not run its tests on.
enum HnCompat {
    /// Set by `HISN_FORCE_LEGACY_UI=1` (or by a test) to take every fallback
    /// below even when the running macOS has the newer API.
    static var forceLegacy = ProcessInfo.processInfo.environment["HISN_FORCE_LEGACY_UI"] == "1"
}

/// The main window's two columns.
///
/// `NavigationSplitView` is macOS 13. Before it there is no SwiftUI sidebar
/// container that separates a custom sidebar from a detail view (macOS
/// `NavigationView` only takes a single content view; it builds its split from
/// a `List` of `NavigationLink`s), so the fallback is `HSplitView` — a real
/// draggable split, without the sidebar material or the collapse button.
struct HnMainSplit<Sidebar: View, Detail: View>: View {
    var sidebarIdealWidth: CGFloat = 232
    let sidebar: Sidebar
    let detail: Detail

    init(sidebarIdealWidth: CGFloat = 232,
         @ViewBuilder sidebar: () -> Sidebar,
         @ViewBuilder detail: () -> Detail) {
        self.sidebarIdealWidth = sidebarIdealWidth
        self.sidebar = sidebar()
        self.detail = detail()
    }

    var body: some View {
        if #available(macOS 13.0, *), !HnCompat.forceLegacy {
            NavigationSplitView {
                sidebar.navigationSplitViewColumnWidth(min: 212, ideal: sidebarIdealWidth, max: 268)
            } detail: {
                detail
            }
        } else {
            // `NavigationView` on macOS has no sidebar/detail initialiser —
            // that split came with `NavigationSplitView`. `HSplitView` is the
            // macOS 12 split: the same draggable divider, the same sidebar on
            // the leading side, with the sidebar colour drawn by hand because
            // there is no sidebar material to inherit. There is no collapse
            // button; the sidebar is simply always there.
            HSplitView {
                sidebar
                    .frame(minWidth: 212, idealWidth: sidebarIdealWidth,
                           maxWidth: 268, maxHeight: .infinity)
                    .background(HnPalette.sidebar)
                detail
                    .frame(minWidth: 420, maxWidth: .infinity, maxHeight: .infinity)
            }
        }
    }
}

extension View {
    /// Hide the native background of a scrollable control — a `ScrollView`, or
    /// a `TextEditor` sitting in a raised well.
    ///
    /// `scrollContentBackground` arrived in macOS 13. On 12 the `TextEditor`
    /// is an `NSTextView` that paints `textBackgroundColor`, which would cut a
    /// white (or near-black) rectangle out of the design's own surface. The
    /// fallback clears that text view once it is in the hierarchy; a plain
    /// `ScrollView` draws no background there, so it is left alone.
    func hnHiddenScrollContentBackground() -> some View {
        modifier(HnHiddenScrollContentBackground())
    }
}

private struct HnHiddenScrollContentBackground: ViewModifier {
    func body(content: Content) -> some View {
        if #available(macOS 13.0, *), !HnCompat.forceLegacy {
            content.scrollContentBackground(.hidden)
        } else {
            content.background(HnNativeTextBackgroundClearer().allowsHitTesting(false))
        }
    }
}

/// Finds the `NSTextView` behind a `TextEditor` and makes it transparent. The
/// probe sits in the editor's background, so the first ancestor that holds a
/// text view is the editor's own.
private struct HnNativeTextBackgroundClearer: NSViewRepresentable {
    func makeNSView(context: Context) -> NSView {
        HnBackgroundProbeView()
    }

    func updateNSView(_ nsView: NSView, context: Context) {}
}

private final class HnBackgroundProbeView: NSView {
    override var isOpaque: Bool { false }

    override func viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        guard window != nil else { return }
        // The hosting view installs and sizes the text view in the same
        // pass; clear on the next turn, when it exists.
        DispatchQueue.main.async { [weak self] in self?.clearTextBackground() }
    }

    private func clearTextBackground() {
        var ancestor = superview
        for _ in 0..<8 {
            guard let view = ancestor else { return }
            if let scrollView = HnBackgroundProbeView.textScrollView(in: view) {
                scrollView.drawsBackground = false
                scrollView.backgroundColor = .clear
                scrollView.borderType = .noBorder
                (scrollView.documentView as? NSTextView)?.drawsBackground = false
                return
            }
            ancestor = view.superview
        }
    }

    private static func textScrollView(in view: NSView, depth: Int = 0) -> NSScrollView? {
        guard depth <= 6 else { return nil }
        if let scrollView = view as? NSScrollView, scrollView.documentView is NSTextView {
            return scrollView
        }
        for subview in view.subviews {
            if let found = textScrollView(in: subview, depth: depth + 1) { return found }
        }
        return nil
    }
}

/// The window a SwiftUI view ended up in — the only way to reach the frame,
/// close button and delegate of the window SwiftUI created for `WindowGroup`.
struct HnWindowReader: NSViewRepresentable {
    let onWindow: (NSWindow) -> Void

    func makeNSView(context: Context) -> NSView {
        let probe = HnWindowProbeView()
        probe.onWindow = onWindow
        return probe
    }

    func updateNSView(_ nsView: NSView, context: Context) {
        (nsView as? HnWindowProbeView)?.onWindow = onWindow
    }
}

private final class HnWindowProbeView: NSView {
    var onWindow: ((NSWindow) -> Void)?

    override func viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        guard let window else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self, self.window === window else { return }
            self.onWindow?(window)
        }
    }
}
