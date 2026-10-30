#!/usr/bin/env python3
"""Validate generated Android resources without Gradle or third-party modules."""

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

from generate import CATEGORIES, DIRECTORIES, LANGUAGES, ROOT, TOKEN, android_escape, signature


def android_decode(value):
    result, index = [], 0
    while index < len(value):
        char = value[index]
        if char in "'\"\n\r\t":
            raise ValueError("unescaped Android quote or whitespace")
        if char == "\\":
            if index + 1 == len(value):
                raise ValueError("trailing backslash")
            following = value[index + 1]
            if following == "u":
                digits = value[index + 2:index + 6]
                if not re.fullmatch(r"[0-9A-Fa-f]{4}", digits):
                    raise ValueError("invalid Android Unicode escape")
                result.append(chr(int(digits, 16)))
                index += 6
                continue
            escapes = {"\\": "\\", "'": "'", '"': '"', "n": "\n", "r": "\r", "t": "\t", "@": "@", "?": "?"}
            if following not in escapes:
                raise ValueError("invalid Android escape")
            result.append(escapes[following])
            index += 2
        else:
            if index == 0 and char in "@?":
                raise ValueError("unescaped resource reference prefix")
            result.append(char)
            index += 1
    decoded = "".join(result)
    if android_escape(decoded) != escape(value):
        raise ValueError("noncanonical escaping; whitespace or text may change in aapt2")
    return decoded


def read_resources(path):
    root = ET.parse(path).getroot()
    if root.tag != "resources":
        raise ValueError(f"{path}: missing resources root")
    resources, android_names = {}, set()
    for element in root:
        kind, name = element.tag, element.get("name")
        if kind not in ("string", "plurals") or not name or not re.fullmatch(r"[A-Za-z_][A-Za-z_0-9.]*", name):
            raise ValueError(f"{path}: invalid resource {kind}/{name}")
        identity = (kind, name)
        android_identity = (kind, name.replace(".", "_"))
        if identity in resources or android_identity in android_names:
            raise ValueError(f"{path}: duplicate resource or R field collision: {identity}")
        android_names.add(android_identity)
        if kind == "string":
            if len(element):
                raise ValueError(f"{path}: unescaped XML markup in {name}")
            items = {"text": android_decode(element.text or "")}
        else:
            items = {}
            for item in element:
                quantity = item.get("quantity")
                if item.tag != "item" or len(item) or quantity in items:
                    raise ValueError(f"{path}: invalid plural item in {name}")
                items[quantity] = android_decode(item.text or "")
        resources[identity] = (items, element.get("formatted") != "false")
    return resources


def validate(res, metadata):
    manifest = json.loads((metadata / "manifest.json").read_text(encoding="utf-8"))
    records = {(item["kind"], item["name"]): item for item in manifest["resources"]}
    if len(records) != len(manifest["resources"]):
        raise ValueError("duplicate manifest resources")
    base = read_resources(res / "values/strings.xml")
    if set(base) != set(records):
        raise ValueError("default values/ keys differ from manifest")
    counts = {}
    for lang in LANGUAGES:
        resources = read_resources(res / DIRECTORIES[lang] / "strings.xml")
        if set(resources) != set(base):
            missing, extra = set(base) - set(resources), set(resources) - set(base)
            raise ValueError(f"{lang}: key coverage differs; missing={sorted(missing)}, extra={sorted(extra)}")
        for identity, (items, xml_formatted) in resources.items():
            record = records[identity]
            categories = set(CATEGORIES[lang]) if identity[0] == "plurals" else {"text"}
            if set(items) != categories:
                raise ValueError(f"{lang}/{identity}: wrong CLDR categories {set(items)}")
            if identity[0] == "string" and xml_formatted != record["formatted"]:
                raise ValueError(f"{lang}/{identity}: incorrect formatted attribute")
            for category, text in items.items():
                if TOKEN.search(text):
                    raise ValueError(f"{lang}/{identity}: unconverted placeholder token")
                signatures = record.get("language_signatures", {}).get(lang, record["signatures"])
                expected = signatures.get(category, signatures.get("other", {}))
                actual = signature(text) if record["formatted"] else {}
                if {str(index): value for index, value in actual.items()} != expected:
                    raise ValueError(f"{lang}/{identity}/{category}: inconsistent positional placeholders")
        counts[lang] = {"strings": sum(kind == "string" for kind, _ in resources), "plurals": sum(kind == "plurals" for kind, _ in resources)}
        if any(counts[lang][kind] != manifest["counts"][lang][kind] for kind in ("strings", "plurals")):
            raise ValueError(f"{lang}: counts differ from manifest")
    config = ET.parse(res / "xml/locales_config.xml").getroot()
    names = [element.get("{http://schemas.android.com/apk/res/android}name") for element in config]
    if config.tag != "locale-config" or names != list(LANGUAGES) or any(item.tag != "locale" for item in config):
        raise ValueError("locales_config.xml must list all 13 BCP 47 language codes once")
    # Every scanned function must be fully extracted or have a source-located handoff.
    manual = {(item["file"], item["line"], item["name"]) for item in manifest["manual"]}
    for function in manifest["functions"]:
        if not function["complete"] and (function["file"], function["line"], function["function"]) not in manual:
            raise ValueError(f"unaccounted function {function}")
    return counts


def compile_resources(res, aapt2, android_jar=None):
    with tempfile.TemporaryDirectory(prefix="triptrack-strings-") as temporary:
        work = Path(temporary)
        selected = work / "res"
        for directory in list(DIRECTORIES.values()) + ["xml"]:
            target = selected / directory
            target.mkdir(parents=True, exist_ok=True)
            filename = "locales_config.xml" if directory == "xml" else "strings.xml"
            shutil.copyfile(res / directory / filename, target / filename)
        archive = work / "resources.zip"
        subprocess.run([str(aapt2), "compile", "--dir", str(selected), "-o", str(archive)], check=True)
        if android_jar:
            manifest = work / "AndroidManifest.xml"
            manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.stringsvalidation"><uses-sdk android:minSdkVersion="29" android:targetSdkVersion="36"/><application android:localeConfig="@xml/locales_config"/></manifest>', encoding="utf-8")
            subprocess.run([str(aapt2), "link", "-I", str(android_jar), "--manifest", str(manifest), "-o", str(work / "validation.apk"), str(archive)], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--res", type=Path, default=ROOT / "core/designsystem/src/main/res")
    parser.add_argument("--metadata", type=Path, default=Path(__file__).resolve().parent)
    parser.add_argument("--aapt2", type=Path, help="also compile with this installed aapt2")
    parser.add_argument("--android-jar", type=Path, help="also link using this platform android.jar")
    args = parser.parse_args()
    try:
        if args.android_jar and not args.aapt2:
            raise ValueError("--android-jar requires --aapt2")
        counts = validate(args.res, args.metadata)
        if args.aapt2:
            compile_resources(args.res, args.aapt2, args.android_jar)
        print("language  strings  plurals")
        for lang, count in counts.items():
            print(f"{lang:8}  {count['strings']:7}  {count['plurals']:7}")
        print("Validation passed" + ("; aapt2 compile and link passed" if args.android_jar else "; aapt2 compile passed" if args.aapt2 else ""))
        return 0
    except (ValueError, OSError, ET.ParseError, subprocess.CalledProcessError) as error:
        print(f"Validation failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
