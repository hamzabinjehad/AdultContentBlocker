#!/usr/bin/env python3
"""Reference implementation of Hisn's phone-to-laptop guardian protocol.

This is the small, testable core of the Guardian Console: a pairing exchange
that produces a six-digit short authentication string, and a signed policy
envelope the phone can verify offline. It deliberately has no server, no
account and no browsing data.

    python3 script/guardian_protocol.py --check

The module is also importable:

    import guardian_protocol as gp
    guardian = gp.generate_key()
    device = gp.generate_key()
    offer = gp.build_offer(guardian.public_key())
    response = gp.build_response(device, offer)
    device_key, sas = gp.verify_response(offer, response, guardian)

Wire formats:

  hisn://pair/1?gk=<base64url P-256 public key>&nonce=<base64url 16 bytes>&label=<text>

  PolicyEnvelope = {
      "v": 1, "kind": "policy", "device": <device id>,
      "counter": <int>, "issued_at": <epoch seconds>,
      "not_after": <epoch seconds>, "rules": {...},
      "sig": <base64url P-256 ECDSA signature over canonical body>
  }

The signature covers every field except `sig`, serialized as UTF-8 JSON with
sorted keys and no whitespace. The phone persists the highest counter it has
accepted; a policy with a lower or equal counter is a replay and is refused.
The private keys never leave the Secure Enclave/Keychain/Keystore in a real
client; this Python module exists for tests, protocol review and the laptop
reference console.
"""
from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
import json
import os
import sys
import time
from dataclasses import dataclass
from urllib.parse import parse_qsl, urlencode, urlsplit

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

VERSION = 1
SCHEME = "hisn"
PAIR_HOST = "pair"
DEVICE_ID_BYTES = 32
NONCE_BYTES = 16
MAX_LABEL = 64
MAX_CLOCK_SKEW = 300
POLICY_FIELDS = {"v", "kind", "device", "counter", "issued_at", "not_after", "rules", "sig"}
POLICY_V2_FIELDS = POLICY_FIELDS


class ProtocolError(ValueError):
    """A malformed, replayed or untrusted pairing or policy message."""


