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
        let state = LockStore.read()
        let locked = LockStore.trustedNow() < state.deadline
        return [
            "lockUntil": locked ? state.deadline.timeIntervalSince1970 * 1000 : 0,
            "mode": locked ? state.mode : "off",
            "allowlist": defaults?.stringArray(forKey: "allowlist") ?? [],
            "customBlocks": defaults?.stringArray(forKey: "customBlocks") ?? [],
            "listVersion": defaults?.integer(forKey: "listVersion") ?? 0,
        ]

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
