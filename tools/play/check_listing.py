#!/usr/bin/env python3
"""Check the complete Google Play listing with Python's standard library."""

import argparse
from pathlib import Path
import sys
import unicodedata


ROOT = Path(__file__).resolve().parents[2]
PLAY_LOCALES = {
    "en": "en-US", "ru": "ru-RU", "de": "de-DE", "es": "es-ES",
    "fr": "fr-FR", "it": "it-IT", "pl": "pl-PL", "id": "id",
    "tr": "tr-TR", "fil": "fil", "uk": "uk", "kk": "kk", "pt": "pt-BR",
}
LIMITS = {"title.txt": 30, "short-description.txt": 80, "full-description.txt": 4000}


def character_count(value):
    # UTF-16 also counts supplementary characters as two: never undercount a Console field.
    return len(value.encode("utf-16-le")) // 2


def check_listing(root):
    errors = []
    if not root.is_dir():
        return [f"{root}: listing directory is missing"]
    expected = set(PLAY_LOCALES.values())
    actual = {path.name for path in root.iterdir() if path.is_dir()}
    for locale in sorted(expected - actual):
        errors.append(f"{locale}: missing locale")
    for locale in sorted(actual - expected):
        errors.append(f"{locale}: unexpected locale")
    for locale in sorted(expected & actual):
        for name, limit in LIMITS.items():
            path = root / locale / name
            try:
                value = path.read_text(encoding="utf-8")
            except (OSError, UnicodeError) as error:
                errors.append(f"{locale}/{name}: cannot read UTF-8 ({error.__class__.__name__})")
                continue
            # A final file newline is not pasted into Console; internal newlines count.
            if value.endswith("\n"):
                value = value[:-1]
            prefix = f"{locale}/{name}"
            if not value.strip():
                errors.append(f"{prefix}: empty field")
            if value != value.strip():
                errors.append(f"{prefix}: leading or trailing whitespace")
            if any(unicodedata.category(char) in ("Cc", "Cf") and char != "\n" for char in value):
                errors.append(f"{prefix}: control or invisible formatting character")
            if name != "full-description.txt" and "\n" in value:
                errors.append(f"{prefix}: must be one line")
            count = character_count(value)
            if count > limit:
                errors.append(f"{prefix}: {count} characters exceeds {limit}")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--listing", type=Path, default=ROOT / "docs/play/listing")
    args = parser.parse_args()
    errors = check_listing(args.listing)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Play listing: {len(PLAY_LOCALES)} locales, all fields within 30/80/4000 characters")
    return 0


if __name__ == "__main__":
    sys.exit(main())
