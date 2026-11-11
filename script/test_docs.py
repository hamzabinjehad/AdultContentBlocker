"""The documentation index is complete and the top-level links resolve.

The index says "every document in this folder"; without a check, a new release
record or plan could be committed and never linked. The same test follows the
local links in the root README, the docs index and the security policy, so a
missing file is caught before a reader sees a dead end.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
INDEX = DOCS / "README.md"
LINKED_DOCUMENTS = (ROOT / "README.md", INDEX, ROOT / "SECURITY.md")


def local_links(text):
    """Targets of markdown links and images, without anchors or queries."""
    for match in re.finditer(r"!?\[[^\]]*\]\(([^)]+)\)", text):
        target = match.group(1).strip()
        if target.startswith(("http://", "https://", "mailto:", "#")):
            continue
        target = target.split("#", 1)[0].split("?", 1)[0]
        if target:
            yield target


class DocumentationTests(unittest.TestCase):
    def test_every_document_is_indexed(self):
        index = INDEX.read_text(encoding="utf-8")
        missing = sorted(
            path.name
            for path in DOCS.glob("*.md")
            if path.name != INDEX.name and path.name not in index
        )
        self.assertEqual(missing, [], "documents missing from docs/README.md")

    def test_top_level_links_resolve(self):
        broken = []
        for page in LINKED_DOCUMENTS:
            for target in local_links(page.read_text(encoding="utf-8")):
                resolved = (page.parent / target).resolve()
                if not resolved.exists():
                    broken.append(f"{page.relative_to(ROOT)} -> {target}")
        self.assertEqual(broken, [], "local links that do not resolve")

    def test_index_names_a_link_not_a_bare_path(self):
        """A name in prose is not an index entry; it must be a markdown link."""
        index = INDEX.read_text(encoding="utf-8")
        for path in DOCS.glob("*.md"):
            if path.name == INDEX.name:
                continue
            pattern = r"\]\([^)]*" + re.escape(path.name) + r"(?:#[^)]*)?\)"
            self.assertRegex(index, pattern, f"{path.name} is not linked")

    def test_security_policy_names_a_private_route(self):
        policy = (ROOT / "SECURITY.md").read_text(encoding="utf-8")
        self.assertIn("GitHub Security Advisories", policy)
        self.assertIn("Do not", policy)


if __name__ == "__main__":
    unittest.main()
