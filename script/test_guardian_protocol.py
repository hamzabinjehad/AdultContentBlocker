"""The guardian protocol: pairing SAS, replay protection and signed policy."""
import hashlib
import re
import unittest

import guardian_protocol as gp

NOW = 1_700_000_000


def pair():
    guardian = gp.generate_key()
    device = gp.generate_key()
    offer = gp.build_offer(guardian.public_key(), nonce=b"0123456789abcdef", label="Guardian")
    return guardian, device, offer


def policy(guardian, device, *, counter=1, issued=NOW, expires=NOW + 3600,
           rules=None):
    return gp.sign_policy(
        guardian, device=gp.device_id(device.public_key()), counter=counter,
        issued_at=issued, not_after=expires,
        rules={"strict": True} if rules is None else rules)


class KeyTests(unittest.TestCase):
    def test_device_id_is_stable_and_key_specific(self):
        first = gp.generate_key()
        second = gp.generate_key()
        self.assertEqual(gp.device_id(first.public_key()), gp.device_id(first.public_key()))
        self.assertNotEqual(gp.device_id(first.public_key()), gp.device_id(second.public_key()))
        self.assertEqual(len(gp.device_id(first.public_key())), 43)

    def test_public_key_round_trip(self):
        key = gp.generate_key()
        encoded = gp.public_b64(key.public_key())
        loaded = gp.load_public_b64(encoded)
        self.assertEqual(gp.public_der(loaded), gp.public_der(key.public_key()))

    def test_private_pem_round_trip(self):
        key = gp.generate_key()
        plain = gp.private_pem(key)
        loaded = gp.load_private_pem(plain)
        self.assertEqual(gp.public_der(loaded.public_key()), gp.public_der(key.public_key()))
        encrypted = gp.private_pem(key, b"correct horse")
        self.assertIn(b"ENCRYPTED", encrypted)
        self.assertEqual(gp.public_der(gp.load_private_pem(encrypted, b"correct horse").public_key()),
                         gp.public_der(key.public_key()))
        with self.assertRaises(gp.ProtocolError):
            gp.load_private_pem(encrypted, b"wrong")


class DomainHashTests(unittest.TestCase):
    def test_domain_list_hash_is_order_independent(self):
        first = gp.domain_list_sha256(["b.example", "a.example"])
        second = gp.domain_list_sha256(["a.example", "b.example"])
        self.assertEqual(first, second)
        self.assertEqual(first, hashlib.sha256(b"a.example\nb.example").hexdigest())
        self.assertNotEqual(first, gp.domain_list_sha256(["a.example", "c.example"]))


class OfferTests(unittest.TestCase):
    def test_offer_round_trip(self):
        guardian = gp.generate_key()
        offer = gp.build_offer(guardian.public_key(), label="Guardian & co")
        parsed = gp.parse_offer(offer.uri)
        self.assertEqual(parsed, offer)
        self.assertEqual(parsed.label, "Guardian & co")

    def test_offer_round_trips_a_unicode_label(self):
        offer = gp.build_offer(gp.generate_key().public_key(), label="حصن")
        self.assertEqual(gp.parse_offer(offer.uri).label, "حصن")

    def test_offer_carries_a_16_byte_nonce_and_p256_key(self):
        offer = gp.build_offer(gp.generate_key().public_key())
        self.assertEqual(len(gp.b64d(offer.nonce)), 16)
        self.assertIsNotNone(gp.load_public_b64(offer.guardian))

    def test_rejects_other_scheme_or_host(self):
        for uri in ("https://pair/1?gk=x&nonce=y", "hisn://laptop/1?gk=x&nonce=y",
                    "hisn://pair/2?gk=x&nonce=y"):
            with self.assertRaises(gp.ProtocolError):
                gp.parse_offer(uri)

    def test_rejects_duplicate_and_unknown_parameters(self):
        offer = gp.build_offer(gp.generate_key().public_key())
        with self.assertRaises(gp.ProtocolError):
            gp.parse_offer(offer.uri + "&nonce=" + offer.nonce)
        with self.assertRaises(gp.ProtocolError):
            gp.parse_offer(offer.uri + "&extra=1")

    def test_rejects_missing_parameter_and_short_nonce(self):
        guardian = gp.public_b64(gp.generate_key().public_key())
        with self.assertRaises(gp.ProtocolError):
            gp.parse_offer(f"hisn://pair/1?gk={guardian}")
        with self.assertRaises(gp.ProtocolError):
            gp.parse_offer(f"hisn://pair/1?gk={guardian}&nonce=AAAA")
        with self.assertRaises(gp.ProtocolError):
            gp.parse_offer(f"hisn://pair/1?gk=AAAA&nonce={gp.b64e(b'0' * 16)}")

    def test_rejects_control_characters_in_label(self):
        with self.assertRaises(gp.ProtocolError):
            gp.build_offer(gp.generate_key().public_key(), label="bad\nlabel")
        with self.assertRaises(gp.ProtocolError):
            gp.build_offer(gp.generate_key().public_key(), label="x" * (gp.MAX_LABEL + 1))

    def test_rejects_fragment_and_userinfo(self):
        offer = gp.build_offer(gp.generate_key().public_key())
        with self.assertRaises(gp.ProtocolError):
            gp.parse_offer(offer.uri + "#fragment")
        with self.assertRaises(gp.ProtocolError):
            gp.parse_offer(offer.uri.replace("hisn://pair/", "hisn://user@pair/"))


