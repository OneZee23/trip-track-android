#!/usr/bin/env python3
"""iOS: SentryDSNWiringTests.swift. Inspect the built APK, including intentional empty DSNs."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess


def check(apk: Path, apkanalyzer: Path, expected: str) -> None:
    result = subprocess.run(
        [str(apkanalyzer), "dex", "code", "--class", "com.onezee.triptrack.BuildConfig", str(apk)],
        capture_output=True, text=True, check=True,
    )
    field = re.search(r'^\s*\.field\s+.*\bSENTRY_DSN:Ljava/lang/String;\s*=\s*("(?:[^"\\]|\\.)*")\s*$',
                      result.stdout, re.MULTILINE)
    if field is None:
        raise SystemExit("Built APK is missing BuildConfig.SENTRY_DSN or its constant value.")
    value = json.loads(field.group(1))
    if "$(" in value or "${" in value:
        raise SystemExit("Built APK contains an unexpanded Sentry DSN placeholder.")
    if value != expected:
        # Never print either DSN to CI logs, including on failure.
        raise SystemExit("Built APK Sentry DSN differs from the Gradle property/environment value.")
    print(f"Sentry APK wiring passed ({'configured' if value else 'disabled'}, value not logged).")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--apkanalyzer", type=Path, required=True)
    args = parser.parse_args()
    if "TRIPTRACK_EXPECTED_SENTRY_DSN" not in os.environ:
        parser.error("TRIPTRACK_EXPECTED_SENTRY_DSN must be set (empty is valid)")
    check(args.apk, args.apkanalyzer, os.environ["TRIPTRACK_EXPECTED_SENTRY_DSN"])
