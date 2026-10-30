#!/usr/bin/env python3
"""Find the other platform's files from Android's // iOS: comments. Stdlib only."""

import argparse
from collections import defaultdict
from dataclasses import dataclass
from fnmatch import fnmatchcase
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
IOS_ROOTS = ("TripTrack", "TripTrackShared")
TEST_ROOTS = ("TripTrackTests", "TripTrackUITests")
SOURCE_SUFFIXES = {".kt", ".java", ".c", ".cc", ".cpp", ".h", ".hpp"}
EXCLUDED = {".git", ".gradle", ".kotlin", ".idea", ".cxx", "build", "spikes", "tools"}
MARKER = re.compile(r"//\s*iOS:\s*(.*)")
SWIFT_FILE = re.compile(r"(?:[\w+*?-]+/)*[\w+*?-]+\.swift\b")


def source_path(path):
    parts = PurePosixPath(path).parts
    return ("src" in parts and not EXCLUDED.intersection(parts)
            and PurePosixPath(path).suffix in SOURCE_SUFFIXES)


def android_sources(root):
    files = []
    for directory, folders, names in os.walk(root):
        folders[:] = sorted(name for name in folders if name not in EXCLUDED)
        for name in sorted(names):
            path = Path(directory, name)
            relative = path.relative_to(root).as_posix()
            if source_path(relative):
                files.append(relative)
    return sorted(files)


def ios_sources(root, folders=IOS_ROOTS):
    return sorted(path.relative_to(root).as_posix()
                  for folder in folders for path in (root / folder).rglob("*.swift")
                  if path.is_file())


def expand_braces(text):
    match = re.search(r"\{([^{}]+)\}", text)
    if match is None:
        return [text]
    return [expanded for item in match[1].split(",")
            for expanded in expand_braces(text[:match.start()] + item.strip() + text[match.end():])]


def markers(source):
    """Keep wrapped // comments together, including lists after the first line."""
    block = None
    for line in source.splitlines():
        match = MARKER.search(line)
        if match:
            if block is not None:
                yield block
            block = match[1]
        elif block is not None and line.lstrip().startswith("//"):
            block += " " + line.lstrip()[2:].strip()
        else:
            if block is not None:
                yield block
                block = None
    if block is not None:
        yield block


def references(block):
    return sorted({match[0] for expanded in expand_braces(block)
                   for match in SWIFT_FILE.finditer(expanded)})


def resolve(reference, files):
    # A full/suffix path disambiguates equal basenames. Bare names deliberately return all matches.
    if reference in files:
        return [reference]
    if "/" in reference:
        return sorted(path for path in files
                      if fnmatchcase(path, reference) or fnmatchcase(path, "*/" + reference))
    return sorted(path for path in files if fnmatchcase(PurePosixPath(path).name, reference))


@dataclass
class ParityIndex:
    swift: list
    android: list
    to_android: dict
    to_ios: dict
    warnings: list


def build_index(android, ios, extra_swift=()):
    swift = ios_sources(ios)
    available = sorted(set(swift + ios_sources(ios, TEST_ROOTS)) | set(extra_swift))
    sources = android_sources(android)
    to_android = defaultdict(set)
    to_ios = defaultdict(set)
    warnings = []
    for path in sources:
        for block in markers((android / path).read_text(encoding="utf-8")):
            refs = references(block)
            if not refs:
                warnings.append((path, block, "no Swift filename"))
            for ref in refs:
                matches = resolve(ref, available)
                if not matches:
                    warnings.append((path, ref, "not found"))
                elif len(matches) > 1 and not any(char in ref for char in "*?"):
                    warnings.append((path, ref, "ambiguous: " + ", ".join(matches)))
                for match in matches:
                    to_android[match].add(path)
                    to_ios[path].add(match)
    return ParityIndex(swift, sources, to_android, to_ios, sorted(set(warnings)))


def divergence_reasons(android, sources):
    """Rows name source paths (or unique basenames); their last cell gives the reason."""
    path = android / "docs/divergences.md"
    if not path.exists():
        return {}
    reasons = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip().startswith("|"):
            continue
        cells = line.strip().strip("|").split("|")
        if len(cells) < 2:
            continue
        named = re.findall(r"(?:[\w.+-]+/)*[\w.+-]+\.(?:kt|java|cpp|cc|c|hpp|h)\b", "|".join(cells[:-1]))
        for name in named:
            matches = [source for source in sources if source == name]
            if not matches and "/" not in name:
                matches = [source for source in sources if PurePosixPath(source).name == name]
            if len(matches) == 1:
                reasons[matches[0]] = cells[-1].strip()
    return reasons


