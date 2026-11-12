#!/usr/bin/env python3
"""Generate Android copy. iOS: AppStrings*.swift and LiveActivityStrings.swift."""

import argparse
from collections import Counter
from dataclasses import dataclass, field
import hashlib
import json
from pathlib import Path
import re
import sys
from xml.sax.saxutils import escape

from swift import (Unsupported, arguments, bindings, calls, expression_body, functions,
                   language_cases, lex, literal, matching, split)


LANGUAGES = ("en", "ru", "de", "es", "fr", "it", "pl", "id", "tr", "fil", "uk", "kk", "pt")
DIRECTORIES = {lang: "values" if lang == "en" else "values-" + {"id": "in", "fil": "b+fil"}.get(lang, lang) for lang in LANGUAGES}
# CLDR cardinal categories; Android's ICU version controls selection at runtime.
CATEGORIES = {lang: ("other",) if lang == "id" else ("one", "few", "many", "other") if lang in ("ru", "uk", "pl") else ("one", "many", "other") if lang in ("es", "fr", "it", "pt") else ("one", "other") for lang in LANGUAGES}
TOKEN = re.compile(r"\{([A-Za-z_][A-Za-z_0-9]*)\}")
FOUNDATION_FORMAT = re.compile(r"%(?:(\d+)\$)?([-+ 0#]*\d*(?:\.\d+)?)(?:ll|l|z)?([@diufgeEGs])")
JAVA_FORMAT = re.compile(r"%(\d+)\$([-+ 0#]*\d*(?:\.\d+)?)([sdfgeEG])")
ROOT = Path(__file__).resolve().parents[2]


class ResourceConflict(ValueError):
    pass


@dataclass
class Resource:
    name: str
    kind: str
    values: dict
    source: str
    line: int
    integer_tokens: set = field(default_factory=set)
    foundation_format: bool = False
    language_placeholders: bool = False


def android_escape(value):
    """Encode Android escapes before XML; preserve whitespace without quote wrappers."""
    encoded = []
    for index, char in enumerate(value):
        if char == " " and (index == 0 or index == len(value) - 1 or value[index - 1].isspace() or value[index + 1].isspace()):
            encoded.append("\\u0020")
        else:
            encoded.append({"\\": "\\\\", "'": "\\'", '"': '\\"', "\n": "\\n", "\r": "\\r", "\t": "\\t"}.get(char, char))
    result = "".join(encoded)
    if result.startswith(("@", "?")):
        result = "\\" + result
    return escape(result)


def plural_categories(forms, lang):
    return {category: forms.get(category, forms.get("many", forms["one"])) if category != "other" else forms.get("fraction", forms.get("few", forms.get("many", forms["one"]))) for category in CATEGORIES[lang]}


def placeholder_schema(texts, integer_tokens):
    schema = {}
    for text in texts:
        for match in TOKEN.finditer(text):
            token = match.group(1)
            if token not in schema:
                schema[token] = {"index": len(schema) + 1, "type": "d" if token in integer_tokens else "s"}
    return schema


def format_text(text, schema, foundation_format=False):
    """Normalize token and Foundation arguments, escaping every literal percent."""
    if not schema and not foundation_format:
        return text
    output, pos, next_index = [], 0, len(schema) + 1
    while pos < len(text):
        token = TOKEN.match(text, pos)
        if token:
            spec = schema[token.group(1)]
            output.append(f"%{spec['index']}${spec['type']}")
            pos = token.end()
            continue
        if text[pos] == "%":
            if foundation_format and text.startswith("%%", pos):
                output.append("%%")
                pos += 2
                continue
            match = FOUNDATION_FORMAT.match(text, pos) if foundation_format else None
            if match:
                index = int(match.group(1)) if match.group(1) else next_index
                next_index = max(next_index, index + 1)
                spec = {"@": "s", "i": "d", "u": "d"}.get(match.group(3), match.group(3))
                output.append(f"%{index}${match.group(2)}{spec}")
                pos = match.end()
                continue
            output.append("%%")
        else:
            output.append(text[pos])
        pos += 1
    return "".join(output)


