"""Store limits, locale completeness and invalid input must fail before upload."""

from pathlib import Path
import tempfile
import unittest

from check_listing import LIMITS, PLAY_LOCALES, character_count, check_listing


class ListingTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        for locale in PLAY_LOCALES.values():
            folder = self.root / locale
            folder.mkdir()
            for name, limit in LIMITS.items():
                (folder / name).write_text("я" * limit + "\n", encoding="utf-8")

    def test_all_thirteen_locales_at_limits_pass(self):
        self.assertEqual(len(PLAY_LOCALES), 13)
        self.assertEqual(PLAY_LOCALES["pt"], "pt-BR")
        self.assertEqual(check_listing(self.root), [])

    def test_each_limit_rejects_one_extra_character(self):
        for name, limit in LIMITS.items():
            with self.subTest(name=name):
                path = self.root / "ru-RU" / name
                path.write_text("я" * (limit + 1), encoding="utf-8")
                self.assertTrue(any(f"{limit + 1} characters exceeds {limit}" in error for error in check_listing(self.root)))
                path.write_text("я" * limit, encoding="utf-8")

    def test_missing_locale_and_file_fail(self):
        folder = self.root / "kk"
        for path in folder.iterdir():
            path.unlink()
        folder.rmdir()
        (self.root / "id" / "title.txt").unlink()
        errors = check_listing(self.root)
        self.assertIn("kk: missing locale", errors)
        self.assertTrue(any("id/title.txt: cannot read" in error for error in errors))

    def test_wrong_locale_code_fails(self):
        (self.root / "pt-BR").rename(self.root / "pt-PT")
        self.assertIn("pt-BR: missing locale", check_listing(self.root))
        self.assertIn("pt-PT: unexpected locale", check_listing(self.root))

    def test_blank_invalid_utf8_and_hidden_characters_fail(self):
        path = self.root / "en-US" / "title.txt"
        for value, message in [(b"\n", "empty field"), (b"\xff", "cannot read UTF-8"),
                               ("Trip\u200bTrack".encode(), "invisible formatting"),
                               (b"TripTrack\n\n", "trailing whitespace")]:
            with self.subTest(value=value):
                path.write_bytes(value)
                self.assertTrue(any(message in error for error in check_listing(self.root)))

    def test_only_full_description_allows_internal_newlines(self):
        (self.root / "en-US" / "full-description.txt").write_text("First\n\nSecond\n")
        self.assertEqual(check_listing(self.root), [])
        (self.root / "en-US" / "short-description.txt").write_text("First\nSecond\n")
        self.assertTrue(any("must be one line" in error for error in check_listing(self.root)))

    def test_unicode_counts_characters_without_undercounting_emoji(self):
        self.assertEqual(character_count("éїқ"), 3)
        self.assertEqual(character_count("🚗"), 2)
        (self.root / "en-US" / "title.txt").write_text("🚗" * 16)
        self.assertTrue(any("32 characters exceeds 30" in error for error in check_listing(self.root)))

    def test_missing_root_fails(self):
        self.assertTrue(check_listing(self.root / "missing"))

    def test_repository_listing_passes(self):
        self.assertEqual(check_listing(Path(__file__).resolve().parents[2] / "docs/play/listing"), [])


if __name__ == "__main__":
    unittest.main()
