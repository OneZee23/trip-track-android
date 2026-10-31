"""Parser regressions and resource parity. iOS: LocalizationTests.swift."""

from pathlib import Path
import json
import tempfile
import unittest
import xml.etree.ElementTree as ET

from generate import (CATEGORIES, DIRECTORIES, LANGUAGES, ROOT, Generator, Resource,
                      android_escape, format_text, placeholder_schema,
                      plural_categories, read_translations, signature)
from swift import Unsupported, bindings, calls, functions, language_cases, lex, literal
from validate import android_decode, read_resources, validate


class SwiftParserTests(unittest.TestCase):
    def test_escapes_unicode_and_concatenation(self):
        source = r'"A\n\t\r\"\\\u{1F680}" + " & < \u{0436}"'
        self.assertEqual(literal(lex(source)), 'A\n\t\r"\\🚀 & < ж')
        self.assertEqual(literal(lex(r'"\UD83D\UDE80"')), "🚀")

    def test_multiline_and_continuation(self):
        self.assertEqual(literal(lex('"""\n    first\n      second\n    """')), "first\n  second")
        self.assertEqual(literal(lex('"""\n    first\\\n    second\n    """')), "firstsecond")

    def test_raw_strings_and_interpolation(self):
        self.assertEqual(literal(lex(r'#"\n \#n"#')), "\\n \n")
        self.assertEqual(literal(lex(r'"\\(literal)"')), "\\(literal)")
        for source in (r'"\(value)"', r'#"\#(value)"#', r'"\(tr(lang, "nested", ru: "р", en: "e"))"'):
            with self.subTest(source=source), self.assertRaises(Unsupported):
                literal(lex(source))

    def test_comments_and_string_braces_do_not_end_functions(self):
        source = '''// static func ghost() { }
        extension AppStrings {
            /* { /* nested */ } */
            static func real(_ lang: Language) -> String {
                tr(lang, "key", ru: "}", en: "// { /*")
            }
        }'''
        parsed = list(functions(lex(source)))
        self.assertEqual([function.name for function in parsed], ["real"])
        self.assertEqual(parsed[0].line, 4)
        self.assertEqual(parsed[0].body[-1].value, ")")

    def test_static_string_property_and_unconvertible_property_are_accounted(self):
        source = '''extension AppStrings {
            static let actor = "Trip" +
                "Track"
            static var computed: String { value() }
        }'''
        parsed = list(bindings(lex(source)))
        self.assertEqual(literal(parsed[0][2]), "TripTrack")
        self.assertEqual(parsed[1][:2], ("computed", 4))
        self.assertEqual(parsed[1][2], [])

    def test_switch_keywords_inside_strings_are_text(self):
        cases = language_cases(lex('switch lang { case .en: return "case" case .ru: return "default" }'), ("en", "ru"))
        self.assertEqual(literal(cases["en"]), "case")
        self.assertEqual(literal(cases["ru"]), "default")

    def test_unknown_expressions_and_escapes_are_rejected(self):
        with self.assertRaises(Unsupported):
            literal(lex('"hello" + value'))
        with self.assertRaises(Unsupported):
            lex(r'"\q"')
        with self.assertRaises(Unsupported):
            lex('/* unclosed')

    def test_translation_merge_precedence_and_override(self):
        source = '''extension Translations {
            static let de: [String: String] = {
                var table = later
                table.merge(earlier) { _, new in new }
                table["override"] = "final" + " value"
                return table
            }()
            private static let earlier: [String: String] = [
                "same": "new", "override": "old", "unicode": "\\u{1F680}"
            ]
            private static let later: [String: String] = ["same": "first"]
        }'''
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "Translations+DE.swift"
            path.write_text(source, encoding="utf-8")
            table = read_translations(path)
            self.assertEqual(table["same"][0], "new")
            self.assertEqual(table["override"][0], "final value")
            self.assertEqual(table["unicode"][0], "🚀")
            path.write_text(source.replace("_, new in new", "old, _ in old"), encoding="utf-8")
            with self.assertRaises(Unsupported):
                read_translations(path)


