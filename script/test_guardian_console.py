"""The laptop console round-trips the pairing and policy protocol through files."""
import contextlib
import io
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import guardian_console as console
import guardian_protocol as gp

NOW = 1_700_000_000


def run(*argv: str) -> tuple[int, str, str]:
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        code = console.main(list(argv))
    return code, out.getvalue(), err.getvalue()


class KeygenTests(unittest.TestCase):
    def test_writes_a_private_key_and_public_key(self):
        with tempfile.TemporaryDirectory() as tmp:
            code, out, _ = run("keygen", "--out", tmp, "--no-password")
            self.assertEqual(code, 0)
            private = Path(tmp) / "guardian.key"
            public = Path(tmp) / "guardian.pub"
            self.assertTrue(private.is_file() and public.is_file())
            self.assertEqual(private.stat().st_mode & 0o777, 0o600)
            key = gp.load_private_pem(private.read_bytes())
            loaded = gp.load_public_b64(public.read_text().strip())
            self.assertEqual(gp.public_der(key.public_key()), gp.public_der(loaded))
            self.assertIn("device id", out)

    def test_refuses_to_replace_a_key_without_force(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(run("keygen", "--out", tmp, "--no-password")[0], 0)
            code, _, err = run("keygen", "--out", tmp, "--no-password")
            self.assertEqual(code, 1)
            self.assertIn("exists", err)
            self.assertEqual(run("keygen", "--out", tmp, "--no-password", "--force")[0], 0)

    def test_encrypted_key_requires_the_password_environment(self):
        with tempfile.TemporaryDirectory() as tmp, mock.patch.dict(os.environ, {"HISN_PW": "correct"}):
            self.assertEqual(run("keygen", "--out", tmp, "--password-env", "HISN_PW")[0], 0)
            key_path = str(Path(tmp) / "guardian.key")
            self.assertEqual(run("offer", "--key", key_path, "--password-env", "HISN_PW")[0], 0)
            self.assertEqual(run("offer", "--key", key_path, "--password-env", "MISSING")[0], 1)

    def test_non_interactive_keygen_without_a_password_fails(self):
        with tempfile.TemporaryDirectory() as tmp, mock.patch("sys.stdin.isatty", return_value=False):
            code, _, err = run("keygen", "--out", tmp)
            self.assertEqual(code, 1)
            self.assertIn("--password-env or --no-password", err)


class OfferTests(unittest.TestCase):
    def test_offer_prints_a_parseable_uri(self):
        with tempfile.TemporaryDirectory() as tmp:
            run("keygen", "--out", tmp, "--no-password")
            key = str(Path(tmp) / "guardian.key")
            code, out, _ = run("offer", "--key", key, "--label", "Guardian test")
            self.assertEqual(code, 0)
            offer = gp.parse_offer(out.strip())
            self.assertEqual(offer.label, "Guardian test")

    def test_reproducible_nonce_is_accepted(self):
        with tempfile.TemporaryDirectory() as tmp:
            run("keygen", "--out", tmp, "--no-password")
            key = str(Path(tmp) / "guardian.key")
            code, out, _ = run("offer", "--key", key, "--nonce", "00" * 16)
            self.assertEqual(code, 0)
            self.assertEqual(gp.b64d(gp.parse_offer(out.strip()).nonce), b"\x00" * 16)


class ResponseTests(unittest.TestCase):
    def test_verify_response_prints_the_sas_the_device_sees(self):
        with tempfile.TemporaryDirectory() as tmp:
            run("keygen", "--out", tmp, "--no-password")
            key_path = Path(tmp) / "guardian.key"
            key = gp.load_private_pem(key_path.read_bytes())
            device = gp.generate_key()
            offer = gp.build_offer(key.public_key(), nonce=b"0123456789abcdef")
            response = gp.build_response(device, offer)
            response_path = Path(tmp) / "response.json"
            response_path.write_text(json.dumps(response))
            code, out, _ = run("verify-response", "--key", str(key_path),
                               "--offer", offer.uri, "--response", str(response_path))
            self.assertEqual(code, 0, out)
            data = json.loads(out)
            self.assertEqual(data["device_id"], gp.device_id(device.public_key()))
            self.assertEqual(data["sas"], gp.device_sas(device, offer))

    def test_tampered_response_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            run("keygen", "--out", tmp, "--no-password")
            key_path = Path(tmp) / "guardian.key"
            key = gp.load_private_pem(key_path.read_bytes())
            device = gp.generate_key()
            offer = gp.build_offer(key.public_key())
            response = gp.build_response(device, offer)
            response["proof"] = gp.b64e(b"not a signature")
            response_path = Path(tmp) / "response.json"
            response_path.write_text(json.dumps(response))
            code, _, err = run("verify-response", "--key", str(key_path),
                               "--offer", offer.uri, "--response", str(response_path))
            self.assertEqual(code, 1)
            self.assertIn("proof", err)


class PolicyTests(unittest.TestCase):
    def _key(self, tmp: str) -> tuple[Path, Path]:
        self.assertEqual(run("keygen", "--out", tmp, "--no-password")[0], 0)
        return Path(tmp) / "guardian.key", Path(tmp) / "guardian.pub"

    def test_sign_and_verify_round_trip(self):
        with tempfile.TemporaryDirectory() as tmp:
            key_path, public_path = self._key(tmp)
            device = gp.generate_key()
            device_id = gp.device_id(device.public_key())
            rules = Path(tmp) / "rules.json"
            rules.write_text(json.dumps({"strict": True, "blocked_hosts_sha256": "00"}))
            policy = Path(tmp) / "policy.json"
            code, _, _ = run("sign-policy", "--key", str(key_path), "--device", device_id,
                             "--counter", "7", "--issued-at", str(NOW),
                             "--not-after", str(NOW + 3600), "--rules", str(rules),
                             "--out", str(policy))
            self.assertEqual(code, 0)
            code, out, _ = run("verify-policy", "--public-key", str(public_path),
                               "--policy", str(policy), "--device", device_id,
                               "--now", str(NOW))
            self.assertEqual(code, 0)
            self.assertEqual(json.loads(out)["rules"]["strict"], True)

    def test_wrong_device_or_replay_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            key_path, public_path = self._key(tmp)
            device = gp.generate_key()
            device_id = gp.device_id(device.public_key())
            rules = Path(tmp) / "rules.json"
            rules.write_text("{}")
            policy = Path(tmp) / "policy.json"
            run("sign-policy", "--key", str(key_path), "--device", device_id,
                "--counter", "2", "--issued-at", str(NOW), "--not-after", str(NOW + 3600),
                "--rules", str(rules), "--out", str(policy))
            code, _, _ = run("verify-policy", "--public-key", str(public_path),
                             "--policy", str(policy), "--device", "other", "--now", str(NOW))
            self.assertEqual(code, 1)
            code, _, _ = run("verify-policy", "--public-key", str(public_path),
                             "--policy", str(policy), "--device", device_id,
                             "--last-counter", "2", "--now", str(NOW))
            self.assertEqual(code, 1)

    def test_non_object_rules_are_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            key_path, _ = self._key(tmp)
            device_id = gp.device_id(gp.generate_key().public_key())
            rules = Path(tmp) / "rules.json"
            rules.write_text("[]")
            code, _, err = run("sign-policy", "--key", str(key_path), "--device", device_id,
                               "--counter", "1", "--rules", str(rules),
                               "--out", str(Path(tmp) / "policy.json"))
            self.assertEqual(code, 1)
            self.assertIn("object", err)


if __name__ == "__main__":
    unittest.main()