def signature(text):
    signature = {}
    pos = 0
    while pos < len(text):
        if text[pos] != "%":
            pos += 1
            continue
        if text.startswith("%%", pos):
            pos += 2
            continue
        match = JAVA_FORMAT.match(text, pos)
        if not match:
            raise Unsupported("unescaped percent or invalid format argument")
        index, conversion = int(match.group(1)), match.group(3)
        if index < 1 or index in signature and signature[index] != conversion:
            raise Unsupported("inconsistent format argument type")
        signature[index] = conversion
        pos = match.end()
    return signature


def integer_tokens(function):
    params = set()
    for part in split(function.signature):
        for index, token in enumerate(part):
            if token.value == ":" and index and index + 1 < len(part) and part[index + 1].value == "Int":
                params.add(part[index - 1].value)
    result = set()
    for _, _, (_, named) in calls(function.body, "replacingOccurrences"):
        try:
            token = literal(named.get("of", []))
        except Unsupported:
            continue
        replacement = "".join(t.value for t in named.get("with", []))
        if replacement in {f"String({param})" for param in params} and TOKEN.fullmatch(token):
            result.add(token[1:-1])
    return result


def read_translations(path):
    """Resolve chunks in actual merge order, then apply table[key] overrides."""
    tokens = lex(path.read_text(encoding="utf-8"))
    extra_paths = ("Translations+HiddenPrivacy.swift", "Translations+HiddenLocks.swift")
    extra_sources = [
        lex(extra_path.read_text(encoding="utf-8"))
        for extra_name in extra_paths
        if (extra_path := path.parent / extra_name).exists()
    ]
    language_suffix = path.stem.split("+")[-1]
    chunks, main, hidden_parts_keys = {}, None, None
    for source_tokens in (tokens, *extra_sources):
        is_extra = source_tokens is not tokens
        for index in range(len(source_tokens) - 2):
            if [token.value for token in source_tokens[index:index + 2]] != ["static", "let"]:
                continue
            name = source_tokens[index + 2].value
            if is_extra and name != "hiddenPartsKeys" and name.startswith(("hiddenPrivacy", "hiddenParts", "hiddenLocks")) and not name.endswith(language_suffix):
                continue
            equals = index + 3
            while source_tokens[equals].value != "=":
                equals += 1
            start = equals + 1
            if source_tokens[start].value == "{" and source_tokens is tokens:
                end = matching(source_tokens, start)
                if main is not None:
                    raise Unsupported(f"multiple translation builders in {path.name}")
                main = source_tokens[start + 1:end]
            elif source_tokens[start].value == "[":
                end = matching(source_tokens, start)
                if name == "hiddenPartsKeys":
                    hidden_parts_keys = [literal(part) for part in split(source_tokens[start + 1:end])]
                    continue
                entries = {}
                for part in split(source_tokens[start + 1:end]):
                    halves = split(part, ":")
                    if len(halves) != 2:
                        raise Unsupported(f"unsupported translation row in {path.name}:{part[0].line}")
                    key = literal(halves[0])
                    if key in entries:
                        raise Unsupported(f"duplicate dictionary key {key} in {path.name}")
                    entries[key] = (literal(halves[1]), halves[0][0].line)
                chunks[name] = entries
            elif source_tokens[start].kind == "code" and source_tokens[start].value == "makeHiddenParts":
                call_open = start + 1
                if source_tokens[call_open].value != "(":
                    raise Unsupported(f"unsupported translation initializer in {path.name}")
                call_end = matching(source_tokens, call_open)
                args = split(source_tokens[call_open + 1:call_end])
                if len(args) != 1 or len(args[0]) < 2 or args[0][0].value != "[":
                    raise Unsupported(f"unsupported makeHiddenParts initializer in {path.name}")
                if hidden_parts_keys is None:
                    raise Unsupported(f"missing hiddenPartsKeys in {path.name}")
                values = [literal(part) for part in split(args[0][1:-1])]
                if len(hidden_parts_keys) != len(values):
                    raise Unsupported(f"hiddenPartsKeys count mismatch in {path.name}")
                chunks[name] = dict(zip(hidden_parts_keys, [(value, source_tokens[start].line) for value in values]))
            elif source_tokens is tokens:
                raise Unsupported(f"unsupported translation initializer in {path.name}")
    if main is None:
        raise Unsupported(f"no translation builder in {path.name}")
    table, used, index = {}, set(), 0
    while index < len(main):
        values = [t.value for t in main[index:index + 5]]
        if values[:3] == ["var", "table", "="]:
            chunk = main[index + 3].value
            table.update(chunks[chunk])
            used.add(chunk)
            index += 4
        elif values[:4] == ["table", ".", "merge", "("]:
            end = matching(main, index + 3)
            chunk = "".join(token.value for token in main[index + 4:end])
            if chunk.startswith("Translations."):
                chunk = chunk[len("Translations."):]
            if not chunk or "," in chunk:
                raise Unsupported("unsupported table.merge arguments")
            closure_end = matching(main, end + 1)
            if [t.value for t in main[end + 2:closure_end]] != ["_", ",", "new", "in", "new"]:
                raise Unsupported("unsupported table.merge conflict policy")
            table.update(chunks[chunk])
            used.add(chunk)
            index = closure_end + 1
        elif values[:2] == ["table", "["]:
            end = matching(main, index + 1)
            key = literal(main[index + 2:end])
            if main[end + 1].value != "=":
                raise Unsupported("unsupported translation override")
            stop = end + 2
            while stop < len(main) and main[stop].value not in ("table", "return"):
                stop += 1
            table[key] = (literal(main[end + 2:stop]), main[index].line)
            index = stop
        elif values[:2] == ["return", "table"] and index + 2 == len(main):
            index += 2
        else:
            raise Unsupported(f"unsupported translation builder at {path.name}:{main[index].line}")
    if used != set(chunks):
        raise Unsupported(f"unmerged translation chunks in {path.name}: {set(chunks) - used}")
    return table


