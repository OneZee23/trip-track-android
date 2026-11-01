import contextlib
import io
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from check_sentry_wiring import check


class SentryArtifactTests(unittest.TestCase):
    def verify(self, field: str, expected: str) -> None:
        result = subprocess.CompletedProcess([], 0, field, "")
        with patch("check_sentry_wiring.subprocess.run", return_value=result), contextlib.redirect_stdout(io.StringIO()):
            check(Path("candidate.apk"), Path("apkanalyzer"), expected)

    def test_empty_and_configured_compiled_values(self):
        self.verify('.field public static final SENTRY_DSN:Ljava/lang/String; = ""', "")
        self.verify('.field public static final SENTRY_DSN:Ljava/lang/String; = "fixture"', "fixture")

    def test_missing_key_fails_instead_of_being_skipped(self):
        with self.assertRaises(SystemExit):
            self.verify("", "")

    def test_mismatch_and_unexpanded_placeholder_fail_without_values(self):
        for actual in ["private-fixture", "$(TRIPTRACK_SENTRY_DSN)", "${TRIPTRACK_SENTRY_DSN}"]:
            with self.subTest(actual=actual), self.assertRaises(SystemExit) as failure:
                self.verify(f'.field public static final SENTRY_DSN:Ljava/lang/String; = "{actual}"', "expected-fixture")
            self.assertNotIn(actual, str(failure.exception))
            self.assertNotIn("expected-fixture", str(failure.exception))


class PageAlignmentTests(unittest.TestCase):
    def run_check(self, libraries, zip_exit=0):
        with tempfile.TemporaryDirectory(prefix="triptrack-release-test-") as directory:
            root = Path(directory)
            apk = root / "candidate.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                for name, contents in libraries.items():
                    archive.writestr(name, contents)
            zipalign = root / "zipalign"
            zipalign.write_text(f"#!/bin/sh\nexit {zip_exit}\n")
            readelf = root / "llvm-readelf"
            readelf.write_text('''#!/usr/bin/env python3
from pathlib import Path
import sys
kind = Path(sys.argv[-1]).read_text()
print("Class: ELF32" if kind == "32bit" else "Class: ELF64")
if kind == "aligned": print("  LOAD 0x0000 0x0000 0x0000 0x0100 0x0100 R E 0x4000")
if kind == "32bit": print("  LOAD 0x0000 0x0000 0x0000 0x0100 0x0100 R E 0x1000")
if kind == "4kb": print("  LOAD 0x0000 0x0000 0x0000 0x0100 0x0100 R E 0x1000")
if kind == "offset": print("  LOAD 0x1000 0x0000 0x0000 0x0100 0x0100 R E 0x4000")
''')
            for tool in (zipalign, readelf):
                tool.chmod(0o700)
            environment = dict(os.environ, ZIPALIGN=str(zipalign), LLVM_READELF=str(readelf))
            return subprocess.run(["bash", str(Path(__file__).with_name("check_16kb.sh")), str(apk)],
                                  env=environment, text=True, capture_output=True)

    def test_every_library_and_abi_are_checked(self):
        result = self.run_check({"lib/arm64-v8a/libfog.so": "aligned", "lib/x86/libthirdparty.so": "aligned"})
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("libthirdparty.so", result.stdout)
        self.assertIn("all 2 libraries", result.stdout)

    def test_bad_alignment_missing_loads_and_wrong_offsets_fail(self):
        for contents in ("4kb", "empty", "offset"):
            with self.subTest(contents=contents):
                result = self.run_check({"lib/arm64-v8a/libfog.so": "aligned", "lib/x86_64/libbad.so": contents})
                self.assertNotEqual(0, result.returncode)
                self.assertIn("FAIL lib/x86_64/libbad.so", result.stdout)

    def test_empty_apk_and_failed_zip_alignment_fail(self):
        self.assertNotEqual(0, self.run_check({}).returncode)
        self.assertNotEqual(0, self.run_check({"lib/arm64-v8a/libfog.so": "aligned"}, zip_exit=1).returncode)

    def test_32bit_loads_are_checked_at_their_supported_page_size(self):
        result = self.run_check({"lib/armeabi-v7a/libmaplibre.so": "32bit", "lib/arm64-v8a/libmaplibre.so": "aligned"})
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("ELF32, required 4 KB", result.stdout)
        # The ELF header governs the check: a 64-bit 4 KB library cannot hide in a 32-bit directory.
        self.assertNotEqual(0, self.run_check({"lib/armeabi-v7a/libbad.so": "4kb"}).returncode)


if __name__ == "__main__":
    unittest.main()
