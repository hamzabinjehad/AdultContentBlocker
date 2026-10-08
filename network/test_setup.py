"""Capability-routing and false-success regressions for the offline planner."""

from __future__ import annotations

import copy
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import setup as planner


PRIVATE = {"network_kind": "private", "network_consent": True, "router_admin": True}


class SetupPlannerTests(unittest.TestCase):
    def plan(self, **capabilities):
        return planner.plan_setup({**PRIVATE, **capabilities})

    def steps(self, plan):
        return {step["id"]: step for step in plan["steps"]}

    def test_unknown_router_gets_assessment_and_immediate_device_continuation(self):
        plan = planner.plan_setup()
        self.assertEqual("guided_assessment", plan["route"])
        steps = self.steps(plan)
        self.assertIn("assess_network", steps)
        self.assertIn("protect_mac", steps)
        self.assertEqual([], steps["protect_mac"]["requires"])
        self.assertIn("protect_other_devices", steps)
        self.assertIn("router_admin", plan["missing_information"])
        self.assertNotIn("configure_dns_ipv4", steps)
        self.assertEqual("unknown", plan["layers"]["dns_firewall_ipv6"]["capability"])

    def test_generic_dns_router_does_not_gain_firewall_or_custom_list_support(self):
        plan = self.plan(dns_ipv4_configurable=True, dns_ipv4_firewall=False,
                         local_resolver_available=False)
        self.assertEqual("manual_dns", plan["route"])
        steps = self.steps(plan)
        self.assertIn("configure_dns_ipv4", steps)
        self.assertNotIn("enforce_dns_ipv4", steps)
        self.assertNotIn("prepare_signed_resolver", steps)
        self.assertIn("choose_filtering_resolver", steps)
        self.assertEqual("unavailable", plan["layers"]["dns_firewall_ipv4"]["capability"])
        self.assertEqual("unknown", plan["layers"]["dns_ipv6"]["capability"])
        self.assertEqual("not_verified", plan["network_status"])

    def test_local_custom_rules_are_verified_before_dns_configuration(self):
        plan = self.plan(dns_ipv4_configurable=True, local_resolver_available=True)
        self.assertEqual("local_resolver", plan["route"])
        steps = self.steps(plan)
        self.assertIn("prepare_signed_resolver", steps["configure_dns_ipv4"]["requires"])
        publication = steps["prepare_signed_resolver"]
        self.assertIn("network/publish.py", publication["action"])
        self.assertTrue(any("client routing" in requirement for requirement in publication["evidence_required"]))
        self.assertEqual("not_verified", plan["layers"]["local_signed_rules"]["status"])

    def test_firewall_dns_enforcement_is_separate_per_family_and_doh_is_partial(self):
        plan = self.plan(dns_ipv4_configurable=True, dns_ipv6_configurable=True,
                         ipv6_active=True, dns_ipv4_firewall=True,
                         dns_ipv6_firewall=False, encrypted_dns_controls=True)
        steps = self.steps(plan)
        self.assertIn("enforce_dns_ipv4", steps)
        self.assertIn("configure_dns_ipv6", steps)
        self.assertNotIn("enforce_dns_ipv6", steps)
        self.assertIn("TCP/UDP", steps["enforce_dns_ipv4"]["action"])
        self.assertIn("partial", steps["partial_encrypted_dns_controls"]["action"])
        self.assertIn("partial_encrypted_dns_controls", steps["verify_network"]["requires"])
        self.assertEqual("partial", plan["layers"]["encrypted_dns"]["maximum_coverage"])
        self.assertEqual("unavailable", plan["layers"]["dns_firewall_ipv6"]["capability"])

    def test_explicitly_inactive_ipv6_has_no_configuration_but_is_not_verified(self):
        plan = self.plan(dns_ipv4_configurable=True, dns_ipv6_configurable=True,
                         dns_ipv6_firewall=True, ipv6_active=False)
        steps = self.steps(plan)
        self.assertNotIn("configure_dns_ipv6", steps)
        self.assertNotIn("enforce_dns_ipv6", steps)
        self.assertEqual("reported_inactive", plan["layers"]["dns_ipv6"]["capability"])
        self.assertEqual("not_verified", plan["layers"]["dns_ipv6"]["status"])

    def test_firewall_capability_can_offer_manual_dns_path_without_lan_dns_settings(self):
        plan = self.plan(dns_ipv4_configurable=False, dns_ipv6_configurable=False,
                         dns_ipv4_firewall=True, ipv6_active=False)
        self.assertEqual("manual_firewall", plan["route"])
        steps = self.steps(plan)
        self.assertIn("enforce_dns_ipv4", steps)
        self.assertNotIn("configure_dns_ipv4", steps)

    def test_verified_api_observation_cannot_create_an_unimplemented_adapter(self):
        plan = self.plan(dns_ipv4_configurable=True, api_integration={
            "identifier": "example-supported-api", "source": "observed",
            "evidence": "Firmware API capability assessment record",
        })
        self.assertEqual("manual_dns", plan["route"])
        self.assertFalse(plan["adapter"]["available"])
        self.assertFalse(plan["adapter"]["automatic_configuration_available"])
        self.assertEqual("not_implemented", plan["adapter"]["status"])
        self.assertEqual("blocked", self.steps(plan)["adapter_unavailable"]["state"])

    def test_api_observation_without_dns_capabilities_stays_unknown(self):
        plan = self.plan(api_integration={"identifier": "api", "source": "observed",
                                         "evidence": "API assessment"})
        self.assertEqual("guided_assessment", plan["route"])
        self.assertNotIn("configure_dns_ipv4", self.steps(plan))

    def test_public_locked_unconsented_or_unsupported_gateway_continues_devices(self):
        cases = [
            {"network_kind": "public", "dns_ipv4_configurable": True},
            {"router_admin": False, "dns_ipv4_configurable": True},
            {"network_consent": False, "dns_ipv4_configurable": True},
            {"dns_ipv4_configurable": False, "dns_ipv6_configurable": False,
             "dns_ipv4_firewall": False, "dns_ipv6_firewall": False},
        ]
        for capabilities in cases:
            with self.subTest(capabilities=capabilities):
                plan = self.plan(**capabilities)
                self.assertEqual("device_only", plan["route"])
                self.assertTrue(plan["fallback_reasons"])
                self.assertEqual({"protect_mac", "protect_other_devices"}, set(self.steps(plan)))

    def test_missing_access_or_permission_does_not_authorize_network_steps(self):
        for missing in ("network_kind", "network_consent", "router_admin"):
            with self.subTest(missing=missing):
                supplied = {**PRIVATE, "dns_ipv4_configurable": True}
                supplied.pop(missing)
                plan = planner.plan_setup(supplied)
                self.assertEqual("guided_assessment", plan["route"])
                self.assertNotIn("configure_dns_ipv4", self.steps(plan))

    def test_observed_capabilities_are_not_protection_test_evidence(self):
        plan = self.plan(**{name: {"value": True, "source": "observed",
                                 "evidence": "Settings inspection record"}
                           for name in planner.CAPABILITIES})
        self.assertTrue(plan["plan_only"])
        self.assertFalse(plan["configuration_performed"])
        self.assertEqual("not_verified", plan["network_status"])
        for name, layer in plan["layers"].items():
            with self.subTest(layer=name):
                self.assertEqual("not_verified", layer["status"])
        verification = self.steps(plan)["verify_network"]
        self.assertTrue(any("timestamp" in item for item in verification["evidence_required"]))

    def test_fake_verification_and_inferred_brand_inputs_are_rejected(self):
        for key in ("verified", "enforced", "status", "adapter_available", "mdm_enrolled",
                    "router_brand", "router_model", "isp", "password"):
            with self.subTest(key=key):
                with self.assertRaises(planner.InvalidCapabilities):
                    self.plan(**{key: True})
        for field in ("verified", "enforced", "available"):
            with self.subTest(api_field=field):
                with self.assertRaises(planner.InvalidCapabilities):
                    self.plan(api_integration={"identifier": "api", "source": "observed",
                                               "evidence": "record", field: True})

    def test_invalid_boolean_and_observation_values_are_rejected(self):
        for value in (1, 0, "true", "yes", [], {"value": True},
                      {"value": True, "source": "observed"},
                      {"value": None, "source": "observed", "evidence": "record"},
                      {"value": True, "source": "verified", "evidence": "record"},
                      {"value": True, "source": "observed", "evidence": "line\nbreak"}):
            with self.subTest(value=value):
                with self.assertRaises(planner.InvalidCapabilities):
                    self.plan(router_admin=value)
        for value in ([], "unknown", True):
            with self.subTest(root=value):
                with self.assertRaises(planner.InvalidCapabilities):
                    planner.plan_setup(value)

    def test_reported_capability_references_are_optional_and_preserved(self):
        plan = self.plan(dns_ipv4_configurable={"value": True, "source": "reported",
                                               "evidence": "  administrator description  "})
        self.assertEqual("administrator description",
                         plan["capabilities"]["dns_ipv4_configurable"]["evidence"])
        self.assertEqual("manual_dns", plan["route"])

    def test_authority_reset_and_phone_limits_remain_visible_in_every_route(self):
        for supplied in ({}, {**PRIVATE, "dns_ipv4_configurable": True},
                         {**PRIVATE, "router_admin": False}):
            with self.subTest(supplied=supplied):
                plan = planner.plan_setup(supplied)
                limits = " ".join(plan["limitations"])
                self.assertIn("physical reset", limits)
                self.assertIn("MDM enrollment", limits)
                self.assertIn("cellular", limits)
                phone = self.steps(plan)["protect_other_devices"]
                self.assertIn("cellular", phone["action"])
                self.assertIn("removal", " ".join(phone["evidence_required"]))

    def test_plan_is_deterministic_does_not_mutate_input_and_orders_dependencies(self):
        supplied = {**PRIVATE, "dns_ipv4_configurable": True, "dns_ipv4_firewall": True,
                    "local_resolver_available": True}
        original = copy.deepcopy(supplied)
        first = planner.plan_setup(supplied)
        self.assertEqual(first, planner.plan_setup(supplied))
        self.assertEqual(original, supplied)
        previous = set()
        for index, step in enumerate(first["steps"], 1):
            self.assertEqual(index, step["order"])
            self.assertTrue(set(step["requires"]).issubset(previous))
            self.assertTrue(step["evidence_required"])
            self.assertNotIn(step["id"], previous)
            previous.add(step["id"])

    def cli(self, *args, stdin=None):
        return subprocess.run([sys.executable, str(Path(planner.__file__)), *args],
                              input=stdin, capture_output=True, text=True, check=False)

    def test_cli_unknown_and_stdin_plans_are_json_without_network_access(self):
        result = self.cli()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("guided_assessment", json.loads(result.stdout)["route"])
        result = self.cli("--capabilities", "-", stdin=json.dumps({**PRIVATE, "dns_ipv4_configurable": True}))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("manual_dns", json.loads(result.stdout)["route"])

    def test_cli_rejects_invalid_duplicate_and_oversized_json_without_partial_plan(self):
        for raw in ("{", "[]", "null", '{"router_admin": true, "router_admin": false}',
                    '{"router_admin": NaN}', " " * (planner.MAX_INPUT + 1),
                    '{"password": "secret-must-not-be-printed"}'):
            with self.subTest(raw=raw[:80]):
                result = self.cli("--capabilities", "-", stdin=raw)
                self.assertEqual(1, result.returncode)
                self.assertEqual("", result.stdout)
                self.assertIn("Network plan rejected:", result.stderr)
                self.assertNotIn("secret-must-not-be-printed", result.stderr)

    def test_cli_reads_local_file_and_reports_missing_file(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "capabilities.json"
            path.write_text(json.dumps({**PRIVATE, "dns_ipv4_configurable": True}), encoding="utf-8")
            result = self.cli("--capabilities", str(path))
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual("manual_dns", json.loads(result.stdout)["route"])
            path.unlink()
            result = self.cli("--capabilities", str(path))
            self.assertEqual(1, result.returncode)
            self.assertEqual("", result.stdout)


if __name__ == "__main__":
    unittest.main()
