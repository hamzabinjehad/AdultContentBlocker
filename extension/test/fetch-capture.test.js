// A picture fetched by the extension: bounded, images only, and never a throw.
import { captureFromURL, fetchableImageURL, FETCH_EDGE } from "../visual/fetch-capture.js";

let checks = 0, failures = 0;
function check(ok, label) { checks++; if (!ok) { failures++; print(`  FAIL  ${label}`); } }
globalThis.setTimeout = globalThis.setTimeout ?? (() => 0);
globalThis.clearTimeout = globalThis.clearTimeout ?? (() => {});
if (typeof globalThis.btoa !== "function") {
  const table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  globalThis.btoa = (text) => { let out = "";
    for (let i = 0; i < text.length; i += 3) { const a = text.charCodeAt(i), b = text.charCodeAt(i + 1), c = text.charCodeAt(i + 2);
      out += table[a >> 2] + table[((a & 3) << 4) | (b >> 4 || 0)] + (i + 1 < text.length ? table[((b & 15) << 2) | (c >> 6 || 0)] : "=") + (i + 2 < text.length ? table[c & 63] : "="); }
    return out; };
}

check(fetchableImageURL("https://pictures.example/a.jpg") && fetchableImageURL("http://x.example/a"), "web addresses can be fetched");
for (const bad of ["data:image/png;base64,AA", "blob:https://x/1", "file:///a.png", "chrome://x", "javascript:1", "https://a b", "", null, "https://x/" + "a".repeat(5000)]) {
  check(!fetchableImageURL(bad), `not fetched: ${String(bad).slice(0, 24)}`);
}

function rig({ ok = true, type = "image/jpeg", size = 1000, length = null, width = 768, height = 384, decodeFails = false, fetchFails = false } = {}) {
  const seen = { options: null, drawn: null, closed: 0 };
  class Canvas {
    constructor(w, h) { this.width = w; this.height = h; seen.canvas = [w, h]; }
    getContext() { return { drawImage: (_b, _x, _y, w, h) => { seen.drawn = [w, h]; },
      getImageData: (_x, _y, w, h) => ({ data: new Uint8ClampedArray(w * h * 4).fill(7) }) }; }
  }
  return { seen, options: { Canvas,
    fetchImpl: async (url, options) => { seen.url = url; seen.options = options; if (fetchFails) throw new Error("offline");
      return { ok, headers: { get: (name) => name === "content-type" ? type : name === "content-length" ? length : null },
        blob: async () => ({ size }) }; },
    decode: async () => { if (decodeFails) throw new Error("not an image"); return { width, height, close: () => { seen.closed++; } }; } } };
}

{
  const r = rig(), out = await captureFromURL("https://pictures.example/a.jpg", r.options);
  check(out && out.width === FETCH_EDGE && out.height === FETCH_EDGE / 2, "a large picture is scaled to the same bound the canvas path uses");
  check(typeof out.rgba === "string" && out.rgba.length === 4 * Math.ceil(out.width * out.height * 4 / 3), "the capture has the standard encoded length");
  check(r.seen.options.credentials === "include" && r.seen.options.cache === "force-cache", "the request is made as the page's own would be, from the stored response where there is one");
  check(r.seen.closed === 1, "the decoded bitmap is released");
}
check((await captureFromURL("https://x.example/a", rig({ width: 40, height: 20 }).options)).width === 40, "a small picture is not enlarged");
check(await captureFromURL("https://x.example/a", rig({ ok: false }).options) === null, "an error response is no capture");
check(await captureFromURL("https://x.example/a", rig({ type: "text/html" }).options) === null, "a page is not a picture");
check(await captureFromURL("https://x.example/a", rig({ type: "" }).options) !== null, "an unnamed type is left to the decoder");
check(await captureFromURL("https://x.example/a", rig({ size: 0 }).options) === null, "an empty body is no capture");
check(await captureFromURL("https://x.example/a", rig({ size: 30e6 }).options) === null, "an oversized body is refused");
check(await captureFromURL("https://x.example/a", rig({ length: "99999999" }).options) === null, "an oversized declared length is refused before reading");
check(await captureFromURL("https://x.example/a", rig({ decodeFails: true }).options) === null, "a body that does not decode is no capture");
check(await captureFromURL("https://x.example/a", rig({ fetchFails: true }).options) === null, "a network failure is no capture, never a throw");
check(await captureFromURL("data:image/png;base64,AA", rig().options) === null, "only web addresses are fetched");

print(`  ${checks - failures}/${checks} fetched-capture checks passed`);
if (failures) throw new Error(`${failures} fetched-capture check(s) failed`);
