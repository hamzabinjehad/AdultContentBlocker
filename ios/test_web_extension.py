import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

import prepare_web_extension as scanner
from network.publish import Rejected


class SafariWebExtensionTests(unittest.TestCase):
    def fixture(self, root, payload=None):
        key = Ed25519PrivateKey.generate()
        public = key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
        (root / "blocklist").mkdir()
        (root / "seed").mkdir()
        (root / "extension/seed").mkdir(parents=True)
        (root / "blocklist/public_key.hex").write_text(public.hex())
        payload = payload if payload is not None else {
            "version": 1, "terms": [{"t": "fixture", "w": 4}],
            "negatives": [{"t": "medical", "w": -2}], "exempt_domains": ["wikipedia.org"]}
        raw = json.dumps(payload, separators=(",", ":")).encode()
        (root / "seed/terms.json").write_bytes(raw)
        (root / "extension/seed/terms.json").write_bytes(raw)
        manifest = json.dumps({"schema": 1, "version": 1, "core_domain_count": 1,
            "artifacts": {"domains_core.txt": {"sha256": "0" * 64, "bytes": 1},
                          "terms.json": {"sha256": hashlib.sha256(raw).hexdigest(), "bytes": len(raw)}}}).encode()
        (root / "seed/manifest.json").write_bytes(manifest)
        (root / "seed/manifest.json.sig").write_text(key.sign(manifest).hex())
        return raw

    def test_verified_terms_accepts_only_signed_matching_seed(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            raw = self.fixture(root)
            self.assertEqual(scanner.verified_terms(root), (raw, 1))
            (root / "seed/terms.json").write_bytes(raw + b" ")
            with self.assertRaises(Rejected): scanner.verified_terms(root)

    def test_scanner_rejects_bad_signature_and_canonical_extension_drift(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.fixture(root)
            signature = (root / "seed/manifest.json.sig").read_text()
            (root / "seed/manifest.json.sig").write_text("00" * 64)
            with self.assertRaises(Rejected): scanner.verified_terms(root)
            (root / "seed/manifest.json.sig").write_text(signature)
            (root / "extension/seed/terms.json").write_text("{}")
            with self.assertRaises(Rejected): scanner.verified_terms(root)

    def test_signed_empty_malformed_wrong_version_terms_are_rejected(self):
        valid = {"version": 1, "terms": [{"t": "fixture", "w": 4}],
                 "negatives": [], "exempt_domains": []}
        for patch in [{"terms": []}, {"terms": None}, {"version": 2}, {"version": True},
                      {"terms": [{"t": "fixture", "w": True}]},
                      {"terms": [{"t": "fixture", "w": -2}]},
                      {"terms": [{"t": "fixture", "w": float("inf")}]},
                      {"terms": [{"t": "", "w": 2}]},
                      {"negatives": [{"t": "medical", "w": 0}]},
                      {"exempt_domains": ["wikipedia.org/path"]}]:
            with self.subTest(patch=patch), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                self.fixture(root, dict(valid, **patch))
                with self.assertRaises(Rejected): scanner.verified_terms(root)

    def test_staged_resources_equal_canonical_sources_and_metadata(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            metadata = scanner.stage(output)
            visual = scanner.verified_visual_resources()
            self.assertEqual(set(metadata["resources"]), set(scanner.RESOURCES) | {"seed/terms.json"} | set(visual))
            for name, entry in metadata["resources"].items():
                raw = (output / name).read_bytes()
                self.assertGreater(len(raw), 0)
                self.assertEqual(len(raw), entry["bytes"])
                self.assertEqual(hashlib.sha256(raw).hexdigest(), entry["sha256"])
                source = scanner.RESOURCES.get(name, f"extension/{name}" if name.startswith("visual/") else "seed/terms.json")
                self.assertEqual(raw, (scanner.ROOT / source).read_bytes())
            self.assertFalse((output / "rules").exists())
            self.assertFalse((output / "public_key.hex").exists())
            self.assertFalse((output / "visual/node_modules").exists())
            self.assertFalse((output / "visual/engine.js").exists())
            self.assertFalse((output / "visual/offscreen.html").exists())
            self.assertFalse((output / "visual/package.json").exists())

    def test_manifest_is_minimal_static_mv3_scanner_without_chrome_policies(self):
        manifest = json.loads((scanner.ROOT / scanner.RESOURCES["manifest.json"]).read_text())
        self.assertEqual(manifest["manifest_version"], 3)
        self.assertEqual(manifest["background"], {"page": "background.html", "persistent": False})
        self.assertEqual(manifest["content_scripts"][0]["js"],
                         ["content/feed.js", "content/scan.js", "visual/capture.js", "content/visual.js"])
        self.assertEqual(manifest["content_scripts"][0]["run_at"], "document_start")
        self.assertNotIn("key", manifest)
        self.assertNotIn("declarative_net_request", manifest)
        self.assertNotIn("nativeMessaging", manifest.get("permissions", []))
        self.assertNotIn("offscreen", manifest.get("permissions", []))
        self.assertEqual(manifest["permissions"], ["storage"])
        self.assertEqual(manifest["action"]["default_popup"], "options.html")
        background = (scanner.ROOT / scanner.RESOURCES["background.html"]).read_text()
        self.assertIn('type="module" src="background.js"', background)

    def visual_fixture(self, root):
        folder = root / "extension/visual"
        folder.mkdir(parents=True)
        artifacts = {}
        for name in scanner.VISUAL_ASSETS:
            raw = (json.dumps({"weightsManifest": [{"paths": [Path(name).stem + ".bin"]}]}).encode()
                   if name in {"models/blazeface.json", "models/gender.json"} else b"fixture visual data")
            destination = folder / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(raw)
            if name in scanner.VISUAL_ASSETS:
                artifacts[name] = {"sha256": hashlib.sha256(raw).hexdigest(), "bytes": len(raw)}
        metadata = {"schema": 1, "revision": "a" * 40, "artifacts": artifacts}
        (folder / "assets.json").write_text(json.dumps(metadata))
        return folder, metadata

    def test_visual_asset_hash_drift_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            folder, _ = self.visual_fixture(root)
            self.assertIn("visual/engine.bundle.js", scanner.verified_visual_resources(root))
            (folder / "models/gender.bin").write_bytes(b"changed model")
            with self.assertRaises(Rejected): scanner.verified_visual_resources(root)

    def test_visual_inventory_cannot_add_build_dependencies_or_paths(self):
        for key in ("../../outside.js", "node_modules/package/index.js", "models/remote.bin"):
            with self.subTest(key=key), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                folder, metadata = self.visual_fixture(root)
                metadata["artifacts"][key] = {"bytes": 1, "sha256": "0" * 64}
                (folder / "assets.json").write_text(json.dumps(metadata))
                with self.assertRaises(Rejected): scanner.verified_visual_resources(root)

    def test_visual_model_manifest_cannot_fetch_unbundled_shards(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            folder, metadata = self.visual_fixture(root)
            raw = json.dumps({"weightsManifest": [{"paths": ["https://example.com/model.bin"]}]}).encode()
            (folder / "models/blazeface.json").write_bytes(raw)
            metadata["artifacts"]["models/blazeface.json"] = {
                "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}
            (folder / "assets.json").write_text(json.dumps(metadata))
            with self.assertRaises(Rejected): scanner.verified_visual_resources(root)

    def test_visual_notices_and_verified_build_are_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            folder, _ = self.visual_fixture(root)
            (folder / "notices/nsfwjs-LICENSE.txt").unlink()
            with self.assertRaises((Rejected, FileNotFoundError)): scanner.verified_visual_resources(root)

    def test_safari_options_document_scope_and_opt_in(self):
        html = (scanner.ROOT / scanner.RESOURCES["options.html"]).read_text()
        self.assertIn('id="inspectVisual" disabled', html)
        self.assertNotIn('id="inspectVisual" checked', html)
        self.assertIn("separate from the app commitment", html)
        self.assertIn("unreadable is not the same as safe", html)
        self.assertIn("Face detection is not whole-body detection", html)
        css = (scanner.ROOT / scanner.RESOURCES["options.css"]).read_text()
        self.assertIn("prefers-color-scheme:dark", css)

    def test_safari_bridge_runtime_contract_with_javascriptcore(self):
        jsc = Path("/System/Library/Frameworks/JavaScriptCore.framework/Versions/A/Helpers/jsc")
        if not jsc.is_file():
            self.skipTest("JavaScriptCore shell is available on macOS only")
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            scanner.stage(output)
            harness = output / "web_extension.test.js"
            harness.write_bytes((scanner.ROOT / "ios/web_extension.test.js").read_bytes())
            result = subprocess.run([str(jsc), "-m", str(harness)],
                                    cwd=output, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("Safari bridge contract checks passed", result.stdout)


if __name__ == "__main__":
    unittest.main()