class Generator:
    def __init__(self, ios):
        self.ios = ios
        self.resources = {}
        self.manual = []
        self.inventory = []
        self.sources = {}
        self.tables = {}
        self.used_keys = set()
        self.fallbacks = {lang: [] for lang in LANGUAGES}
        self.source_functions = {}

    def source(self, path):
        relative = path.relative_to(self.ios).as_posix()
        data = path.read_bytes()
        self.sources[relative] = hashlib.sha256(data).hexdigest()
        try:
            return relative, lex(data.decode("utf-8-sig"))
        except Unsupported as error:
            raise Unsupported(f"{relative}: {error}") from error

    def issue(self, source, line, name, reason):
        item = dict(file=source, line=line, name=name, reason=reason)
        if item not in self.manual:
            self.manual.append(item)

    def add(self, resource):
        identity = (resource.kind, resource.name)
        if not re.fullmatch(r"[a-zA-Z_][a-zA-Z_0-9.]*", resource.name):
            raise Unsupported(f"invalid resource name {resource.name}")
        if identity in self.resources:
            old = self.resources[identity]
            if old.values != resource.values or old.integer_tokens != resource.integer_tokens or old.foundation_format != resource.foundation_format:
                raise ResourceConflict(f"conflicting resource {identity}")
        else:
            self.resources[identity] = resource

    def merge_android(self, path):
        """Android copy is complete and strict: never silently fall back to iOS/English."""
        def unique(pairs):
            result = {}
            for key, value in pairs:
                if key in result:
                    raise ValueError(f"duplicate Android override key: {key}")
                result[key] = value
            return result

        data = path.read_bytes()
        overrides = json.loads(data, object_pairs_hook=unique)
        source = "tools/strings/android.json"
        self.sources[source] = hashlib.sha256(data).hexdigest()
        for name, entry in overrides.items():
            if not isinstance(entry, dict) or set(entry) - {"kind", "values", "replace", "integer_tokens", "foundation_format"}:
                raise ValueError(f"{name}: invalid Android override entry")
            kind = entry.get("kind", "string")
            values = entry["values"]
            if kind not in ("string", "plurals") or not isinstance(values, dict) or set(values) != set(LANGUAGES):
                raise ValueError(f"{name}: Android overrides require all 13 languages")
            forms = {}
            for lang, value in values.items():
                forms[lang] = {"text": value} if kind == "string" else value
                expected = {"text"} if kind == "string" else set(CATEGORIES[lang])
                if not isinstance(forms[lang], dict) or set(forms[lang]) != expected or any(not isinstance(text, str) or not text.strip() for text in forms[lang].values()):
                    raise ValueError(f"{name}/{lang}: missing text or plural categories")
            old = self.resources.get((kind, name))
            if entry.get("replace", False) != (old is not None):
                raise ValueError(f"{name}: replace must name an existing iOS resource")
            resource = Resource(name, kind, forms, source, 1, set(entry.get("integer_tokens", [])), entry.get("foundation_format", False))
            schema = placeholder_schema(list(forms["en"].values()), resource.integer_tokens)
            expected_tokens = set(schema)
            expected_signature = signature(format_text(next(iter(forms["en"].values())), schema, resource.foundation_format)) if schema or resource.foundation_format else {}
            for lang, items in forms.items():
                for text in items.values():
                    if set(TOKEN.findall(text)) != expected_tokens:
                        raise ValueError(f"{name}/{lang}: Android placeholder tokens differ from English")
                    if resource.foundation_format and signature(format_text(text, schema, True)) != expected_signature:
                        raise ValueError(f"{name}/{lang}: Android format arguments differ from English")
            if old:
                old_schema = placeholder_schema(list(old.values["en"].values()), old.integer_tokens)
                old_signature = signature(format_text(next(iter(old.values["en"].values())), old_schema, old.foundation_format)) if old_schema or old.foundation_format else {}
                if schema != old_schema or expected_signature != old_signature:
                    raise ValueError(f"{name}: Android override changes the iOS placeholder contract")
                del self.resources[kind, name]
            self.add(resource)
            for lang in LANGUAGES:
                self.fallbacks[lang] = [key for key in self.fallbacks[lang] if key != name and not key.startswith(name + "/")]

    def tr_values(self, positional, named):
        if len(positional) != 2 or set(named) != {"ru", "en"}:
            raise Unsupported("unsupported tr signature")
        key = literal(positional[1])
        values = {lang: literal(named[lang]) for lang in ("en", "ru")}
        for lang, table in self.tables.items():
            values[lang] = table[key][0] if key in table else values["en"]
            if key not in table and key not in self.fallbacks[lang]:
                self.fallbacks[lang].append(key)
        self.used_keys.add(key)
        return key, values

    def evaluate(self, tokens, lang):
        tokens = expression_body(tokens)
        if len(tokens) > 2 and tokens[0].kind == "code" and tokens[0].value in ("tr", "plural") and tokens[1].value == "(" and matching(tokens, 1) == len(tokens) - 1:
            positional, named = arguments(tokens[2:-1])
            if tokens[0].value == "tr":
                return self.tr_values(positional, named)[1][lang]
            forms = {key: self.evaluate(value, lang) for key, value in named.items() if key in ("one", "few", "many", "fraction")}
            if set(named) - {"one", "few", "many", "fraction"} or len(positional) != 2 or not {"one", "many"} <= set(forms):
                raise Unsupported("unsupported plural expression")
            return forms
        return literal(tokens)

    def extract_function(self, function, source, prefix=""):
        extracted = []
        ints = integer_tokens(function)
        tr_calls = list(calls(function.body, "tr")) if not prefix else []
        foundation_format = any(token.value == "format" and token.kind == "code" for token in function.body)
        for start, _, args in tr_calls:
            try:
                key, values = self.tr_values(*args)
                self.add(Resource(key, "string", {lang: {"text": value} for lang, value in values.items()}, source, function.body[start].line, ints, foundation_format))
                extracted.append(key)
            except Unsupported as error:
                self.issue(source, function.body[start].line, function.name, str(error))
        body = expression_body(function.body)
        # A counted sentence can end with the same substitutions as a plain tr.
        if len(body) > 1 and body[0].value == "plural" and body[1].value == "(":
            end = matching(body, 1)
            tail = body[end + 1:]
            index = 0
            try:
                while index < len(tail):
                    if [t.value for t in tail[index:index + 3]] != [".", "replacingOccurrences", "("]:
                        raise Unsupported("non-token plural operation")
                    stop = matching(tail, index + 2)
                    _, named = arguments(tail[index + 3:stop])
                    if set(named) != {"of", "with"} or not TOKEN.fullmatch(literal(named["of"])):
                        raise Unsupported("non-token plural replacement")
                    replacement = "".join(t.value for t in named["with"])
                    if not re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*|String\([A-Za-z_][A-Za-z_0-9]*\)", replacement):
                        raise Unsupported("plural replacement requires logic")
                    index = stop + 1
                body = body[:end + 1]
            except Unsupported:
                pass
        complete = False
        try:
            pick = list(calls(body, "pick")) if prefix else []
            if pick and pick[0][0] == 0 and pick[0][1] == len(body) - 1:
                _, named = pick[0][2]
                if set(named) != set(LANGUAGES):
                    raise Unsupported("incomplete Live Activity language table")
                values = {lang: literal(named[lang]) for lang in LANGUAGES}
            elif body and body[0].value == "switch":
                cases = language_cases(body, LANGUAGES)
                values = {lang: self.evaluate(cases[lang], lang) for lang in LANGUAGES}
            else:
                values = {lang: self.evaluate(body, lang) for lang in LANGUAGES}
            is_plural = any(isinstance(value, dict) for value in values.values())
            values = {lang: plural_categories(value if isinstance(value, dict) else {"one": value, "many": value}, lang) if is_plural else {"text": value} for lang, value in values.items()}
            # A direct tr call already owns its iOS key, which can differ from the function.
            if not (len(tr_calls) == 1 and body[0].value == "tr" and not is_plural):
                self.add(Resource(prefix + function.name, "plurals" if is_plural else "string", values, source, function.line, ints))
                extracted.append(prefix + function.name)
            complete = bool(extracted)
        except Unsupported:
            pass
        # Token substitutions need no Kotlin logic; other wrappers must be reviewed.
        if not complete and extracted and len(tr_calls) == 1 and tr_calls[0][0] == (1 if function.body and function.body[0].value == "return" else 0):
            _, end, _ = tr_calls[0]
            tail = function.body[end + 1:]
            index = 0
            try:
                while index < len(tail):
                    if [t.value for t in tail[index:index + 3]] != [".", "replacingOccurrences", "("]:
                        raise Unsupported("non-token operation")
                    stop = matching(tail, index + 2)
                    _, named = arguments(tail[index + 3:stop])
                    if set(named) != {"of", "with"} or not TOKEN.fullmatch(literal(named["of"])):
                        raise Unsupported("non-token replacement")
                    replacement = "".join(t.value for t in named["with"])
                    if not re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*|String\([A-Za-z_][A-Za-z_0-9]*\)", replacement):
                        raise Unsupported("replacement requires formatting or logic")
                    index = stop + 1
                complete = True
            except Unsupported:
                pass
        if not complete:
            reasons = []
            if any(token.interpolated for token in function.body):
                reasons.append("Swift interpolation")
            if any(token.value in ("format", "NumberFormatter", "DateFormatter") and token.kind == "code" for token in function.body):
                reasons.append("formatting code")
            reasons.append("port function logic; static tr templates extracted where available")
            self.issue(source, function.line, prefix + function.name, "; ".join(reasons))
        self.inventory.append(dict(file=source, line=function.line, function=prefix + function.name, complete=complete, resources=sorted(set(extracted))))

    def collect(self):
        source_functions = self.source_functions
        localization = self.ios / "TripTrack/Localization"
        for lang in LANGUAGES[2:]:
            path = localization / f"Translations/Translations+{lang.upper()}.swift"
            self.source(path)
            self.tables[lang] = read_translations(path)
        self.source(localization / "Translations/Translations+HiddenPrivacy.swift")
        self.source(localization / "Translations/Translations+HiddenLocks.swift")
        paths = sorted(path for path in localization.glob("*.swift") if path.name.startswith("AppStrings") or "extension AppStrings" in path.read_text(encoding="utf-8"))
        if not paths:
            raise Unsupported("no AppStrings sources found")
        for path in paths:
            source, tokens = self.source(path)
            for function in functions(tokens):
                self.extract_function(function, source)
                source_functions[function.name] = (source, function)
            for name, line, initializer in bindings(tokens):
                try:
                    value = literal(initializer)
                    self.add(Resource(name, "string", {lang: {"text": value} for lang in LANGUAGES}, source, line))
                except Unsupported as error:
                    self.issue(source, line, name, f"static property: {error}")
        path = self.ios / "TripTrackShared/LiveActivityStrings.swift"
        source, tokens = self.source(path)
        for function in functions(tokens):
            self.extract_function(function, source, "live_")
            source_functions["live_" + function.name] = (source, function)
        from logic_resources import extract_logic
        extract_logic(self, source_functions)
        # iOS: BadgeDefinitions.swift / Badge.title. These dynamic metadata keys
        # have no standalone AppStrings function, but the achievement shelf reads them.
        source, tokens = self.source(self.ios / "TripTrack/Models/BadgeDefinitions.swift")
        for start, _, (_, named) in calls(tokens, "Badge"):
            badge_id = literal(named["id"])
            values = {"ru": {"text": literal(named["titleRu"])},
                      "en": {"text": literal(named["titleEn"])}}
            for lang in LANGUAGES[2:]:
                values[lang] = {"text": self.tables[lang].get(f"badge.{badge_id}.title", (values["en"]["text"], 0))[0]}
            self.add(Resource(f"badge_{badge_id}_title", "string", values, source, tokens[start].line))
            descriptions = {"ru": {"text": literal(named["descriptionRu"])},
                            "en": {"text": literal(named["descriptionEn"])}}
            for lang in LANGUAGES[2:]:
                descriptions[lang] = {"text": self.tables[lang].get(f"badge.{badge_id}.desc", (descriptions["en"]["text"], 0))[0]}
            self.add(Resource(f"badge_{badge_id}_description", "string", descriptions, source, tokens[start].line, foundation_format=True))
        # iOS: DriverRank.title; the rank name is a dynamic translation-table key.
        rank_path = self.ios / "TripTrack/Models/GamificationModels.swift"
        rank_source, rank_tokens = self.source(rank_path)
        rank_start = next(i for i, token in enumerate(rank_tokens) if token.value == "DriverRank")
        rank_brace = next(i for i in range(rank_start, len(rank_tokens)) if rank_tokens[i].value == "{")
        rank_body = rank_tokens[rank_brace + 1:matching(rank_tokens, rank_brace)]
        rank_titles = {}
        for function in functions(rank_body):
            if function.name in ("titleRu", "titleEn"):
                rank_titles[function.name] = {
                    key: literal(value) for key, value in language_cases(function.body, [function.body[i + 2].value for i, token in enumerate(function.body) if token.value == "case"]).items()
                }
        for rank, english in rank_titles["titleEn"].items():
            values = {lang: {"text": self.tables.get(lang, {}).get(
                f"driverRank.{rank}",
                (rank_titles["titleRu"][rank] if lang == "ru" else english, 0)
            )[0]} for lang in LANGUAGES}
            self.add(Resource(f"driver_rank_{rank}", "string", values, rank_source, 91))
        permissions = {}
        for lang in LANGUAGES:
            path = self.ios / f"TripTrack/Resources/{lang}.lproj/InfoPlist.strings"
            source, tokens = self.source(path)
            for part in split(tokens, ";"):
                halves = split(part, "=")
                if len(halves) != 2:
                    raise Unsupported(f"unsupported permission entry {source}:{part[0].line}")
                key = literal(halves[0])
                if (key, lang) in permissions:
                    raise Unsupported(f"duplicate permission {key}/{lang}")
                permissions[key, lang] = (literal(halves[1]), source, part[0].line)
        for key in sorted({key for key, _ in permissions}):
            if (key, "en") not in permissions:
                raise Unsupported(f"permission lacks English default: {key}")
            _, source, line = permissions[key, "en"]
            values = {}
            for lang in LANGUAGES:
                if (key, lang) not in permissions:
                    self.fallbacks[lang].append("permission_" + key)
                values[lang] = {"text": permissions.get((key, lang), permissions[key, "en"])[0]}
            self.add(Resource("permission_" + key, "string", values, source, line))
        for lang, table in self.tables.items():
            for key, (_, line) in table.items():
                if key not in self.used_keys:
                    self.issue(f"TripTrack/Localization/Translations/Translations+{lang.upper()}.swift", line, key, f"{lang}: translation has no extractable English tr key; inspect manual function or stale table row")

    def render(self):
        output = {}
        records = []
        rendered = {lang: [] for lang in LANGUAGES}
        for (_, _), resource in sorted(self.resources.items()):
            english = resource.values["en"]
            schema_texts = list(english.values())
            if resource.language_placeholders:
                schema_texts += [text for forms in resource.values.values() for text in forms.values()]
            schema = placeholder_schema(schema_texts, resource.integer_tokens)
            formatted = bool(schema) or resource.foundation_format
            values = {}
            for lang in LANGUAGES:
                converted = {}
                for category, text in resource.values[lang].items():
                    fallback = english.get(category, english.get("other", english.get("text")))
                    expected_tokens = set(TOKEN.findall(fallback))
                    try:
                        if not resource.language_placeholders and set(TOKEN.findall(text)) != expected_tokens:
                            raise Unsupported("placeholder tokens differ from English")
                        normalized = format_text(text, schema, resource.foundation_format)
                        if formatted and not resource.language_placeholders and signature(normalized) != signature(format_text(fallback, schema, resource.foundation_format)):
                            raise Unsupported("format arguments differ from English")
                    except (Unsupported, KeyError) as error:
                        self.issue(resource.source, resource.line, resource.name, f"{lang}/{category}: {error}; English fallback emitted")
                        normalized = format_text(fallback, schema, resource.foundation_format)
                        self.fallbacks[lang].append(f"{resource.name}/{category}")
                    converted[category] = normalized
                values[lang] = converted
                if resource.kind == "string":
                    attribute = '' if formatted else ' formatted="false"'
                    if resource.language_placeholders:
                        attribute += ' tools:ignore="StringFormatCount"'
                    rendered[lang].append(f'    <string name="{resource.name}"{attribute}>{android_escape(converted["text"])}</string>')
                else:
                    # iOS returns counted words separately; the caller supplies the visible count.
                    ignored = "ImpliedQuantity,StringFormatCount" if resource.language_placeholders else "ImpliedQuantity"
                    rendered[lang].append(f'    <plurals name="{resource.name}" tools:ignore="{ignored}">')
                    for category, value in converted.items():
                        rendered[lang].append(f'        <item quantity="{category}">{android_escape(value)}</item>')
                    rendered[lang].append("    </plurals>")
            records.append(dict(name=resource.name, kind=resource.kind, file=resource.source, line=resource.line, placeholders=schema, formatted=formatted,
                                signatures={cat: signature(value) if formatted else {} for cat, value in values["en"].items()}))
            if resource.language_placeholders:
                records[-1]["language_signatures"] = {
                    lang: {cat: signature(value) if formatted else {} for cat, value in forms.items()}
                    for lang, forms in values.items()
                }
        for lang in LANGUAGES:
            output[f"res/{DIRECTORIES[lang]}/strings.xml"] = '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/strings/generate.py. Edit iOS sources or tools/strings/android.json. -->\n<resources xmlns:tools="http://schemas.android.com/tools">\n' + "\n".join(rendered[lang]) + "\n</resources>\n"
        output["res/xml/locales_config.xml"] = '<?xml version="1.0" encoding="utf-8"?>\n<locale-config xmlns:android="http://schemas.android.com/apk/res/android">\n' + "".join(f'    <locale android:name="{lang}" />\n' for lang in LANGUAGES) + "</locale-config>\n"
        counts = Counter(resource.kind for resource in self.resources.values())
        manifest = dict(sources=self.sources, languages=list(LANGUAGES), directories=DIRECTORIES, categories=CATEGORIES,
                        counts={lang: dict(strings=counts["string"], plurals=counts["plurals"], english_fallbacks=len(set(self.fallbacks[lang]))) for lang in LANGUAGES},
                        fallbacks={lang: sorted(set(keys)) for lang, keys in self.fallbacks.items()},
                        functions=self.inventory, resources=records,
                        manual=sorted(self.manual, key=lambda item: (item["file"], item["line"], item["name"], item["reason"])))
        from facade import render_facade, render_token_rules
        if self.source_functions:
            output["kotlin/ResourceAppStrings.kt"] = render_facade(self, self.source_functions)
            output["kotlin/ResourceTokenRules.kt"] = render_token_rules(self)
        output["tools/manifest.json"] = json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
        lines = ["# Ручной порт строк", "", "Список обновляет `generate.py`. Kotlin-тест проверяет наличие каждой функции или явную причину пропуска. Шаблоны `tr` уже выгружены, если их удалось разобрать.", "", "| Файл:строка | Функция / ключ | Причина ручного порта / пропуска |", "|---|---|---|"]
        skipped = json.loads((Path(__file__).resolve().parent / "porting-status.json").read_text())
        for item in manifest["manual"]:
            reason = skipped.get(item["name"])
            if "/Translations/" in item["file"]:
                reason = "Intentionally skipped: translation-table key, not a standalone Swift function; dynamic metadata is outside this facade."
            if reason:
                item = dict(item, reason=reason)
            lines.append(f'| `{item["file"]}:{item["line"]}` | `{item["name"]}` | {item["reason"]} |')
        output["tools/manual-porting.md"] = "\n".join(lines) + "\n"
        return output, manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ios", type=Path, default=Path.home() / "OneZeeProjects/trip-track")
    parser.add_argument("--res", type=Path, default=ROOT / "core/designsystem/src/main/res")
    parser.add_argument("--metadata", type=Path, default=Path(__file__).resolve().parent)
    parser.add_argument("--android", type=Path, default=Path(__file__).resolve().with_name("android.json"))
    parser.add_argument("--check", action="store_true", help="compare generated files without writing")
    args = parser.parse_args()
    try:
        generator = Generator(args.ios.expanduser().resolve())
        generator.collect()
        generator.merge_android(args.android)
        output, manifest = generator.render()
        changed = []
        for name, content in output.items():
            base, relative = name.split("/", 1)
            path = (args.res if base == "res" else ROOT / "core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings" if base == "kotlin" else args.metadata) / relative
            if not path.exists() or path.read_text(encoding="utf-8") != content:
                changed.append(str(path))
                if not args.check:
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text(content, encoding="utf-8")
        if args.check and changed:
            print("Stale generated files:\n" + "\n".join(changed), file=sys.stderr)
            return 1
        print(json.dumps(dict(counts=manifest["counts"], manual=len(manifest["manual"])), ensure_ascii=False, indent=2))
        return 0
    except (Unsupported, OSError, KeyError, ValueError) as error:
        print(f"Generation failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