class GeneratorTests(unittest.TestCase):
    def merge_android(self, generator, entries):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "android.json"
            path.write_text(json.dumps(entries, ensure_ascii=False), encoding="utf-8")
            generator.merge_android(path)

    def test_android_override_replaces_ios_copy_and_records_its_source(self):
        generator = self.generator()
        generator.add(Resource("themePickerFootnote", "string", {lang: {"text": "iPhone"} for lang in LANGUAGES}, "fixture.swift", 1))
        self.merge_android(generator, {"themePickerFootnote": {"replace": True, "values": {lang: "Android" for lang in LANGUAGES}}})
        resource = generator.resources["string", "themePickerFootnote"]
        self.assertEqual(resource.values["ru"]["text"], "Android")
        self.assertEqual(resource.source, "tools/strings/android.json")

    def test_android_requires_all_languages_without_english_fallback(self):
        for missing in LANGUAGES:
            with self.subTest(missing=missing), self.assertRaisesRegex(ValueError, "13 languages"):
                self.merge_android(self.generator(), {"android_test": {"values": {lang: "Text" for lang in LANGUAGES if lang != missing}}})

    def test_android_rejects_missing_tokens_and_accidental_replacements(self):
        values = {lang: "{name}" for lang in LANGUAGES}
        values["uk"] = "Текст"
        with self.assertRaisesRegex(ValueError, "placeholder tokens"):
            self.merge_android(self.generator(), {"android_test": {"values": values}})
        with self.assertRaisesRegex(ValueError, "replace"):
            self.merge_android(self.generator(), {"unknown_ios_key": {"replace": True, "values": {lang: "Text" for lang in LANGUAGES}}})
        generator = self.generator()
        generator.add(Resource("sample", "string", {lang: {"text": "{name}"} for lang in LANGUAGES}, "fixture.swift", 1))
        with self.assertRaisesRegex(ValueError, "placeholder contract"):
            self.merge_android(generator, {"sample": {"replace": True, "values": {lang: "{other}" for lang in LANGUAGES}}})

    def test_android_diagnostics_preserve_positional_numeric_formats(self):
        values = {lang: "%1$s: %2$d, %3$.2f ms" for lang in LANGUAGES}
        generator = self.generator()
        self.merge_android(generator, {"android_stats": {"foundation_format": True, "values": values}})
        output, _ = generator.render()
        self.assertIn("%3$.2f ms", output["res/values-ru/strings.xml"])
        values["ru"] = "%1$s: %2$s, %3$.2f ms"
        with self.assertRaisesRegex(ValueError, "format arguments"):
            self.merge_android(self.generator(), {"android_stats": {"foundation_format": True, "values": values}})
        generator.add(Resource("ios_count", "string", {lang: {"text": "%d"} for lang in LANGUAGES}, "fixture.swift", 1, foundation_format=True))
        with self.assertRaisesRegex(ValueError, "placeholder contract"):
            self.merge_android(generator, {"ios_count": {"replace": True, "foundation_format": True, "values": {lang: "%s" for lang in LANGUAGES}}})

    def test_android_plurals_require_russian_and_ukrainian_forms(self):
        from generate import CATEGORIES
        values = {lang: {category: "trip" for category in CATEGORIES[lang]} for lang in LANGUAGES}
        values["ru"] = dict(one="поездка", few="поездки", many="поездок", other="поездки")
        values["uk"] = dict(one="поїздка", few="поїздки", many="поїздок", other="поїздки")
        generator = self.generator()
        self.merge_android(generator, {"android_trips": {"kind": "plurals", "values": values}})
        self.assertEqual(generator.resources["plurals", "android_trips"].values["uk"]["many"], "поїздок")
        del values["uk"]["few"]
        with self.assertRaisesRegex(ValueError, "plural categories"):
            self.merge_android(self.generator(), {"android_trips": {"kind": "plurals", "values": values}})

    def generator(self):
        generator = Generator(Path("unused"))
        generator.tables = {lang: {} for lang in LANGUAGES[2:]}
        return generator

    def test_missing_translation_falls_back_to_english(self):
        # iOS: LocalizationTests.testMissingKeyFallsBackToEnglish
        generator = self.generator()
        call = list(functions(lex('static func sample(_ lang: Language) -> String { tr(lang, "no-such-key-exists", ru: "русский", en: "english") }')))[0]
        _, _, args = list(calls(call.body, "tr"))[0]
        self.assertEqual(generator.tr_values(*args)[1]["de"], "english")

    def test_interpolated_tr_is_never_marked_complete(self):
        generator = self.generator()
        function = list(functions(lex(r'static func sample(_ lang: Language) -> String { tr(lang, "sample", ru: "\(value)", en: "hello") }')))[0]
        generator.extract_function(function, "fixture.swift")
        self.assertFalse(generator.inventory[0]["complete"])
        self.assertTrue(generator.manual)

    def test_tr_concatenation_and_integer_token_inference(self):
        source = '''static func sample(_ lang: Language, count: Int) -> String {
            tr(lang, "sample", ru: "{n} " + "раз", en: "{n} times")
                .replacingOccurrences(of: "{n}", with: String(count))
        }'''
        generator = self.generator()
        generator.extract_function(list(functions(lex(source)))[0], "fixture.swift")
        resource = generator.resources["string", "sample"]
        self.assertEqual(resource.values["ru"]["text"], "{n} раз")
        self.assertEqual(resource.integer_tokens, {"n"})
        self.assertTrue(generator.inventory[0]["complete"])

    def test_complex_function_keeps_template_and_manual_location(self):
        source = '''static func sample(_ lang: Language, n: Int) -> String {
            let value = tr(lang, "sample", ru: "{n} раз", en: "{n} times")
            return "\\(n) " + value
        }'''
        generator = self.generator()
        generator.extract_function(list(functions(lex(source)))[0], "fixture.swift")
        self.assertIn(("string", "sample"), generator.resources)
        self.assertFalse(generator.inventory[0]["complete"])
        self.assertEqual(generator.manual[0]["file"], "fixture.swift")
        self.assertEqual(generator.manual[0]["line"], 1)

    def test_plural_sentence_with_token_substitution(self):
        source = '''static func count(_ lang: Language, count: Int) -> String {
            plural(lang, count,
                one: tr(lang, "countOne", ru: "{n} раз", en: "{n} time"),
                few: tr(lang, "countFew", ru: "{n} раза", en: "{n} times"),
                many: tr(lang, "countMany", ru: "{n} раз", en: "{n} times"))
                .replacingOccurrences(of: "{n}", with: String(count))
        }'''
        generator = self.generator()
        generator.extract_function(list(functions(lex(source)))[0], "fixture.swift")
        self.assertEqual(generator.resources["plurals", "count"].values["ru"]["few"], "{n} раза")
        self.assertEqual(generator.resources["plurals", "count"].integer_tokens, {"n"})
        self.assertTrue(generator.inventory[0]["complete"])

    def test_placeholder_indices_survive_reordering_and_repetition(self):
        schema = placeholder_schema(["{name}: {n} / {name}"], {"n"})
        self.assertEqual(format_text("{n} von {name} ({name}) 50%", schema), "%2$d von %1$s (%1$s) 50%%")
        self.assertEqual(signature(format_text("{n} von {name}", schema)), {1: "s", 2: "d"})

    def test_literal_percent_is_not_a_formatter(self):
        self.assertEqual(format_text("50% faster", {}), "50% faster")
        self.assertEqual(format_text("{name}: 50% faster", placeholder_schema(["{name}"], set())), "%1$s: 50%% faster")
        self.assertEqual(format_text("{name}: %%", placeholder_schema(["{name}"], set())), "%1$s: %%%%")
        self.assertEqual(format_text("last %@ %d %.1f%%", {}, True), "last %1$s %2$d %3$.1f%%")

    def test_unsafe_translation_is_reported_and_falls_back(self):
        generator = self.generator()
        values = {lang: {"text": "{name}"} for lang in LANGUAGES}
        values["de"]["text"] = "{wrong}"
        generator.add(Resource("test", "string", values, "fixture.swift", 7))
        output, manifest = generator.render()
        self.assertIn("%1$s", output["res/values-de/strings.xml"])
        self.assertEqual(manifest["manual"][0]["line"], 7)
        self.assertIn("test/text", manifest["fallbacks"]["de"])

    def test_fraction_forms_and_two_form_fallback(self):
        self.assertEqual(plural_categories({"one": "фут", "few": "фути", "many": "футів", "fraction": "фута"}, "uk")["other"], "фута")
        forms = plural_categories({"one": "Fahrt", "many": "Fahrten"}, "de")
        self.assertEqual(forms, {"one": "Fahrt", "other": "Fahrten"})
        self.assertEqual(plural_categories({"one": "perjalanan", "many": "perjalanan"}, "id"), {"other": "perjalanan"})


class ResourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.res = ROOT / "core/designsystem/src/main/res"
        cls.resources = {lang: read_resources(cls.res / directory / "strings.xml") for lang, directory in DIRECTORIES.items()}

    def text(self, lang, key):
        return self.resources[lang]["string", key][0]["text"]

    def test_all_generated_resources_validate(self):
        self.assertEqual(set(validate(self.res, Path(__file__).resolve().parent)), set(LANGUAGES))

    def test_translation_values_from_ios_tests(self):
        # iOS: LocalizationTests.testTranslationsAreActuallyReached
        for lang, expected in (("de", "Abbrechen"), ("es", "Cancelar"), ("fr", "Annuler"), ("it", "Annulla"), ("pl", "Anuluj")):
            self.assertEqual(self.text(lang, "cancel"), expected)

    def test_plural_words_from_ios_tests(self):
        # iOS: LocalizationTests, same counts and expected words. Categories
        # are fixture inputs; runtime ICU selection belongs to the Android app.
        cases = {
            "ru": [(1, "one", "поездка"), (2, "few", "поездки"), (5, "many", "поездок"), (11, "many", "поездок"), (21, "one", "поездка"), (22, "few", "поездки"), (111, "many", "поездок")],
            "fr": [(0, "one", "trajet"), (2, "other", "trajets")],
            "id": [(0, "other", "perjalanan"), (1, "other", "perjalanan"), (5, "other", "perjalanan")],
            "pl": [(1, "one", "trasa"), (2, "few", "trasy"), (5, "many", "tras"), (21, "many", "tras"), (22, "few", "trasy")],
        }
        for lang, examples in cases.items():
            forms = self.resources[lang]["plurals", "nounTrips"][0]
            for count, category, expected in examples:
                with self.subTest(lang=lang, count=count):
                    self.assertEqual(forms[category], expected)

    def test_region_date_token_and_live_activity_from_ios_tests(self):
        # iOS: testRegionSinceCarriesTheDateTokenInEveryLanguage,
        # testLiveActivityStringsCoverEveryLanguage.
        for lang in LANGUAGES:
            self.assertEqual(self.text(lang, "mapRegionSince").count("%1$s"), 1)
            self.assertTrue(self.text(lang, "live_paused"))
            self.assertTrue(self.text(lang, "live_speedCaption"))
        self.assertEqual(self.text("de", "live_paused"), "Pausiert")

    def test_android_escape_roundtrip(self):
        for text in ("it's \"quoted\" & <tag>", "@reference", "?attr", "  a  b  ", "a\nb\tc\rd", r"a\b", "50% faster", "🚀"):
            encoded = android_escape(text)
            decoded_xml = ET.fromstring("<string>" + encoded + "</string>").text
            self.assertEqual(android_decode(decoded_xml), text)
        for unsafe in ("@reference", "?attr", "it's", '"quote"', "a  b", "\\q"):
            with self.subTest(unsafe=unsafe), self.assertRaises(ValueError):
                android_decode(unsafe)

    def test_validator_rejects_missing_keys_and_changed_placeholders(self):
        metadata = Path(__file__).resolve().parent
        with tempfile.TemporaryDirectory() as temporary:
            res = Path(temporary) / "res"
            for file in self.res.rglob("*.xml"):
                target = res / file.relative_to(self.res)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(file.read_bytes())
            target = res / "values-de/strings.xml"
            original = target.read_text(encoding="utf-8")
            target.write_text(original.replace('name="cancel"', 'name="cancel_missing"'), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "key coverage"):
                validate(res, metadata)
            target.write_text(original.replace("%1$s", "%9$s", 1), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "placeholders"):
                validate(res, metadata)


if __name__ == "__main__":
    unittest.main()