def code(value):
    return "`" + value.replace("|", "\\|").replace("`", "&#96;") + "`"


def render_report(index, reasons):
    areas = defaultdict(list)
    for path in index.swift:
        areas[str(PurePosixPath(path).parent)].append(path)
    linked = sum(bool(index.to_android[path]) for path in index.swift)
    lines = ["# iOS / Android file parity", "",
             "Regenerate: `python3 tools/parity/parity.py report`.", "",
             "Swift inventory: `TripTrack/` and `TripTrackShared/`. Android inventory: Kotlin, Java and "
             "C/C++ files under module `src/` directories, including tests. Build output and `spikes/` are excluded.", "",
             "Links come from `// iOS:` comments. A link can cover part of a file; it does not prove behavioural parity. "
             "References to `TripTrackTests/` and `TripTrackUITests/` are resolved but excluded from the Swift area counts.", "",
             f"Swift files: {len(index.swift)}. With Android references: {linked}. Missing: {len(index.swift) - linked}.", "",
             "| iOS area | Swift files | With Android references | Missing |",
             "|---|---:|---:|---:|"]
    for area, paths in sorted(areas.items()):
        count = sum(bool(index.to_android[path]) for path in paths)
        lines.append(f"| {code(area)} | {len(paths)} | {count} | {len(paths) - count} |")
    for area, paths in sorted(areas.items()):
        lines += ["", f"## {area}", "", "| Swift file | Android counterpart files |", "|---|---|"]
        for path in paths:
            counterparts = "<br>".join(code(item) for item in sorted(index.to_android[path])) or "missing"
            lines.append(f"| {code(path)} | {counterparts} |")
    unmapped = [path for path in index.android if not index.to_ios[path]]
    lines += ["", "## Android-only files", "",
              "These files have no resolved Swift counterpart. An absent or unresolved comment also lands here; "
              "it is not evidence of an intentional product divergence.", "",
              f"Files: {len(unmapped)}.", "", "| Android file | Reason from docs/divergences.md |", "|---|---|"]
    for path in unmapped:
        lines.append(f"| {code(path)} | {reasons.get(path, 'not documented')} |")
    if not unmapped:
        lines.append("| none | |")
    lines += ["", "## Marker warnings", "",
              "Use a repository-relative Swift path to resolve an ambiguous name. "
              "Name the `.swift` file explicitly for symbol-only and non-Swift references.", ""]
    if index.warnings:
        lines += ["| Android file | Reference | Issue |", "|---|---|---|"]
        lines += [f"| {code(path)} | {code(ref)} | {issue} |" for path, ref, issue in index.warnings]
    else:
        lines.append("none")
    return "\n".join(lines) + "\n"


