# Extension signing key

`hisn-extension-signing.pem` (git-ignored, matches `*.pem` in the root
`.gitignore`) pins this extension's Chrome/Edge ID so it stays the same across
every unpacked load, every dev machine, and eventual Chrome Web Store
publication — without it, an unpacked extension gets a random ID on every
install location, which is fatal here specifically: the native messaging host
manifest that lets the browser talk to the macOS app has to name the extension
ID in `allowed_origins`, and that has to be decided before the extension is
ever loaded.

Only the **public** half is used at runtime — it lives in `manifest.json`'s
`"key"` field, and Chrome derives the extension ID from it without needing the
private key at all. The private key only matters if you ever re-derive the
public key or need to prove ownership to Google during Chrome Web Store
publication; it is not read by anything in this repo.

Regenerating this key changes the extension ID, which breaks every native
messaging host manifest already written to a user's machine (see
`macos/Hisn/NativeMessagingInstaller.swift`) until the app relaunches and
rewrites them. Treat it like the blocklist signing key: generate it once,
keep it, do not rotate it casually.

To regenerate from scratch (do not do this unless the old key is compromised):

    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
        -out keys/hisn-extension-signing.pem
    openssl rsa -in keys/hisn-extension-signing.pem -pubout -outform DER \
        -out /tmp/pub.der
    python3 -c "
import base64, hashlib
der = open('/tmp/pub.der', 'rb').read()
print('manifest.json key:', base64.b64encode(der).decode())
digest = hashlib.sha256(der).digest()
m = 'abcdefghijklmnop'
print('extension id:', ''.join(m[b >> 4] + m[b & 0xF] for b in digest[:16]))
"

Then update `EXTENSION_ID` in `NativeMessagingInstaller.swift` and `"key"` in
`manifest.json` to match, and bump `extensionKeyVersion` there so installed
copies of the app know to overwrite a stale manifest under the old ID.
