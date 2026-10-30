"""The bundled Apple notices must cover every enabled list source and be current."""
import unittest

import notices


class NoticeTests(unittest.TestCase):
    def test_generated_files_are_current(self):
        self.assertEqual(notices.IOS.read_text(), notices.document("iOS"))
        self.assertEqual(notices.MAC.read_text(), notices.document("macOS"))

    def test_every_enabled_source_is_named_with_licence_and_url(self):
        for name, path in (("iOS", notices.IOS), ("macOS", notices.MAC)):
            text = path.read_text()
            for source in notices.sources():
                self.assertIn(source["id"], text, f"{name}: {source['id']} missing")
                self.assertIn(source["license"], text, f"{name}: licence missing")
                if source["url"]:
                    self.assertIn(source["url"], text, f"{name}: upstream URL missing")

    def test_ios_carries_the_visual_runtime_notices(self):
        text = notices.IOS.read_text()
        for path in sorted((notices.VISUAL / "notices").glob("*")):
            self.assertIn(path.name, text)
        self.assertIn("engine bundle legal notice", text)
        self.assertNotIn("engine bundle legal notice", notices.MAC.read_text(),
                         "the Mac app does not bundle the visual runtime")

    def test_core_licence_texts_are_reproduced(self):
        text = notices.IOS.read_text()
        for _, _, path in notices.TEXTS:
            first = path.read_text().strip().splitlines()[0]
            self.assertIn(first, text, f"{path.name}: first line missing")


if __name__ == "__main__":
    unittest.main()
