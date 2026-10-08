#!/usr/bin/env python3
"""Plan network-first setup from reported or observed capabilities.

This offline planner does not discover routers, configure them, collect secrets,
or verify enforcement. Missing facts stay unknown. A DNS setting, imported
blocklist, or self-reported capability is never a successful protection test.

    python3 network/setup.py
    python3 network/setup.py --capabilities /path/to/capabilities.json

Boolean capabilities accept true, false, or null (unknown). To attach an
observation, use {"value": true, "source": "observed", "evidence": "reference"}.
An observation describes a capability, not a passing protection test. Router
brands and ISP names are deliberately absent from this input contract.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


MAX_INPUT = 64 << 10
CAPABILITIES = (
    "network_consent", "router_admin", "dns_ipv4_configurable",
    "dns_ipv6_configurable", "ipv6_active", "local_resolver_available",
    "dns_ipv4_firewall", "dns_ipv6_firewall", "encrypted_dns_controls",
    "guardian_admin_control",
)
INPUT_FIELDS = set(CAPABILITIES) | {"network_kind", "api_integration"}


class InvalidCapabilities(ValueError):
    """The input cannot be represented without inventing capability evidence."""


def fields(value: object, allowed: set[str], label: str) -> dict:
    if not isinstance(value, dict):
        raise InvalidCapabilities(f"{label} must be an object")
    if any(not isinstance(key, str) or key not in allowed for key in value):
        raise InvalidCapabilities(f"{label} contains unsupported fields")
    return value


def reference(value: object, label: str) -> str:
    if (not isinstance(value, str) or not value.strip() or len(value) > 1000
            or any(ord(character) < 32 for character in value)):
        raise InvalidCapabilities(f"{label} must be a nonempty reference of at most 1000 characters")
    return value.strip()


def capability(value: object, label: str) -> dict:
    if value is None or type(value) is bool:
        return {"value": value, "source": "unknown" if value is None else "reported"}
    obj = fields(value, {"value", "source", "evidence"}, label)
    fact = obj.get("value")
    if type(fact) is not bool:
        raise InvalidCapabilities(f"{label}.value must be a boolean; use null for unknown")
    source = obj.get("source")
    if source not in ("reported", "observed"):
        raise InvalidCapabilities(f"{label}.source must be reported or observed")
    result = {"value": fact, "source": source}
    if source == "observed" or "evidence" in obj:
        result["evidence"] = reference(obj.get("evidence"), f"{label}.evidence")
    return result


def normalize_input(value: dict | None) -> dict:
    obj = fields({} if value is None else value, INPUT_FIELDS, "capabilities")
    kind = obj.get("network_kind", "unknown")
    if kind not in ("unknown", "private", "public"):
        raise InvalidCapabilities("network_kind must be unknown, private, or public")
    result = {"network_kind": kind}
    result.update({name: capability(obj.get(name), name) for name in CAPABILITIES})
    api = obj.get("api_integration")
    result["api_integration"] = None
    if api is not None:
        api = fields(api, {"identifier", "source", "evidence"}, "api_integration")
        if api.get("source") not in ("reported", "observed"):
            raise InvalidCapabilities("api_integration.source must be reported or observed")
        result["api_integration"] = {
            "identifier": reference(api.get("identifier"), "api_integration.identifier"),
            "source": api["source"],
            "evidence": reference(api.get("evidence"), "api_integration.evidence"),
        }
    return result


def plan_setup(capabilities: dict | None = None) -> dict:
    """Return a deterministic, reviewable plan; never return installed protection.

    Callers must store actual protection-test results separately, tied to the
    current network, policy version, and time. This API accepts no verified,
    enforced, enrollment, or adapter-availability overrides.
    """
    facts = normalize_input(capabilities)
    values = {name: facts[name]["value"] for name in CAPABILITIES}
    kind = facts["network_kind"]
    unknown = [name for name in CAPABILITIES if values[name] is None]
    if kind == "unknown":
        unknown.insert(0, "network_kind")
    api = facts["api_integration"]
    adapter = {
        "requested": api["identifier"] if api else None,
        "available": False,
        "status": "not_implemented" if api else "not_selected",
        "automatic_configuration_available": False,
    }
    ipv6_possible = values["ipv6_active"] is not False
    dns_possible = (values["dns_ipv4_configurable"] is True or
                    (ipv6_possible and values["dns_ipv6_configurable"] is True))
    firewall_possible = (values["dns_ipv4_firewall"] is True or
                         (ipv6_possible and values["dns_ipv6_firewall"] is True))
    reasons = []
    if kind == "public":
        reasons.append("This is a public/shared network; continue on devices without changing its gateway.")
    if values["router_admin"] is False:
        reasons.append("No authorized router administrator is available.")
    if values["network_consent"] is False:
        reasons.append("Network changes were not authorized.")
    access_known = (kind == "private" and values["router_admin"] is True and
                    values["network_consent"] is True)
    remaining_paths = ["dns_ipv4_configurable", "dns_ipv4_firewall"]
    if ipv6_possible:
        remaining_paths.extend(("dns_ipv6_configurable", "dns_ipv6_firewall"))
    unsupported = all(values[name] is False for name in remaining_paths)
    if reasons:
        route = "device_only"
    elif not access_known:
        route = "guided_assessment"
    elif dns_possible:
        route = "local_resolver" if values["local_resolver_available"] is True else "manual_dns"
    elif firewall_possible:
        route = "manual_firewall"
    elif unsupported:
        route = "device_only"
        reasons.append("The reported gateway capabilities cannot direct or enforce client DNS.")
    else:
        route = "guided_assessment"
    network_candidate = route in ("manual_dns", "local_resolver", "manual_firewall")

    steps: list[dict] = []

    def add(identifier: str, scope: str, action: str, evidence: list[str],
            requires: tuple[str, ...] = (), *, blocked: bool = False) -> None:
        steps.append({"order": len(steps) + 1, "id": identifier, "scope": scope,
                      "state": "blocked" if blocked else "todo", "action": action,
                      "requires": list(requires), "evidence_required": evidence})

    if route != "device_only":
        add("assess_network", "network",
            "With the network owner, inspect the installed gateway's documented settings. "
            "Record permission, administrator access, LAN DNS settings, IPv6/RA behavior, "
            "firewall controls, and resolver hosting. Leave unobserved facts unknown.",
            ["Owner authorization covering affected household users and a recovery contact.",
             "Current firmware/settings documentation or direct administrator observations; a brand name is insufficient."])
    if api:
        add("adapter_unavailable", "network",
            "No router API adapter is implemented for the requested integration. Use the "
            "capability-based manual plan, or continue device protection while assessing it.",
            ["A future adapter must identify supported firmware, preview changes, preserve existing settings, "
             "support recovery, and pass live enforcement tests before availability can be claimed."],
            blocked=True)
    if network_candidate:
        add("backup_recovery", "network",
            "Preview settings with the administrator, export a supported backup, and prepare "
            "an out-of-band recovery route before applying any network changes.",
            ["Administrator-reviewed configuration preview and tested recovery instructions."],
            ("assess_network",))
        if values["local_resolver_available"] is True:
            add("prepare_signed_resolver", "network",
                "On an administrator-controlled resolver host, publish the signed Hisn bundle "
                "using network/publish.py. If using AdGuard Home, import only the verified "
                "current/adguard.txt generation and refresh it using the installed resolver's supported interface.",
                ["Pinned key, accepted version/digests, administrator-controlled publication directory, "
                 "and resolver import/refresh log.",
                 "Resolver tests for an owned blocked host, its descendant, and a benign host; "
                 "publication alone does not prove client routing."],
                ("backup_recovery",))
            resolver_step = "prepare_signed_resolver"
        else:
            add("choose_filtering_resolver", "network",
                "Choose and test a filtering resolver with the administrator. Every configured "
                "primary and backup resolver must apply the selected policy. If custom Hisn "
                "lists are needed, separately assess a local resolver host and the signed publisher.",
                ["Named resolver/provider and policy, working filtering tests, and outage behavior "
                 "with no fallback to an unfiltered resolver."],
                ("backup_recovery",))
            resolver_step = "choose_filtering_resolver"
        for family in ("ipv4", "ipv6"):
            if family == "ipv6" and not ipv6_possible:
                continue
            if values[f"dns_{family}_configurable"] is True:
                add(f"configure_dns_{family}", "network",
                    f"Manually configure the gateway's {family.upper()} LAN DNS advertisement "
                    "and, where needed, upstream DNS to use the approved resolver. Renew client "
                    "leases/settings and account for every primary/secondary resolver.",
                    [f"Client resolver settings and query logs show the approved resolver on {family.upper()}.",
                     "Fresh owned blocked/benign destination checks after client settings renew."],
                    (resolver_step,))
            if values[f"dns_{family}_firewall"] is True:
                add(f"enforce_dns_{family}", "network",
                    f"Have the administrator preview and apply {family.upper()} gateway rules "
                    "that deny or deliberately redirect unauthorized TCP/UDP port 53, permitting "
                    "only the approved DNS path and necessary resolver upstream traffic.",
                    [f"Firewall counters and direct unauthorized TCP/UDP DNS tests on {family.upper()}.",
                     "Approved DNS works; resolver outage never selects an unfiltered fallback; reboot preserves policy."],
                    (resolver_step,))
        if values["encrypted_dns_controls"] is True:
            add("partial_encrypted_dns_controls", "network",
                "Review available controls for unauthorized encrypted DNS with the administrator. "
                "Port 853 and known DoH endpoint rules provide partial coverage; HTTPS on port "
                "443 can carry other DoH endpoints and tunnels. Assess endpoint restrictions separately.",
                ["Specific tested protocols/endpoints, gateway exceptions, and documented remaining HTTPS/tunnel paths."],
                (resolver_step,))
        add("verify_network", "network",
            "Record live tests on this network separately from this plan. Check each client/family, "
            "blocked and benign hosts, alternate DNS, encrypted DNS controls, resolver outage, "
            "reboot, and bypass limits before presenting any verified coverage.",
            ["Actual test records with network identity, firmware, policy version, timestamp, clients, "
             "IPv4/IPv6 paths, test outcomes, and failures.",
             "Enforcement requires independent bypass and outage tests; configured DNS or a saved checkbox is insufficient."],
            tuple(step["id"] for step in steps
                  if step["id"].startswith(("configure_dns_", "enforce_dns_")) or
                  step["id"] == "partial_encrypted_dns_controls"))
        add("separate_gateway_authority", "network",
            "Arrange administrator control with a trusted guardian and document recovery before "
            "claiming resistance to ordinary disabling. Router resets and replacement gateways remain limits.",
            ["Test that the daily protected user cannot edit DNS/firewall settings or obtain administrator credentials.",
             "Guardian recovery is reachable outside the protected device; physical-reset limits are disclosed."],
            ("backup_recovery",))

    add("protect_mac", "device",
        "Continue Mac setup now, including when router assessment is incomplete or unsupported. "
        "Enable the signed device filter and browser protection, test blocking, and arrange "
        "administrator separation with recovery. Managed non-removal requires separate real enrollment.",
        ["Live signed-filter blocking tests and health checks on the Mac, including provider interruption and restart.",
         "Tests on another Wi-Fi network; administrator separation or MDM enrollment must be independently verified."])
    add("protect_other_devices", "device",
        "Set up each phone and other device separately using its supported personal or managed "
        "protection path. Test home Wi-Fi, cellular, and another Wi-Fi network. Do not infer "
        "phone coverage or managed enrollment from installation on the Mac.",
        ["Per-device blocking, roaming, restart, removal, and failure-mode test records; actual "
         "management/enrollment and exceptions if using a managed path."])

    def layer(cap: str, applicable: bool = True) -> dict:
        if not applicable:
            state = "reported_inactive"
        elif values[cap] is False:
            state = "unavailable"
        elif values[cap] is None:
            state = "unknown"
        else:
            state = "candidate" if network_candidate else "not_actionable"
        return {"capability": state, "status": "not_verified"}

    limitations = [
        "The planner performs no discovery, configuration, import, or protection test; every layer remains unverified.",
        "Network DNS selects hosts, not individual posts, URLs, images, videos, local files, or established connections.",
        "DNS advertisement alone can be bypassed; IPv4 and IPv6 firewall enforcement require separate live tests.",
        "Known DoH endpoints/port rules cannot guarantee blocking every encrypted DNS service, VPN, proxy, or HTTPS tunnel.",
        "Home gateway policy does not follow a device onto cellular or another network.",
        "User consent does not grant router administrator access, device supervision, or MDM enrollment.",
        "Guardian-held administration can resist ordinary disabling, but physical reset, replacement equipment, "
        "device erasure, recovery access, and unmanaged devices remain threat-model limits.",
        "Recheck protection after a network, gateway, resolver, firmware, or policy change; a past result is not current evidence.",
    ]
    return {
        "schema": 1, "plan_only": True, "configuration_performed": False,
        "network_status": "not_verified", "route": route,
        "capabilities": facts, "missing_information": unknown,
        "fallback_reasons": reasons, "adapter": adapter,
        "layers": {
            "dns_ipv4": layer("dns_ipv4_configurable"),
            "dns_ipv6": layer("dns_ipv6_configurable", ipv6_possible),
            "dns_firewall_ipv4": layer("dns_ipv4_firewall"),
            "dns_firewall_ipv6": layer("dns_ipv6_firewall", ipv6_possible),
            "encrypted_dns": {**layer("encrypted_dns_controls"), "maximum_coverage": "partial"},
            "local_signed_rules": layer("local_resolver_available"),
            "guardian_authority": layer("guardian_admin_control"),
            "mac": {"status": "not_verified"},
            "other_devices": {"status": "not_verified"},
        },
        "steps": steps, "limitations": limitations,
    }


def parse_input(raw: str) -> dict:
    def unique(pairs: list[tuple[str, object]]) -> dict:
        obj = {}
        for key, value in pairs:
            if key in obj:
                raise InvalidCapabilities("capabilities contain a duplicate JSON key")
            obj[key] = value
        return obj

    try:
        parsed = json.loads(raw, object_pairs_hook=unique,
                            parse_constant=lambda _: (_ for _ in ()).throw(
                                InvalidCapabilities("capabilities contain a non-finite number")))
        if not isinstance(parsed, dict):
            raise InvalidCapabilities("capabilities JSON must be an object")
        return parsed
    except (ValueError, RecursionError) as exc:
        raise InvalidCapabilities("invalid capabilities JSON") from exc


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--capabilities", type=Path,
                        help="local capability JSON file; '-' reads stdin; omitted means unknown")
    args = parser.parse_args(argv)
    try:
        supplied = {}
        if args.capabilities is not None:
            if str(args.capabilities) == "-":
                raw = sys.stdin.read(MAX_INPUT + 1)
            else:
                with args.capabilities.open(encoding="utf-8") as source:
                    raw = source.read(MAX_INPUT + 1)
            if len(raw) > MAX_INPUT:
                raise InvalidCapabilities("capabilities input exceeds 65536 characters")
            supplied = parse_input(raw)
        result = plan_setup(supplied)
    except (OSError, UnicodeError, InvalidCapabilities) as exc:
        print(f"Network plan rejected: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
