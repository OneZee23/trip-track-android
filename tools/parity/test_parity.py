"""Parity tooling regressions; fixtures live in temporary checkouts, with no iOS dependency."""

from contextlib import redirect_stderr, redirect_stdout
import io
from pathlib import Path
import subprocess
import tempfile
import unittest

from parity import (android_changes, android_sources, build_index, changed_files,
                    divergence_reasons, ios_changes, main, markers, range_commits,
                    references, render_report, resolve)


class ParityTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.android = self.root / "android"
        self.ios = self.root / "ios"
        self.android.mkdir()
        (self.ios / "TripTrack").mkdir(parents=True)

    def write(self, root, path, text=""):
        target = root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")
        return target

    def index(self):
        return build_index(self.android, self.ios)

    def git(self, root, *args, input=None):
        return subprocess.run(["git", "-C", str(root), *args], input=input,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              check=True).stdout.decode().strip()

    def init_git(self, root):
        self.git(root, "init", "-b", "main")
        self.git(root, "config", "user.name", "Parity fixture")
        self.git(root, "config", "user.email", "fixture@example.invalid")
        self.git(root, "config", "commit.gpgsign", "false")

    def snapshot(self, root, parents=()):
        # Build fixture history without invoking git commit or touching the real checkout's index.
        self.git(root, "add", "--all")
        tree = self.git(root, "write-tree")
        parent_args = [arg for parent in parents for arg in ("-p", parent)]
        revision = self.git(root, "commit-tree", tree, *parent_args, input=b"fixture\n")
        self.git(root, "update-ref", "refs/heads/main", revision)
        return revision

    def test_markers_include_wrapped_lists_and_multiple_comments(self):
        source = """// iOS: Models/Trip.swift (summary),
// Journey.swift
package fixture
// Ordinary Unrelated.swift does not claim a counterpart.
class Trip { // iOS: TripTrackShared/UnitNumber.swift
}
// iOS: First.swift
// iOS: Second.swift
"""
        blocks = list(markers(source))
        self.assertEqual([references(block) for block in blocks], [
            ["Journey.swift", "Models/Trip.swift"], ["TripTrackShared/UnitNumber.swift"],
            ["First.swift"], ["Second.swift"]])

    def test_braces_globs_plus_names_and_deduplication(self):
        self.assertEqual(references("Networking/{PlusAPI,SecretsAPI}.swift, AppStrings+*.swift; PlusAPI.swift."),
                         ["AppStrings+*.swift", "Networking/PlusAPI.swift", "Networking/SecretsAPI.swift", "PlusAPI.swift"])
        files = ["TripTrack/Localization/AppStrings+Trip.swift", "TripTrack/Localization/AppStrings+Map.swift"]
        self.assertEqual(resolve("AppStrings+*.swift", files), sorted(files))

    def test_scans_module_sources_and_tests_but_not_builds_or_spikes(self):
        included = ["app/src/main/kotlin/App.kt", "core/logic/src/test/kotlin/GateTest.kt",
                    "feature/new/src/main/java/New.java", "core/map/src/main/cpp/fog.cpp"]
        excluded = ["core/logic/build/generated/src/Gate.kt", "spikes/map/src/main/Map.kt",
                    "tools/parity/src/Fake.kt", "docs/Example.kt", "app/src/main/res/values/strings.xml"]
        for path in included + excluded:
            self.write(self.android, path)
        self.assertEqual(android_sources(self.android), sorted(included))

    def test_full_report_counts_per_folder_and_keeps_shared_and_missing(self):
        for path in ("TripTrack/Models/Trip.swift", "TripTrack/Models/Journey.swift",
                     "TripTrack/Services/Gate.swift", "TripTrackShared/UnitNumber.swift"):
            self.write(self.ios, path)
        self.write(self.ios, "TripTrackTests/GateTests.swift")
        self.write(self.ios, "Unrelated/Outside.swift")
        self.write(self.android, "core/logic/src/main/Gate.kt", "// iOS: Gate.swift\n// iOS: Gate.swift\n")
        self.write(self.android, "core/logic/src/test/GateTests.kt", "// iOS: GateTests.swift\n")
        self.write(self.android, "app/src/main/App.kt")
        index = self.index()
        report = render_report(index, {})
        self.assertIn("Swift files: 4. With Android references: 1. Missing: 3.", report)
        self.assertIn("| `TripTrack/Models` | 2 | 0 | 2 |", report)
        self.assertIn("| `TripTrack/Models/Trip.swift` | missing |", report)
        self.assertIn("| `TripTrackShared/UnitNumber.swift` | missing |", report)
        self.assertIn("| `app/src/main/App.kt` | not documented |", report)
        self.assertEqual(index.to_ios["core/logic/src/test/GateTests.kt"], {"TripTrackTests/GateTests.swift"})
        self.assertNotIn("Outside.swift", report)
        self.assertEqual(report, render_report(self.index(), {}))
        self.assertNotIn(str(self.root), report)

    def test_one_swift_to_many_android_and_many_swift_to_one_android(self):
        for path in ("TripTrack/Gate.swift", "TripTrack/Trip.swift"):
            self.write(self.ios, path)
        self.write(self.android, "core/logic/src/main/Gate.kt", "// iOS: Gate.swift, Trip.swift\n")
        self.write(self.android, "core/model/src/main/Trip.kt", "// iOS: Trip.swift\n")
        index = self.index()
        self.assertEqual(len(index.to_android["TripTrack/Trip.swift"]), 2)
        self.assertEqual(len(index.to_ios["core/logic/src/main/Gate.kt"]), 2)

    def test_ambiguous_basename_reports_all_and_paths_disambiguate(self):
        files = ["TripTrack/Models/Item.swift", "TripTrack/Views/Item.swift"]
        for path in files:
            self.write(self.ios, path)
        self.write(self.android, "app/src/main/Item.kt", "// iOS: Item.swift\n")
        index = self.index()
        self.assertEqual(index.to_ios["app/src/main/Item.kt"], set(files))
        self.assertIn("ambiguous:", index.warnings[0][2])
        self.assertEqual(resolve("Models/Item.swift", files), files[:1])
        self.assertEqual(resolve(files[1], files), files[1:])

    def test_unknown_and_symbol_only_markers_are_visible(self):
        self.write(self.android, "app/src/main/Storage.kt", "// iOS: UserDefaults flags\n// iOS: Gone.swift\n")
        index = self.index()
        self.assertFalse(index.to_ios["app/src/main/Storage.kt"])
        self.assertEqual({issue for _, _, issue in index.warnings}, {"not found", "no Swift filename"})
        self.assertIn("Gone.swift", render_report(index, {}))

    def test_android_only_reasons_use_paths_or_unique_basenames(self):
        sources = ["app/src/main/App.kt", "core/data/src/main/Platform.kt", "app/src/test/App.kt"]
        self.write(self.android, "docs/divergences.md", """| Files | Reason |
|---|---|
| `app/src/main/App.kt` | Android entry point |
| `Platform.kt` | Native storage |
| `App.kt` | Ambiguous, do not guess |
""")
        reasons = divergence_reasons(self.android, sources)
        self.assertEqual(reasons, {sources[0]: "Android entry point", sources[1]: "Native storage"})
        for source in sources:
            self.write(self.android, source)
        self.assertIn("| `app/src/main/App.kt` | Android entry point |", render_report(self.index(), reasons))

    def test_ios_changes_include_modified_added_deleted_and_localization(self):
        self.init_git(self.ios)
        old = self.write(self.ios, "TripTrack/Models/Old.swift", "old\n")
        self.write(self.ios, "TripTrack/Gate.swift", "one\n")
        base = self.snapshot(self.ios)
        old.unlink()
        self.write(self.ios, "TripTrack/Gate.swift", "two\n")
        self.write(self.ios, "TripTrack/New.swift", "new\n")
        self.write(self.ios, "TripTrack/Localization/Translations/Translations+DE.swift", "de\n")
        end = self.snapshot(self.ios, [base])
        self.write(self.android, "core/logic/src/main/Gate.kt", "// iOS: Gate.swift, Old.swift\n")
        output = ios_changes(self.android, self.ios, f"{base}..{end}")
        self.assertIn("D TripTrack/Models/Old.swift\n  core/logic/src/main/Gate.kt", output)
        self.assertIn("M TripTrack/Gate.swift\n  core/logic/src/main/Gate.kt", output)
        self.assertIn("A TripTrack/New.swift\n  no counterpart yet", output)
        self.assertIn("run tools/strings/generate.py", output)

    def test_ios_rename_uses_both_names(self):
        self.init_git(self.ios)
        old = self.write(self.ios, "TripTrack/Old.swift", "struct Keep {}\n")
        base = self.snapshot(self.ios)
        old.rename(self.ios / "TripTrack/New.swift")
        end = self.snapshot(self.ios, [base])
        self.write(self.android, "app/src/main/Old.kt", "// iOS: Old.swift\n")
        self.write(self.android, "app/src/main/New.kt", "// iOS: New.swift\n")
        output = ios_changes(self.android, self.ios, f"{base}..{end}")
        self.assertIn("R100 TripTrack/Old.swift -> TripTrack/New.swift", output)
        self.assertIn("  app/src/main/Old.kt", output)
        self.assertIn("  app/src/main/New.kt", output)

    def test_ios_non_swift_strings_and_shared_strings_trigger_generation(self):
        for path in ("TripTrack/Resources/de.lproj/InfoPlist.strings", "TripTrackShared/LiveActivityStrings.swift"):
            with self.subTest(path=path), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                self.init_git(root)
                self.write(root, "TripTrack/Keep.swift")
                self.write(root, path, "one")
                base = self.snapshot(root)
                (root / path).unlink()
                end = self.snapshot(root, [base])
                self.assertIn("run tools/strings/generate.py", ios_changes(self.android, root, f"{base}..{end}"))

    def test_reverse_reads_both_revisions_even_if_worktree_has_other_markers(self):
        self.init_git(self.android)
        for name in ("Before", "After", "Worktree"):
            self.write(self.ios, f"TripTrack/{name}.swift")
        source = "app/src/main/Fix.kt"
        self.write(self.android, source, "// iOS: Before.swift\n")
        base = self.snapshot(self.android)
        self.write(self.android, source, "// iOS: After.swift\n")
        end = self.snapshot(self.android, [base])
        self.write(self.android, source, "// iOS: Worktree.swift\n")
        output = android_changes(self.android, self.ios, f"{base}..{end}")
        self.assertIn("  TripTrack/Before.swift", output)
        self.assertIn("  TripTrack/After.swift", output)
        self.assertNotIn("Worktree.swift", output)

    def test_reverse_deletion_and_rename_keep_old_counterparts_and_paths_with_spaces(self):
        self.init_git(self.android)
        self.write(self.ios, "TripTrack/Gate.swift")
        old = self.write(self.android, "app/src/main/Old name.kt", "// iOS: Gate.swift\n")
        deleted = self.write(self.android, "app/src/main/Удалить.kt", "// iOS: Gate.swift\nclass Old\n")
        base = self.snapshot(self.android)
        old.rename(old.with_name("New name.kt"))
        deleted.unlink()
        end = self.snapshot(self.android, [base])
        changes = changed_files(self.android, base, end)
        self.assertEqual(len(changes), 2)
        output = android_changes(self.android, self.ios, f"{base}..{end}")
        self.assertIn("Old name.kt -> app/src/main/New name.kt", output)
        self.assertIn("D app/src/main/Удалить.kt\n  TripTrack/Gate.swift", output)

    def test_reverse_android_only_reason_and_string_resources(self):
        self.init_git(self.android)
        self.write(self.android, "docs/divergences.md", "| File | Why |\n|---|---|\n| `app/src/main/Native.kt` | Native service |\n")
        base = self.snapshot(self.android)
        self.write(self.android, "app/src/main/Native.kt")
        self.write(self.android, "core/designsystem/src/main/res/values-ru/strings.xml", "<resources/>\n")
        end = self.snapshot(self.android, [base])
        output = android_changes(self.android, self.ios, f"{base}..{end}")
        self.assertIn("Android-only: Native service", output)
        self.assertIn("update TripTrack/Localization in iOS first; then run tools/strings/generate.py", output)

    def test_empty_range_and_three_dot_range(self):
        self.init_git(self.ios)
        self.write(self.ios, "TripTrack/Base.swift")
        base = self.snapshot(self.ios)
        self.write(self.ios, "TripTrack/Left.swift")
        left = self.snapshot(self.ios, [base])
        (self.ios / "TripTrack/Left.swift").unlink()
        self.write(self.ios, "TripTrack/Right.swift")
        right = self.snapshot(self.ios, [base])
        self.assertEqual(range_commits(self.ios, f"{left}...{right}"), [base, right])
        output = ios_changes(self.android, self.ios, f"{left}...{right}")
        self.assertIn("TripTrack/Right.swift", output)
        self.assertNotIn("TripTrack/Left.swift", output)
        self.assertEqual(ios_changes(self.android, self.ios, f"{base}..{base}"), "No changed files in this range.\n")

    def test_bad_ranges_are_errors_and_never_shell_commands(self):
        for revision_range in ("HEAD", "--help..HEAD", "HEAD..--stat", "no-such-ref..HEAD", "$(touch unsafe)..HEAD"):
            with self.subTest(revision_range=revision_range), self.assertRaises(ValueError):
                range_commits(self.ios, revision_range)
        self.assertFalse((self.root / "unsafe").exists())

    def test_cli_report_accepts_options_before_or_after_command(self):
        self.write(self.ios, "TripTrack/Trip.swift")
        common = ["--ios", str(self.ios), "--android", str(self.android)]
        for arguments in (["report"] + common, common + ["report"]):
            with self.subTest(arguments=arguments), redirect_stdout(io.StringIO()):
                self.assertEqual(main(arguments), 0)
        self.assertTrue((self.android / "docs/parity.md").exists())

    def test_cli_missing_checkout_bad_range_and_readonly_ios_output(self):
        common = ["--ios", str(self.ios), "--android", str(self.android)]
        for arguments in (["report", "--ios", str(self.root / "missing")],
                          ["changed", "HEAD"] + common,
                          ["report", "--output", str(self.ios / "parity.md")] + common):
            with self.subTest(arguments=arguments), redirect_stderr(io.StringIO()) as errors:
                self.assertEqual(main(arguments), 2)
                self.assertTrue(errors.getvalue().startswith("parity:"))
        self.assertFalse((self.ios / "parity.md").exists())


if __name__ == "__main__":
    unittest.main()
