import Foundation
import NetworkExtension

/// Entry point for the content-filter system extension.
///
/// A system extension is a standalone executable, not a plugin the host loads,
/// so it needs a `main` of its own. `startSystemExtensionMode()` hands control
/// to NetworkExtension, which reads `NEProviderClasses` from Info.plist and
/// instantiates `FilterDataProvider`. If that key is missing or names a class
/// that cannot be found, this call succeeds and the process then sits there
/// filtering nothing — which looks exactly like a working install.
///
/// `dispatchMain()` never returns: it parks the main thread on the main queue
/// so the process stays alive to service flows. Returning from `main` here
/// would exit the extension the moment it finished launching.
autoreleasepool {
    NEProvider.startSystemExtensionMode()
}

dispatchMain()