def b64e(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")


def b64d(text: str) -> bytes:
    if not isinstance(text, str) or not text:
        raise ProtocolError("empty base64url value")
    padding = "=" * (-len(text) % 4)
    try:
        return base64.b64decode(text + padding, altchars=b"-_", validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ProtocolError("invalid base64url value") from exc


def canonical(value: object) -> bytes:
    """The only serialization the protocol signs or hashes."""
    try:
        return json.dumps(value, sort_keys=True, separators=(",", ":"),
                          ensure_ascii=False, allow_nan=False).encode("utf-8")
    except (TypeError, ValueError) as exc:
        raise ProtocolError("value is not canonical JSON") from exc


def generate_key() -> ec.EllipticCurvePrivateKey:
    return ec.generate_private_key(ec.SECP256R1())


def _p256_public(key) -> ec.EllipticCurvePublicKey:
    if not isinstance(key, ec.EllipticCurvePublicKey) or not isinstance(key.curve, ec.SECP256R1):
        raise ProtocolError("not a P-256 public key")
    return key


def _p256_private(key) -> ec.EllipticCurvePrivateKey:
    if not isinstance(key, ec.EllipticCurvePrivateKey) or not isinstance(key.curve, ec.SECP256R1):
        raise ProtocolError("not a P-256 private key")
    return key


def public_der(key) -> bytes:
    return _p256_public(key).public_bytes(serialization.Encoding.DER,
                                          serialization.PublicFormat.SubjectPublicKeyInfo)


def public_b64(key) -> str:
    return b64e(public_der(key))


def load_public_b64(text: str) -> ec.EllipticCurvePublicKey:
    data = b64d(text)
    try:
        key = serialization.load_der_public_key(data)
    except (ValueError, TypeError) as exc:
        raise ProtocolError("public key is not DER") from exc
    return _p256_public(key)


def load_public_pem(data: bytes) -> ec.EllipticCurvePublicKey:
    try:
        key = serialization.load_pem_public_key(data)
    except (ValueError, TypeError) as exc:
        raise ProtocolError("public key is not readable PEM") from exc
    return _p256_public(key)


def private_pem(key, password: bytes | None = None) -> bytes:
    _p256_private(key)
    encryption = (serialization.BestAvailableEncryption(password) if password
                  else serialization.NoEncryption())
    return key.private_bytes(serialization.Encoding.PEM,
                             serialization.PrivateFormat.PKCS8, encryption)


def load_private_pem(data: bytes, password: bytes | None = None) -> ec.EllipticCurvePrivateKey:
    try:
        key = serialization.load_pem_private_key(data, password=password)
    except (ValueError, TypeError) as exc:
        raise ProtocolError("private key is not readable PEM") from exc
    return _p256_private(key)


def device_id(public_key) -> str:
    """A stable, non-secret identifier derived from the device public key."""
    return b64e(hashlib.sha256(public_der(public_key)).digest())


@dataclass(frozen=True)
class Offer:
    guardian: str
    nonce: str
    label: str
    uri: str


def build_offer(guardian_public, *, nonce: bytes | None = None, label: str = "Guardian") -> Offer:
    guardian = public_b64(guardian_public)
    raw_nonce = os.urandom(NONCE_BYTES) if nonce is None else nonce
    if not isinstance(raw_nonce, (bytes, bytearray)) or len(raw_nonce) != NONCE_BYTES:
        raise ProtocolError(f"nonce must be {NONCE_BYTES} bytes")
    if not isinstance(label, str) or len(label) > MAX_LABEL:
        raise ProtocolError(f"label must be at most {MAX_LABEL} characters")
    if any(ord(ch) < 0x20 or ord(ch) == 0x7f for ch in label):
        raise ProtocolError("label contains a control character")
    encoded_nonce = b64e(bytes(raw_nonce))
    query = urlencode({"gk": guardian, "nonce": encoded_nonce, "label": label})
    uri = f"{SCHEME}://{PAIR_HOST}/{VERSION}?{query}"
    return Offer(guardian, encoded_nonce, label, uri)


def parse_offer(uri: str) -> Offer:
    if not isinstance(uri, str):
        raise ProtocolError("offer is not text")
    parts = urlsplit(uri)
    try:
        port = parts.port
    except ValueError as exc:
        raise ProtocolError("offer has a malformed authority") from exc
    if parts.scheme != SCHEME or parts.netloc != PAIR_HOST or parts.path != f"/{VERSION}":
        raise ProtocolError("not a Hisn pairing offer")
    if parts.fragment or parts.username or parts.password or port is not None:
        raise ProtocolError("offer has an unexpected authority or fragment")
    try:
        pairs = parse_qsl(parts.query, keep_blank_values=True, strict_parsing=True)
    except ValueError as exc:
        raise ProtocolError("offer query is malformed") from exc
    keys = [key for key, _ in pairs]
    if len(set(keys)) != len(keys):
        raise ProtocolError("offer repeats a parameter")
    values = dict(pairs)
    if set(values) - {"gk", "nonce", "label"}:
        raise ProtocolError("offer has an unknown parameter")
    if "gk" not in values or "nonce" not in values:
        raise ProtocolError("offer is missing a key or nonce")
    guardian = load_public_b64(values["gk"])
    nonce = b64d(values["nonce"])
    if len(nonce) != NONCE_BYTES:
        raise ProtocolError("offer nonce is the wrong length")
    label = values.get("label", "")
    if len(label) > MAX_LABEL or any(ord(ch) < 0x20 or ord(ch) == 0x7f for ch in label):
        raise ProtocolError("offer label is invalid")
    return Offer(public_b64(guardian), values["nonce"], label, uri)


def _shared(private_key, peer_public) -> bytes:
    return _p256_private(private_key).exchange(ec.ECDH(), _p256_public(peer_public))


def _sas(private_key, peer_public, guardian_b64: str, device_b64: str, nonce: str) -> str:
    info = canonical({"v": VERSION, "guardian": guardian_b64, "device": device_b64, "nonce": nonce})
    material = HKDF(algorithm=hashes.SHA256(), length=32, salt=b"hisn-pair-v1",
                    info=info).derive(_shared(private_key, peer_public))
    value = int.from_bytes(material, "big") % 1_000_000
    return f"{value:06d}"


def response_body(offer: Offer, device_public) -> dict:
    return {"v": VERSION, "kind": "pair-response", "nonce": offer.nonce,
            "guardian": offer.guardian, "device": public_b64(device_public)}


def build_response(device_private, offer: Offer) -> dict:
    device_public = _p256_private(device_private).public_key()
    body = response_body(offer, device_public)
    proof = _p256_private(device_private).sign(canonical(body), ec.ECDSA(hashes.SHA256()))
    return {**body, "proof": b64e(proof)}


def verify_response(offer: Offer, response: dict, guardian_private) -> tuple[ec.EllipticCurvePublicKey, str]:
    """Verify the device's proof and return its key and the shared six-digit SAS."""
    if not isinstance(response, dict):
        raise ProtocolError("response is not an object")
    expected = {"v", "kind", "nonce", "guardian", "device", "proof"}
    if set(response) != expected:
        raise ProtocolError("response has missing or unknown fields")
    if not isinstance(response["v"], int) or isinstance(response["v"], bool) \
            or response["v"] != VERSION or response["kind"] != "pair-response":
        raise ProtocolError("wrong response version or kind")
    if response["nonce"] != offer.nonce:
        raise ProtocolError("response nonce does not match the offer")
    if response["guardian"] != offer.guardian:
        raise ProtocolError("response names a different guardian")
    device = load_public_b64(response["device"])
    body = response_body(offer, device)
    try:
        _p256_public(device).verify(b64d(response["proof"]), canonical(body),
                                    ec.ECDSA(hashes.SHA256()))
    except InvalidSignature as exc:
        raise ProtocolError("response proof is invalid") from exc
    sas = _sas(guardian_private, device, offer.guardian, response["device"], offer.nonce)
    return device, sas


def device_sas(device_private, offer: Offer) -> str:
    """The device's side of the SAS; it must equal the guardian's value."""
    guardian = load_public_b64(offer.guardian)
    device_public = _p256_private(device_private).public_key()
    return _sas(device_private, guardian, offer.guardian, public_b64(device_public), offer.nonce)


def _int_field(message: dict, name: str) -> int:
    value = message.get(name)
    if not isinstance(value, int) or isinstance(value, bool):
        raise ProtocolError(f"{name} must be an integer")
    return value


def domain_list_sha256(domains) -> str:
    """SHA-256 of the canonical domain list: sorted, newline-joined UTF-8."""
    canonical = "\n".join(sorted(str(domain) for domain in domains))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def sign_policy(guardian_private, *, device: str, counter: int, issued_at: int,
                not_after: int, rules: dict) -> dict:
    """Sign a version-2 policy envelope.

    The rules object is canonical JSON inside a base64url string, so the phone
    can verify the flat envelope signature without parsing a nested rules tree.
    """
    if not isinstance(device, str) or not device:
        raise ProtocolError("device id is required")
    if not isinstance(counter, int) or isinstance(counter, bool) or counter < 1:
        raise ProtocolError("counter must be a positive integer")
    if not isinstance(issued_at, int) or isinstance(issued_at, bool):
        raise ProtocolError("issued_at must be an integer")
    if not isinstance(not_after, int) or isinstance(not_after, bool):
        raise ProtocolError("not_after must be an integer")
    if not_after <= issued_at:
        raise ProtocolError("not_after must be later than issued_at")
    if not isinstance(rules, dict):
        raise ProtocolError("rules must be an object")
    body = {"v": 2, "kind": "policy", "device": device, "counter": counter,
            "issued_at": issued_at, "not_after": not_after,
            "rules": b64e(canonical(rules))}
    signature = _p256_private(guardian_private).sign(canonical(body), ec.ECDSA(hashes.SHA256()))
    return {**body, "sig": b64e(signature)}


def _policy_time_checks(policy: dict, *, device: str, now: int, last_counter: int,
                        max_skew: int) -> tuple[int, int, int]:
    if not isinstance(policy["v"], int) or isinstance(policy["v"], bool) \
            or policy["kind"] != "policy":
        raise ProtocolError("wrong policy version or kind")
    if not isinstance(policy["device"], str) or policy["device"] != device:
        raise ProtocolError("policy is for a different device")
    counter = _int_field(policy, "counter")
    issued_at = _int_field(policy, "issued_at")
    not_after = _int_field(policy, "not_after")
    if counter <= last_counter:
        raise ProtocolError("policy replay: counter is not newer")
    if not_after <= issued_at:
        raise ProtocolError("policy expires before it was issued")
    if issued_at > now + max_skew:
        raise ProtocolError("policy is issued in the future")
    if not_after <= now:
        raise ProtocolError("policy has expired")
    return counter, issued_at, not_after


def _verify_policy_v1(guardian_public, policy: dict, *, device: str, now: int,
                      last_counter: int, max_skew: int) -> dict:
    if set(policy) != POLICY_FIELDS or policy["v"] != 1:
        raise ProtocolError("policy has missing or unknown fields")
    _policy_time_checks(policy, device=device, now=now, last_counter=last_counter,
                        max_skew=max_skew)
    if not isinstance(policy["rules"], dict):
        raise ProtocolError("policy rules are not an object")
    body = {key: policy[key] for key in POLICY_FIELDS if key != "sig"}
    try:
        _p256_public(guardian_public).verify(b64d(policy["sig"]), canonical(body),
                                             ec.ECDSA(hashes.SHA256()))
    except InvalidSignature as exc:
        raise ProtocolError("policy signature is invalid") from exc
    return body


def verify_policy(guardian_public, policy: dict, *, device: str, now: int,
                  last_counter: int = 0, max_skew: int = MAX_CLOCK_SKEW) -> dict:
    """Verify a version-1 or version-2 policy offline.

    Version 2 returns the rules as the parsed object. Version 1 is kept so old
    reference fixtures still verify; new policies are signed as version 2.
    """
    if not isinstance(policy, dict):
        raise ProtocolError("policy is not an object")
    version = policy.get("v")
    if version == 1 and not isinstance(version, bool):
        return _verify_policy_v1(guardian_public, policy, device=device, now=now,
                                 last_counter=last_counter, max_skew=max_skew)
    if isinstance(version, bool) or version != 2 or set(policy) != POLICY_V2_FIELDS:
        raise ProtocolError("policy has missing or unknown fields")
    _policy_time_checks(policy, device=device, now=now, last_counter=last_counter,
                        max_skew=max_skew)
    if not isinstance(policy["rules"], str):
        raise ProtocolError("policy rules are not a base64 string")
    try:
        parsed_rules = json.loads(b64d(policy["rules"]).decode("utf-8"))
    except (ProtocolError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ProtocolError("policy rules are not valid JSON") from exc
    if not isinstance(parsed_rules, dict):
        raise ProtocolError("policy rules are not an object")
    body = {key: policy[key] for key in POLICY_V2_FIELDS if key != "sig"}
    try:
        _p256_public(guardian_public).verify(b64d(policy["sig"]), canonical(body),
                                             ec.ECDSA(hashes.SHA256()))
    except InvalidSignature as exc:
        raise ProtocolError("policy signature is invalid") from exc
    return {**body, "rules": parsed_rules}


def self_check() -> None:
    """A protocol round trip, exercised by `--check` and the test suite."""
    guardian = generate_key()
    device = generate_key()
    offer = build_offer(guardian.public_key(), nonce=b"0123456789abcdef", label="Test guardian")
    parsed = parse_offer(offer.uri)
    if parsed != offer:
        raise ProtocolError("offer did not round-trip")
    response = build_response(device, parsed)
    device_public, guardian_sas = verify_response(parsed, response, guardian)
    if device_public.public_numbers() != device.public_key().public_numbers():
        raise ProtocolError("device key changed in pairing")
    if guardian_sas != device_sas(device, parsed):
        raise ProtocolError("the two devices derived different SAS values")
    now = int(time.time())
    policy = sign_policy(guardian, device=device_id(device.public_key()), counter=1,
                         issued_at=now, not_after=now + 86400, rules={"strict": True})
    body = verify_policy(guardian.public_key(), policy, device=device_id(device.public_key()),
                         now=now, last_counter=0)
    if body["rules"] != {"strict": True}:
        raise ProtocolError("policy rules changed in verification")
    print("guardian protocol self-check passed: pairing SAS and signed policy round trip")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="run the protocol self-check")
    args = parser.parse_args(argv)
    if not args.check:
        parser.print_help()
        return 2
    try:
        self_check()
    except ProtocolError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