class PairingTests(unittest.TestCase):
    def test_both_sides_derive_the_same_six_digits(self):
        guardian, device, offer = pair()
        response = gp.build_response(device, offer)
        device_key, guardian_sas = gp.verify_response(offer, response, guardian)
        self.assertEqual(gp.public_der(device_key), gp.public_der(device.public_key()))
        self.assertRegex(guardian_sas, r"^\d{6}$")
        self.assertEqual(guardian_sas, gp.device_sas(device, offer))

    def test_mitm_key_substitution_produces_a_different_sas(self):
        guardian, device, offer = pair()
        eve = gp.generate_key()
        eve_response = gp.build_response(eve, offer)
        _, eve_sas = gp.verify_response(offer, eve_response, guardian)
        self.assertEqual(eve_sas, gp.device_sas(eve, offer))
        self.assertNotEqual(eve_sas, gp.device_sas(device, offer))

    def test_rejects_wrong_nonce(self):
        guardian, device, offer = pair()
        response = gp.build_response(device, offer)
        response["nonce"] = gp.b64e(b"x" * 16)
        with self.assertRaises(gp.ProtocolError):
            gp.verify_response(offer, response, guardian)

    def test_rejects_wrong_guardian(self):
        guardian, device, offer = pair()
        response = gp.build_response(device, offer)
        response["guardian"] = gp.public_b64(gp.generate_key().public_key())
        with self.assertRaises(gp.ProtocolError):
            gp.verify_response(offer, response, guardian)

    def test_rejects_bad_proof(self):
        guardian, device, offer = pair()
        response = gp.build_response(device, offer)
        response["proof"] = gp.b64e(b"not a signature")
        with self.assertRaises(gp.ProtocolError):
            gp.verify_response(offer, response, guardian)

    def test_rejects_extra_or_missing_fields(self):
        guardian, device, offer = pair()
        response = gp.build_response(device, offer)
        extra = dict(response, extra=1)
        with self.assertRaises(gp.ProtocolError):
            gp.verify_response(offer, extra, guardian)
        missing = dict(response)
        del missing["proof"]
        with self.assertRaises(gp.ProtocolError):
            gp.verify_response(offer, missing, guardian)

    def test_rejects_a_boolean_version(self):
        guardian, device, offer = pair()
        response = dict(gp.build_response(device, offer), v=True)
        with self.assertRaises(gp.ProtocolError):
            gp.verify_response(offer, response, guardian)