def git(root, *args):
    result = subprocess.run(["git", "-C", str(root), *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise ValueError(result.stderr.decode("utf-8", errors="replace").strip())
    return result.stdout


def range_commits(root, revision_range):
    match = re.fullmatch(r"(.+?)(\.{2,3})(.+)", revision_range)
    if match is None or match[1].startswith("-") or match[3].startswith("-"):
        raise ValueError("use a Git range such as v0.9.2..HEAD or master...HEAD")
    commits = [git(root, "rev-parse", "--verify", "--end-of-options", revision + "^{commit}").decode().strip()
               for revision in (match[1], match[3])]
    if match[2] == "...":
        commits[0] = git(root, "merge-base", *commits).decode().strip()
    return commits


@dataclass
class Change:
    status: str
    paths: tuple


def changed_files(root, base, end):
    fields = iter(git(root, "diff", "--name-status", "-z", "--find-renames", base, end, "--").decode("utf-8").split("\0")[:-1])
    changes = []
    for status in fields:
        paths = (next(fields), next(fields)) if status.startswith(("R", "C")) else (next(fields),)
        changes.append(Change(status, paths))
    return changes


def ios_strings(path):
    return (path.startswith("TripTrack/Localization/")
            or (path.startswith("TripTrack/Resources/") and path.endswith("/InfoPlist.strings"))
            or path == "TripTrackShared/LiveActivityStrings.swift")


def android_strings(path):
    return (path.startswith("core/designsystem/src/")
            and (path.endswith("/strings.xml") or path.endswith("/locales_config.xml")
                 or "/designsystem/strings/" in path))


def render_changes(changes, counterparts, reverse=False, reasons=None):
    lines = []
    for change in changes:
        lines.append(f"{change.status} " + " -> ".join(change.paths))
        targets = sorted(counterparts(change))
        lines.extend("  " + path for path in targets)
        if not targets:
            reason = next(((reasons or {}).get(path) for path in change.paths if (reasons or {}).get(path)), None)
            lines.append("  " + ("Android-only: " + reason if reason else "no counterpart yet"))
    if not changes:
        lines.append("No changed files in this range.")
    string_check = android_strings if reverse else ios_strings
    if any(string_check(path) for change in changes for path in change.paths):
        if reverse:
            lines += ["", "Strings: update TripTrack/Localization in iOS first; then run tools/strings/generate.py."]
        else:
            lines += ["", "Strings: run tools/strings/generate.py."]
    return "\n".join(lines) + "\n"


def ios_changes(android, ios, revision_range):
    base, end = range_commits(ios, revision_range)
    changes = changed_files(ios, base, end)
    # Deleted/renamed Swift files must still resolve against comments that use the old name.
    extra = [path for change in changes for path in change.paths if path.endswith(".swift")]
    index = build_index(android, ios, extra)
    return render_changes(changes, lambda change: {
        target for path in change.paths for target in index.to_android[path]})


def android_changes(android, ios, revision_range):
    base, end = range_commits(android, revision_range)
    changes = changed_files(android, base, end)
    available = ios_sources(ios, IOS_ROOTS + TEST_ROOTS)

    def counterparts(change):
        targets = set()
        snapshots = []
        if not change.status.startswith("A"):
            snapshots.append((base, change.paths[0]))
        if not change.status.startswith("D"):
            snapshots.append((end, change.paths[-1]))
        for revision, path in snapshots:
            if source_path(path):
                source = git(android, "show", f"{revision}:{path}").decode("utf-8")
                for block in markers(source):
                    for ref in references(block):
                        targets.update(resolve(ref, available))
        return targets

    all_paths = set(android_sources(android)) | {path for change in changes for path in change.paths if source_path(path)}
    reasons = divergence_reasons(android, all_paths)
    return render_changes(changes, counterparts, reverse=True, reasons=reasons)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.set_defaults(ios=Path.home() / "OneZeeProjects/trip-track", android=ROOT)
    commands = parser.add_subparsers(dest="command", required=True)
    subparsers = []
    for name in ("report", "changed", "changed-android"):
        command = commands.add_parser(name)
        if name == "report":
            command.add_argument("--output", type=Path, help="default: <android>/docs/parity.md")
        else:
            command.add_argument("range", help="two-dot or three-dot Git range")
        subparsers.append(command)
    for command in [parser] + subparsers:
        command.add_argument("--ios", type=Path, default=argparse.SUPPRESS, help="read-only iOS checkout")
        command.add_argument("--android", type=Path, default=argparse.SUPPRESS, help="Android checkout (default: this repository)")
    args = parser.parse_args(argv)
    android, ios = args.android.expanduser().resolve(), args.ios.expanduser().resolve()
    try:
        if not android.is_dir() or not (ios / "TripTrack").is_dir():
            raise ValueError("Android checkout and iOS TripTrack/ must exist; pass --android and --ios")
        if args.command == "report":
            index = build_index(android, ios)
            output = args.output or android / "docs/parity.md"
            output = output.expanduser().resolve()
            # --output must never turn this read-only iOS inspection into a write there.
            if output == ios or ios in output.parents:
                raise ValueError("the iOS checkout is read-only; choose an Android report path")
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(render_report(index, divergence_reasons(android, index.android)), encoding="utf-8")
            print(f"Wrote {output.name}: {len(index.swift)} Swift files, {len(index.warnings)} marker warnings.")
        elif args.command == "changed":
            print(ios_changes(android, ios, args.range), end="")
        else:
            print(android_changes(android, ios, args.range), end="")
    except (OSError, ValueError, UnicodeError) as error:
        print(f"parity: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
