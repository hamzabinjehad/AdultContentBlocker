import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "android/core/src/main/java/app/hisn/core/EncryptedDnsProviders.java"
SWIFT = ROOT / "ios/Shared/EncryptedDNSProviders.swift"
ENGINE = ROOT / "ios/Shared/DNSFilterEngine.swift"
NAME = re.compile(r'"([a-z0-9][a-z0-9.\-]*)"')


def names(path):
    return set(NAME.findall(path.read_text(encoding="utf-8")))


class EncryptedDnsParityTests(unittest.TestCase):
    def test_provider_lists_are_identical(self):
        """A provider added on one platform must not stay open on the other."""
        java, swift = names(JAVA), names(SWIFT)
        self.assertEqual(java, swift, f"Android-only: {sorted(java - swift)}; "
                                      f"iOS-only: {sorted(swift - java)}")
        self.assertGreaterEqual(len(java), 35)
        self.assertIn("use-application-dns.net", java)
        self.assertIn("resolver.arpa", java)
        self.assertIn("dns.google", java)

    def test_ios_checks_providers_before_a_custom_allowance(self):
        """A saved allowance must not turn a DoH endpoint into the bypass route."""
        engine = ENGINE.read_text(encoding="utf-8")
        decide = engine.split("func decide(", 1)[1].split("let domains:", 1)[0]
        provider = decide.index("EncryptedDNSProviders.blocks(host)")
        custom = decide.index("CustomSitePolicy.covers(customSites, host)")
        self.assertLess(provider, custom)
        self.assertIn("case encryptedDns", engine)

    def test_android_checks_providers_before_the_saved_rules(self):
        service = (ROOT / "android/app/src/main/java/app/hisn/android/DnsVpnService.java").read_text()
        rules = service.split("DnsPolicy.Rules rules =", 1)[1].split("DnsPolicy policy =", 1)[0]
        self.assertLess(rules.index("EncryptedDnsProviders.blocks"), rules.index("browserTest.evaluate"))


if __name__ == "__main__":
    unittest.main()
