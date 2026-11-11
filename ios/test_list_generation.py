import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GENERATION = ROOT / "ios/Shared/ListGeneration.swift"
UPDATER = ROOT / "ios/Hisn/ListUpdater.swift"
ANDROID = ROOT / "android/app/src/main/java/app/hisn/android/AdultListUpdater.java"
PUBLIC_KEY = ROOT / "blocklist/public_key.hex"


class ListGenerationParityTests(unittest.TestCase):
    def test_pinned_key_matches_the_published_public_key(self):
        swift = GENERATION.read_text(encoding="utf-8")
        match = re.search(r'pinnedPublicKeyHex\s*=\s*\n?\s*"([0-9a-f]{64})"', swift)
        self.assertIsNotNone(match, "pinned key not found in ListGeneration.swift")
        self.assertEqual(match.group(1), PUBLIC_KEY.read_text(encoding="utf-8").strip())

    def test_update_base_matches_android(self):
        swift = UPDATER.read_text(encoding="utf-8")
        self.assertIn("raw.githubusercontent.com/hamzabinjehad/AdultContentBlocker/lists", swift)
        java = ANDROID.read_text(encoding="utf-8")
        self.assertIn("raw.githubusercontent.com/hamzabinjehad/AdultContentBlocker/lists", java)

    def test_the_store_lives_in_the_dns_app_group(self):
        store = (ROOT / "ios/Shared/ListGenerationStore.swift").read_text(encoding="utf-8")
        self.assertIn("DNSFilterAssets.group", store)
        self.assertIn("list-generations", store)


if __name__ == "__main__":
    unittest.main()
