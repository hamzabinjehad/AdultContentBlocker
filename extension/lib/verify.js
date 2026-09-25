/**
 * Signed-manifest verification for the Hisn blocklist.
 *
 * The extension must never apply a list it did not verify. A blocklist fetched
 * over plain HTTPS is only as trustworthy as the TLS chain and the server; an
 * Ed25519 signature over the manifest means a compromised CDN, a corporate
 * TLS-inspection proxy, or a local attacker with a trusted root cannot swap in
 * an empty list and silently switch the protection off.
 *
 * Ed25519 in WebCrypto requires Chrome 137+ (declared in manifest.json).
 */

/** Public key of the Hisn list-signing key, raw 32 bytes, hex. */
export const PUBLIC_KEY_HEX =
  "eb6751a0429413d0cfb24a778f7d6ecdd8436e573af94c325de5fcbd38e59ede";

function hexToBytes(hex) {
  const clean = hex.trim();
  if (clean.length % 2 !== 0) throw new Error("odd-length hex");
  const out = new Uint8Array(clean.length / 2);
  for (let i = 0; i < out.length; i++) {
    out[i] = parseInt(clean.substr(i * 2, 2), 16);
  }
  return out;
}

function bytesToHex(bytes) {
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

const keyCache = new Map();

async function importPublicKey(hex = PUBLIC_KEY_HEX) {
  if (!keyCache.has(hex)) {
    keyCache.set(hex, await crypto.subtle.importKey(
      "raw", hexToBytes(hex), { name: "Ed25519" }, false, ["verify"]));
  }
  return keyCache.get(hex);
}

/**
 * Verify a detached signature over the exact manifest bytes.
 * @param {ArrayBuffer|Uint8Array} manifestBytes raw bytes as served
 * @param {string} signatureHex detached Ed25519 signature, hex
 * @param {string} publicKeyHex  a seam for tests (test/browser/verify.html),
 *   exactly as BlocklistStore(publicKeyHex:) is in Swift; the worker never
 *   passes it, so production always verifies against the pinned key.
 *
 * Every failure is `false` — a malformed signature included, which used to
 * throw out of `acceptList` instead of refusing the list.
 */
export async function verifyManifest(manifestBytes, signatureHex, publicKeyHex = PUBLIC_KEY_HEX) {
  try {
    const key = await importPublicKey(publicKeyHex);
    return await crypto.subtle.verify({ name: "Ed25519" }, key,
                                      hexToBytes(signatureHex), manifestBytes);
  } catch {
    return false;
  }
}

/** SHA-256 of an artifact, hex — must equal the hash recorded in the manifest. */
export async function sha256Hex(bytes) {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return bytesToHex(new Uint8Array(digest));
}

/**
 * Full client-side acceptance check for a downloaded list.
 *
 * @param {Object} args
 * @param {Uint8Array} args.manifestBytes   raw manifest.json bytes
 * @param {string}     args.signatureHex    contents of manifest.json.sig
 * @param {Map<string,Uint8Array>} args.artifacts  filename -> raw bytes
 * @param {number}     args.heldVersion     version currently installed
 * @returns {Promise<{ok: boolean, reason?: string, manifest?: Object}>}
 */
export async function acceptList({
  manifestBytes,
  signatureHex,
  artifacts,
  heldVersion,
  publicKeyHex = PUBLIC_KEY_HEX,
}) {
  if (!(await verifyManifest(manifestBytes, signatureHex, publicKeyHex))) {
    return { ok: false, reason: "signature-invalid" };
  }

  let manifest;
  try {
    manifest = JSON.parse(new TextDecoder().decode(manifestBytes));
  } catch {
    return { ok: false, reason: "manifest-unparseable" };
  }

  // Rollback protection. Without this, anyone who can serve us an older —
  // still validly signed — manifest can roll the blocklist back to a version
  // that predates whatever they want unblocked.
  if (typeof manifest.version !== "number") {
    return { ok: false, reason: "manifest-no-version" };
  }
  if (heldVersion != null && manifest.version < heldVersion) {
    return { ok: false, reason: "rollback-rejected" };
  }

  // A signed but empty list is a valid signature over a useless list. Refuse
  // it: failing to update is safe, applying an empty list is not.
  if (!manifest.core_domain_count || manifest.core_domain_count < 10000) {
    return { ok: false, reason: "list-suspiciously-small" };
  }

  for (const [name, bytes] of artifacts) {
    const expected = manifest.artifacts?.[name]?.sha256;
    if (!expected) return { ok: false, reason: `artifact-not-in-manifest:${name}` };
    if ((await sha256Hex(bytes)) !== expected) {
      return { ok: false, reason: `artifact-hash-mismatch:${name}` };
    }
  }

  return { ok: true, manifest };
}