class PolicyTests(unittest.TestCase):
    def setUp(self):
        self.guardian = gp.generate_key()
        self.device = gp.generate_key()
        self.device_id = gp.device_id(self.device.public_key())
        self.body = policy(self.guardian, self.device)

    def test_round_trip_returns_the_signed_body(self):
        verified = gp.verify_policy(self.guardian.public_key(), self.body,
                                    device=self.device_id, now=NOW)
        self.assertEqual(verified["rules"], {"strict": True})
        self.assertEqual(verified["counter"], 1)
        self.assertNotIn("sig", verified)

    def test_rejects_tampered_rules(self):
        tampered = dict(self.body, rules={"strict": False})
        with self.assertRaises(gp.ProtocolError):
            gp.verify_policy(self.guardian.public_key(), tampered, device=self.device_id, now=NOW)

    def test_rejects_another_device(self):
        with self.assertRaises(gp.ProtocolError):
            gp.verify_policy(self.guardian.public_key(), self.body,
                             device=gp.device_id(gp.generate_key().public_key()), now=NOW)

    def test_rejects_replay_at_or_below_the_counter(self):
        with self.assertRaises(gp.ProtocolError):
            gp.verify_policy(self.guardian.public_key(), self.body,
                             device=self.device_id, now=NOW, last_counter=1)
        with self.assertRaises(gp.ProtocolError):
            gp.verify_policy(self.guardian.public_key(), self.body,
                             device=self.device_id, now=NOW, last_counter=2)
        gp.verify_policy(self.guardian.public_key(), self.body,
                         device=self.device_id, now=NOW, last_counter=0)

    def test_rejects_expired_and_future_policy(self):
        with self.assertRaises(gp.ProtocolError):
            gp.verify_policy(self.guardian.public_key(), self.body,
                             device=self.device_id, now=NOW + 3600)
        future = policy(self.guardian, self.device, issued=NOW + 301)
        with self.assertRaises(gp.ProtocolError):
            gp.verify_policy(self.guardian.public_key(), future,
                             device=self.device_id, now=NOW)

    def test_rejects_expiry_before_issue(self):
        with self.assertRaises(gp.ProtocolError):
            policy(self.guardian, self.device, issued=NOW, expires=NOW)

    def test_rejects_unknown_or_missing_fields(self):
        with self.assertRaises(gp.ProtocolError):
            gp.verify_policy(self.guardian.public_key(), dict(self.body, extra=1),
                             device=self.device_id, now=NOW)
        missing = dict(self.body)
        del missing["rules"]
        with self.assertRaises(gp.ProtocolError):
            gp.verify_policy(self.guardian.public_key(), missing, device=self.device_id, now=NOW)

    def test_rejects_bool_as_an_integer(self):
        for field in ("counter", "issued_at", "not_after"):
            bad = dict(self.body, **{field: True})
            with self.assertRaises(gp.ProtocolError):
                gp.verify_policy(self.guardian.public_key(), bad, device=self.device_id, now=NOW)

    def test_rejects_bad_signature_and_wrong_key(self):
        bad = dict(self.body, sig=gp.b64e(b"bad signature"))
        with self.assertRaises(gp.ProtocolError):
            gp.verify_policy(self.guardian.public_key(), bad, device=self.device_id, now=NOW)
        other = gp.generate_key()
        with self.assertRaises(gp.ProtocolError):
            gp.verify_policy(other.public_key(), self.body, device=self.device_id, now=NOW)

    def test_rejects_wrong_version_and_non_object(self):
        with self.assertRaises(gp.ProtocolError):
            gp.verify_policy(self.guardian.public_key(), dict(self.body, v=3),
                             device=self.device_id, now=NOW)
        with self.assertRaises(gp.ProtocolError):
            gp.verify_policy(self.guardian.public_key(), dict(self.body, v=True),
                             device=self.device_id, now=NOW)
        with self.assertRaises(gp.ProtocolError):
            gp.verify_policy(self.guardian.public_key(), [], device=self.device_id, now=NOW)

    def test_rejects_non_object_rules(self):
        bad = dict(self.body, rules=["not", "an", "object"])
        with self.assertRaises(gp.ProtocolError):
            gp.verify_policy(self.guardian.public_key(), bad, device=self.device_id, now=NOW)

    def test_version_one_policy_still_verifies(self):
        body = {"v": 1, "kind": "policy", "device": self.device_id, "counter": 3,
                "issued_at": NOW, "not_after": NOW + 3600, "rules": {"strict": True}}
        signature = self.guardian.sign(gp.canonical(body), gp.ec.ECDSA(gp.hashes.SHA256()))
        policy = {**body, "sig": gp.b64e(signature)}
        verified = gp.verify_policy(self.guardian.public_key(), policy,
                                    device=self.device_id, now=NOW)
        self.assertEqual(verified["rules"], {"strict": True})
        self.assertEqual(verified["v"], 1)


class CanonicalTests(unittest.TestCase):
    def test_key_order_does_not_change_the_bytes(self):
        self.assertEqual(gp.canonical({"b": 1, "a": True}), gp.canonical({"a": True, "b": 1}))
        self.assertIn(b'"a":true', gp.canonical({"a": True, "b": 1}))

    def test_rejects_nan(self):
        with self.assertRaises(gp.ProtocolError):
            gp.canonical({"value": float("nan")})

    def test_base64url_is_unpadded_and_round_trips(self):
        data = bytes(range(32))
        text = gp.b64e(data)
        self.assertNotIn("=", text)
        self.assertEqual(gp.b64d(text), data)
        with self.assertRaises(gp.ProtocolError):
            gp.b64d("not base64!")


if __name__ == "__main__":
    unittest.main()
