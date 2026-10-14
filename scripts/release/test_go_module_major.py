#!/usr/bin/env python3
"""Unit tests for go_module_major.py: python3 scripts/release/test_go_module_major.py"""
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import go_module_major as gmm  # noqa: E402

# Built from the tool's own constants: a literal module path here would be
# rewritten by --set, breaking these tests in the release commit.
CLIENT = gmm.REPO_PREFIX + gmm.MODULE_DIRS[0]
TC = gmm.REPO_PREFIX + gmm.MODULE_DIRS[1]


def rewrite(text, major=10):
    return gmm.rewrite(text, major)


def stale(text, major=10):
    return [ref for ref, _ in gmm.stale_references(text, major)]


class RewriteTest(unittest.TestCase):
    def test_suffixed_and_bare_module_paths_move_to_the_new_major(self):
        self.assertEqual(rewrite(f'import "{CLIENT}/v9"'), f'import "{CLIENT}/v10"')
        self.assertEqual(rewrite(f'go get {TC}'), f'go get {TC}/v10')
        self.assertEqual(rewrite(f"badge/{CLIENT}/v9.svg)"), f"badge/{CLIENT}/v10.svg)")

    def test_go_mod_require_version_follows_the_suffix(self):
        self.assertEqual(rewrite(f"require {CLIENT}/v9 v9.0.0\n"), f"require {CLIENT}/v10 v10.0.0\n")
        self.assertEqual(rewrite(f"\t{CLIENT}/v9\tv9.0.0\n"), f"\t{CLIENT}/v10\tv10.0.0\n")

    def test_query_version_follows_the_suffix(self):
        self.assertEqual(rewrite(f"go list -m {TC}/v9@v9.0.0"), f"go list -m {TC}/v10@v10.0.0")
        self.assertEqual(rewrite(f"{TC}/v9@v9.0.0."), f"{TC}/v10@v10.0.0.")

    def test_a_version_with_the_right_major_is_kept(self):
        text = f"{CLIENT}/v10@v10.2.3 and require {CLIENT}/v10 v10.4.0"
        self.assertEqual(rewrite(text), text)

    def test_a_version_on_the_next_line_is_not_taken_as_a_require_version(self):
        text = f"see {CLIENT}/v9\nv9.0.0 was the tag\n"
        self.assertEqual(rewrite(text), f"see {CLIENT}/v10\nv9.0.0 was the tag\n")
        self.assertEqual(stale(f"see {CLIENT}/v10\nv9.0.0 was the tag\n"), [])

    def test_paths_inside_a_module_directory_are_not_module_paths(self):
        for text in (f"{CLIENT}/README.md", f"{TC}/go.mod", f"{TC}/README.md",
                     f"{CLIENT}/v9/README.md", f"{TC}/v9/doc.go", f"{CLIENT}/vendor/x",
                     f"{CLIENT}-extra", f"{TC}/v9.0.0", f"{CLIENT}/v${{MAJOR}}@v${{VERSION}}"):
            with self.subTest(text=text):
                self.assertEqual(rewrite(text), text)
                self.assertEqual(stale(text), [])

    def test_subpackage_paths_move_to_the_new_major(self):
        self.assertEqual(rewrite(f'import "{CLIENT}/v9/internal/wire"'), f'import "{CLIENT}/v10/internal/wire"')
        self.assertEqual(rewrite(f"go test {TC}/v9/..."), f"go test {TC}/v10/...")
        self.assertEqual(stale(f'"{CLIENT}/v9/internal"'), [f"{CLIENT}/v9"])
        self.assertEqual(stale(f"go test {TC}/v9/..."), [f"{TC}/v9"])

    def test_set_is_idempotent(self):
        once = rewrite(f"require {CLIENT}/v7 v7.0.0\nimport \"{TC}\"\n")
        self.assertEqual(rewrite(once), once)


class StaleTest(unittest.TestCase):
    def test_another_major_or_a_missing_suffix_is_stale(self):
        self.assertEqual(stale(f'"{CLIENT}/v9"'), [f"{CLIENT}/v9"])
        self.assertEqual(stale(f'"{TC}"'), [TC])

    def test_a_version_whose_major_differs_from_the_suffix_is_stale(self):
        self.assertEqual(stale(f"{TC}/v10@v9.0.0"), [f"{TC}/v10@v9.0.0"])
        self.assertEqual(stale(f"require {CLIENT}/v10 v9.0.0"), [f"{CLIENT}/v10 v9.0.0"])

    def test_current_references_are_not_stale(self):
        self.assertEqual(stale(f"require {CLIENT}/v10 v10.0.0\n{TC}/v10@v10.1.0"), [])


if __name__ == "__main__":
    unittest.main()
