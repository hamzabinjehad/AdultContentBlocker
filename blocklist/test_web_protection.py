import json
import re
import unittest
import web_protection as policy


class WebProtectionTests(unittest.TestCase):
    def test_generated_files_are_current(self):
        for name, content in policy.outputs().items():
            with self.subTest(name=name):
                self.assertEqual((policy.ROOT / name).read_text(), content)

    def test_search_matching_is_exact_not_entire_yandex_ecosystem(self):
        rules = json.loads(policy.outputs()["extension/rules/web_protection.json"])
        patterns = [re.compile(r["condition"]["regexFilter"], re.I)
                    for r in rules if "regexFilter" in r["condition"]]
        for host in policy.read_policy()["blockedSearchHosts"]:
            for url in (f"https://{host}/images", f"http://{host}:80/", f"https://{host.upper()}./search?q=test"):
                self.assertTrue(any(p.search(url) for p in patterns), url)
        for host in ("mail.yandex.ru", "maps.yandex.com", "yandex.com.example.org", "notyandex.com"):
            self.assertFalse(any(p.search(f"https://{host}/") for p in patterns), host)

    def test_every_block_covers_documents_and_embedded_traffic(self):
        rules = json.loads(policy.outputs()["extension/rules/web_protection.json"])
        self.assertEqual(len({r["id"] for r in rules}), len(rules))
        for document, embedded in zip(rules[::2], rules[1::2]):
            self.assertEqual(document["action"]["type"], "redirect")
            self.assertEqual(embedded["action"]["type"], "block")
            self.assertIn("image", embedded["condition"]["resourceTypes"])
            self.assertIn("xmlhttprequest", embedded["condition"]["resourceTypes"])
            self.assertGreater(document["priority"], 1000 + 2 * 32 + 1)


if __name__ == "__main__":
    unittest.main()
