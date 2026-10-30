#!/usr/bin/env python3
"""Run original Swift string functions on macOS to refresh Android parity fixtures.

iOS: LocalizationTests.swift, UnitLabelTests.swift, MeasureFormatTests.swift.
Only linkage types are stubbed; string functions and Foundation formatters run unchanged.
"""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import tempfile

from swift import functions, lex, split

ROOT = Path(__file__).resolve().parents[2]
SKIP = {'tr', 'plural', 'pluralForm', 'patterns', 'templates', 'table', 'uppercased', 'lowercased',
        'ukrainianOrdinalFeminine', 'capitalizedFirst', 'accountAppleIdLine', 'notifAutoStartFailedBody',
        'live_pick', 'live_slavic'}


def source_function(path, name, occurrence=0):
    text = path.read_text()
    fn = [f for f in functions(lex(text)) if f.name == name][occurrence]
    return '\n'.join(text.splitlines()[fn.line - 1:max(t.line for t in fn.body) + 1])


def generate(ios):
    localization = ios / 'TripTrack/Localization'
    language = (localization / 'LanguageManager.swift').read_text()
    language = language[language.index('    enum Language:'):language.index('\n    static var currentLanguage:')]
    showcase = ios / 'TripTrack/Views/Plus/ProShowcase.swift'
    badge = ios / 'TripTrack/Models/Badge.swift'
    stats = ios / 'TripTrack/Views/Profile/StatsScreenView.swift'
    stubs = 'import Foundation\nenum LanguageManager {\n' + language + '\n}\n'
    stubs += '''enum TripJunkClassifier { enum Reason { case tooShort, noDrivingSpeed } }
    enum MapViewModel { enum StartRefusal { case locationDenied, recoveryPending, noFix, unknown } }
    enum FuelCurrency { static let current = "€" }
    enum DiscoveryKind { case secret, riddle, milestone }
    enum RiddleType { case pass, lighthouse, border, ferry, dam, bridge, viewpoint, observatory, seaRoad, extreme, centre, tripoint }
    enum Milestone { case firstRegion, easternmost, westernmost, northernmost, southernmost, above2000, belowSea, threeRegionsDay, countryBorder, nightPass }
    enum ProShowcaseKind { case profileBackground, avatarFrame, vehicleCard, routeLine
    ''' + source_function(showcase, 'title') + '\n}\n'
    stubs += 'enum BadgeRarity { case common, uncommon, rare, epic, legendary, exclusive\n'
    stubs += '\n'.join(source_function(badge, n, i) for n, i in [('titleRu', 1), ('titleEn', 1), ('title', 1)]) + '\n}\n'
    stubs += '''enum StatsPeriodFormat {
        private static let genitiveMonths = LocalizedDateFormatter.patterns("MMMM")
        private static let monthYearInlineFmt = LocalizedDateFormatter.templates("MMMMyyyy")
        private static let ruLocale = LanguageManager.Language.ru.locale
        private static func yearText(_ date: Date) -> String { String(Calendar.current.component(.year, from: date)) }
    '''
    stubs += source_function(stats, 'monthYearInline') + '\n' + source_function(stats, 'monthYearGenitive') + '\n}\n'

    manifest = json.loads((ROOT / 'tools/strings/manifest.json').read_text())
    calls, seen = [], set()
    source_functions = {}
    for file in {i['file'] for i in manifest['functions']}:
        for fn in functions(lex((ios / file).read_text())):
            source_functions[file, fn.line] = fn
    for item in manifest['functions']:
        name = item['function']
        if item['complete'] or name in SKIP:
            continue
        fn = source_functions[item['file'], item['line']]
        params = fn.signature[:next(i for i, t in enumerate(fn.signature) if t.value == '->') - 1]
        parsed = []
        for part in split(params):
            colon = next(i for i, t in enumerate(part) if t.value == ':')
            parsed.append((part[0].value, part[colon - 1].value, ''.join(t.value for t in part[colon + 1:])))
        # Counts exercise East Slavic/Polish distinctions, zero singular, and grouped numbers.
        for sample in (0, 1, 2, 5, 11, 21, 22, 12345):
            arguments, values = [], []
            for label, param, kind in parsed:
                if kind == 'LanguageManager.Language' or name.startswith('live_') and param == 'c':
                    value = 'l.rawValue' if name.startswith('live_') else 'l'
                else:
                    if kind in ('Int', 'Int?'):
                        raw = None if kind.endswith('?') and sample == 0 else sample
                        value = 'nil' if raw is None else str(raw)
                    elif kind == 'Double':
                        raw = float(sample) + (0.5 if sample == 2 else 0)
                        value = str(raw)
                    elif kind == 'String':
                        raw = {'type': 'vehiclePhoto', 'action': 'unpublish', 'kind': 'dct',
                               'raw': 'few', 'color': 'blue', 'style': 'bicycle'}.get(param, 'Text 50% {name}')
                        value = json.dumps(raw)
                    elif kind == 'String?':
                        raw = None if sample == 0 else '' if sample == 1 else 'Text 50% {name}'
                        value = 'nil' if raw is None else json.dumps(raw)
                    elif kind == 'Bool':
                        raw = sample % 2 == 0
                        value = str(raw).lower()
                    elif kind == 'Date':
                        raw = '2026-05-12T12:00:00Z'
                        value = 'Date(timeIntervalSince1970: 1778587200)'
                    else:
                        cases = {'DistanceUnit': ['km', 'miles'],
                                 'ProShowcaseKind': ['profileBackground', 'avatarFrame', 'vehicleCard', 'routeLine'],
                                 'TripJunkClassifier.Reason': ['tooShort', 'noDrivingSpeed'],
                                 'TripStopReason': ['bluetooth', 'inactivity'],
                                 'BadgeRarity': ['common', 'uncommon', 'rare', 'epic', 'legendary', 'exclusive'],
                                 'DiscoveryKind': ['secret', 'riddle', 'milestone'],
                                 'RiddleType': ['pass', 'tripoint'], 'Milestone': ['firstRegion', 'nightPass']}
                        if kind not in cases:
                            raise ValueError((name, param, kind))
                        raw = cases[kind][sample % len(cases[kind])]
                        value = '.' + raw
                    values.append(raw)
                arguments.append(('' if label == '_' else label + ': ') + value)
            if name == 'pricePerLiter':
                values.append('€')
            signature = json.dumps([name, values], ensure_ascii=False)
            if signature in seen:
                continue
            seen.add(signature)
            owner, method = ('LiveActivityStrings', name[5:]) if name.startswith('live_') else ('AppStrings', name)
            calls.append('emit(l, ' + json.dumps(name) + ', ' + json.dumps(json.dumps(values, ensure_ascii=False), ensure_ascii=False)
                         + ', ' + owner + '.' + method + '(' + ', '.join(arguments) + '))')
    # These values distinguish sequential Foundation substitutions from String.format.
    calls += [
        r'emit(l, "proManageRenews", "[\"{price}\",\"50% {date}\",true]", AppStrings.proManageRenews(l, date: "{price}", price: "50% {date}", yearly: true))',
        r'emit(l, "garageDrive2Valid", "[\"{model}\",\"50% %1$s\"]", AppStrings.garageDrive2Valid(l, make: "{model}", model: "50% %1$s"))',
        r'emit(l, "statsVsPrevious", "[true,false,\"Text\"]", AppStrings.statsVsPrevious(l, more: true, year: false, previous: "Text"))',
        r'emit(l, "statsVsPrevious", "[false,true,\"Text\"]", AppStrings.statsVsPrevious(l, more: false, year: true, previous: "Text"))',
    ]
    main = '''import Foundation
    setenv("TZ", "UTC", 1)
    var rows: [[String: Any]] = []
    func emit(_ l: LanguageManager.Language, _ function: String, _ arguments: String, _ value: String?) {
        rows.append(["language": l.rawValue, "function": function,
                     "arguments": try! JSONSerialization.jsonObject(with: Data(arguments.utf8)),
                     "expected": value as Any? ?? NSNull()])
    }
    for l in LanguageManager.Language.allCases {
    ''' + '\n'.join(calls) + '''
    }
    let data = try! JSONSerialization.data(withJSONObject: rows, options: [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes])
    FileHandle.standardOutput.write(data)
    '''
    sources = sorted(localization.glob('AppStrings*.swift')) + [localization / 'AtlasCopy.swift']
    sources += sorted((localization / 'Translations').glob('*.swift'))
    sources += [ios / 'TripTrackShared/DistanceUnit.swift', ios / 'TripTrackShared/UnitNumber.swift',
                ios / 'TripTrackShared/LiveActivityStrings.swift']
    with tempfile.TemporaryDirectory(prefix='triptrack-ios-strings-') as tmp:
        tmp = Path(tmp)
        (tmp / 'Stubs.swift').write_text(stubs)
        (tmp / 'main.swift').write_text(main)
        subprocess.run(['swiftc', '-module-cache-path', str(tmp / 'cache'), *map(str, sources),
                        str(tmp / 'Stubs.swift'), str(tmp / 'main.swift'), '-o', str(tmp / 'fixtures')], check=True)
        result = subprocess.check_output([str(tmp / 'fixtures')])
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ios', type=Path, default=Path.home() / 'OneZeeProjects/trip-track')
    args = parser.parse_args()
    result = generate(args.ios)
    target = ROOT / 'core/designsystem/src/test/resources/ios/AppStrings.json'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(result + b'\n')
    print(f'Wrote {len(json.loads(result))} Swift fixtures to {target.relative_to(ROOT)}')
