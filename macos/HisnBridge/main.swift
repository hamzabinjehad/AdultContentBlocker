import Foundation

/// Native messaging host for the browser extension.
///
/// The browser extension cannot be trusted to hold the lock clock: its storage
/// lives in a profile directory the user can edit, and an extension can be
/// disabled. This process is the authority. The extension asks it for the lock
/// state on a heartbeat, and — importantly — treats *silence* as tampering and
/// tightens rather than relaxes. See `handleNativeLoss` in background.js.
///
/// Protocol (Chrome native messaging): each message is a little-endian UInt32
/// length followed by that many bytes of UTF-8 JSON, on stdin/stdout.

// MARK: - Wire format

/// Read exactly `count` bytes, or nil at end of input.
///
/// A pipe is free to hand back fewer bytes than asked for, so a single
/// `readData(ofLength:)` is not the same question as "read this many bytes".
/// Treating a short read as end-of-input exits the host mid-conversation, and
/// the extension — which reads silence as tampering — then fails closed to
/// strict. Loop until the request is satisfied.
func readExactly(_ count: Int) -> Data? {
    var buffer = Data()
    buffer.reserveCapacity(count)
    while buffer.count < count {
        let chunk = FileHandle.standardInput.readData(ofLength: count - buffer.count)
        if chunk.isEmpty { return nil }          // genuine EOF
        buffer.append(chunk)
    }
    return buffer
}

func readMessage() -> [String: Any]? {
    guard let header = readExactly(4) else { return nil }
    let lengthBytes = [UInt8](header)

    let length = UInt32(lengthBytes[0])
        | UInt32(lengthBytes[1]) << 8
        | UInt32(lengthBytes[2]) << 16
        | UInt32(lengthBytes[3]) << 24

    // Chrome caps incoming messages at 4MB; anything larger is malformed or
    // hostile, and allocating on an attacker-supplied length is how you get an
    // easy denial of service.
    guard length > 0, length <= 4 * 1024 * 1024 else { return nil }

    guard let body = readExactly(Int(length)) else { return nil }

    return try? JSONSerialization.jsonObject(with: body) as? [String: Any]
}

func writeMessage(_ object: [String: Any]) {
    guard let data = try? JSONSerialization.data(withJSONObject: object) else { return }
    var length = UInt32(data.count).littleEndian
    let header = Data(bytes: &length, count: 4)
    FileHandle.standardOutput.write(header)
    FileHandle.standardOutput.write(data)
}

// MARK: - Handlers

/// The extension only ever *reads* state here.
///
/// There is deliberately no message that lets the browser shorten a lock,
/// disable the filter, or widen the allowlist. Anything that weakens protection
/// has to go through the app, where the guards live — an extension is far too
/// easy to talk to from a devtools console to be given that authority.
func handle(_ message: [String: Any]) -> [String: Any] {
    let defaults = UserDefaults(suiteName: LockStore.appGroup)

    switch message["type"] as? String {
    case "getLockState":
        // Record that the browser reached us. Nothing else in the system can
        // observe this: the extension heartbeats *into* this process, so
        // without a timestamp here the app has no way to distinguish "the
        // extension is running and polling" from "the extension was never
        // loaded" — and those look identical from the app's side while meaning
        // opposite things. The Overview reports it, because an extension that
        // never connected is a whole enforcement layer that is quietly absent.
        defaults?.set(Date(), forKey: "extensionLastSeen")

        let state = LockStore.read()
        // The EFFECTIVE deadline, so a matured self-release ends the lock in
        // the browser at the same moment it ends everywhere else. Comparing
        // against `state.deadline` here would leave the extension enforcing a
        // lock the app had already released.
        let locked = LockStore.trustedNow() < LockStore.effectiveDeadline(state)
        var reply: [String: Any] = [
            "lockUntil": locked
                ? LockStore.effectiveDeadline(state).timeIntervalSince1970 * 1000
                : 0,
            "mode": locked ? state.mode : "off",
            "allowlist": SiteLists.allowlist(),
            "customBlocks": SiteLists.customBlocks(),
            "listVersion": defaults?.integer(forKey: "listVersion") ?? 0,
        ]
        // Inspection settings ride the same heartbeat. Still read-only: there
        // is deliberately no message that lets the browser change any of these,
        // because an extension is far too easy to talk to from a devtools
        // console to be given that authority.
        reply.merge(Inspection.bridgePayload()) { current, _ in current }
        // Hand-typed words ride the same heartbeat. Apps deliberately do
        // not: the browser cannot enforce them, and shipping a list of
        // someone's installed apps into a process that has no use for it
        // is exposure bought for nothing.
        reply.merge(UserBlocks.bridgePayload()) { current, _ in current }
        return reply

    case "ping":
        return ["ok": true, "at": Date().timeIntervalSince1970 * 1000]

    default:
        return ["ok": false, "reason": "unknown-message"]
    }
}

// MARK: - Loop

while let message = readMessage() {
    writeMessage(handle(message))
}
