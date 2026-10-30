"""Extract the inline language branches used by the Kotlin logic facade.

iOS: AppStrings.swift and extensions. Specs select expressions, never supply copy.
"""
import re

from swift import (Unsupported, _literal, arguments, language_cases, lex, matching,
                   split)


# A selector is (language switch index, assigned variable or return, resource suffix).
SIMPLE = '''groupsWaitlistCount clubWaitingCount pricePerLiter totalKm earnedTimes
mapKmDriven mapRoadTrips exploredPercent notifTripStartBody badgeLastEarned chartMileMark
chartKmMark signInLegalMarkdown vehicleSoldState publicAboutBy outOf inGarageSince
sinceYear fuelPricePerUnit vehicleLevelToNext vehicleLevelAndAbove activityReactedTo
activityAchievementUnlocked syncUpdatedAgo blockedSince statsKmTotal nameHelper
streakDaysInARow repeatRouteTimes commentReplyingTo companionsAndMore photosAdded
companionPhotoUploadPartial achievementsEarnedBy myProfileAvatarRetired levelsToNextLevel
levelsNextRank levelsXpTotal statsWithTripTrackSince contentFilterTooLong relTimeMinutesAgo
relTimeTodayAt relTimeYesterdayAt relTimeDaysAgo publicProfileTripsTotal'''.split()
SPECS = {name: [(0, 'return', '')] for name in SIMPLE}
SPECS.update({
    'cosmeticRetainedChoice': [(0, 'sentence', '')],
    'recordingDiscardReason': [(0, 'return', 'tooShort'), (1, 'return', 'noDrivingSpeed')],
    'onboardingConsentMarkdown': [(0, 'termsTitle', 'terms'), (0, 'privacyTitle', 'privacy'), (1, 'return', '')],
    'notifTripStopBody': [(0, 'ask', 'ask'), (1, 'tail', 'tail')],
    'activityCommented': [(0, 'return', 'text'), (1, 'return', 'trip')],
    'statsKmByMonth': [(0, 'return', '')],
    'companionsPendingSuffix': [(0, 'return', '')],
    'syncAttemptsLabel': [(0, 'word', 'word')],
    'publishedTripsSignOutMessage': [(0, key, key) for key in ('noun', 'pron', 'head', 'tail')],
    'statsVsPrevious': [(0, 'return', '')],
    'statsNewPlacesLine': [(0, 'noun', 'noun'), (1, 'head', 'head'), (2, 'return', '')],
    'statsRecordBestDay': [(0, 'return', '')],
    'statsMemoriesLine': [(0, 'noun', 'noun'), (0, 'return', '')],
    'unitDistanceShort': [(0, 'return', '')],
    'unitElevationShort': [(0, 'return', '')],
    'unitSpeedShort': [(0, 'return', '')],
})
# Conditions are evaluated at generation time into separate resource variants.
VARIANTS = {
    'statsKmByMonth': [('miles', {'miles': True}), ('km', {'miles': False})],
    'statsRecordBestDay': [('miles', {'unit==.miles': True}), ('km', {'unit==.miles': False})],
    'companionsPendingSuffix': [('one', {'count==1': True}), ('many', {'count==1': False})],
    'statsVsPrevious': [(f'{m}_{y}', {'more': m == 'more', 'year': y == 'year'})
                        for m in ('more', 'less') for y in ('year', 'month')],
}
ALIASES = {
    'ukrainianOrdinalFeminine(n)': 'ordinal',
    'capitalizedFirst(u,lang)': 'capitalizedUnit',
    'tripsCount(lang,n:trips)': 'countedTrips',
    'verb': 'pron',
}


def text_expression(tokens, conditions):
    """Keep interpolation symbolic; only known boolean choices are evaluated."""
    if len(tokens) == 1 and tokens[0].kind == 'string':
        value, output, start = tokens[0].value, [], 0
        while True:
            pos = value.find('\\(', start)
            if pos < 0:
                return ''.join(output) + value[start:]
            output.append(value[start:pos])
            end, depth = pos + 2, 1
            while depth:
                if value[end] == '"':
                    _, end, _ = _literal(value, end)
                    continue
                if value[end] == '(':
                    depth += 1
                elif value[end] == ')':
                    depth -= 1
                end += 1
            expression = value[pos + 2:end - 1]
            parsed = lex(expression)
            if any(t.kind == 'code' and t.value == '?' for t in parsed):
                output.append(text_expression(parsed, conditions))
            else:
                key = ALIASES.get(''.join(t.value for t in parsed), expression)
                if not re.fullmatch(r'[A-Za-z_][A-Za-z_0-9]*', key):
                    raise Unsupported(f'unknown logic interpolation: {expression}')
                output.append('{' + key + '}')
            start = end
    question = next((i for i, t in enumerate(tokens) if t.kind == 'code' and t.value == '?'), None)
    if question is not None:
        condition = ''.join(t.value for t in tokens[:question])
        colon = next(i for i in range(question + 1, len(tokens)) if tokens[i].kind == 'code' and tokens[i].value == ':')
        return text_expression(tokens[question + 1:colon] if conditions[condition] else tokens[colon + 1:], conditions)
    if len(tokens) == 1 and tokens[0].value in ('termsOfService', 'privacyPolicy'):
        raise Unsupported('expected call')
    # Defaults in the legal-link table use existing resources (resolved below).
    if tokens and tokens[0].value in ('termsOfService', 'privacyPolicy'):
        return ('resource', tokens[0].value)
    raise Unsupported('unsupported logic expression: ' + ''.join(t.value for t in tokens))


def selected_expression(tokens, selector):
    if selector == 'return':
        index = next((i for i, t in enumerate(tokens) if t.kind == 'code' and t.value == 'return'), None)
        # language_cases strips an initial return.
        if index is None:
            return tokens
        return tokens[index + 1:]
    aliases = (selector, 'verb') if selector == 'pron' else (selector,)
    for index, token in enumerate(tokens[:-1]):
        if token.kind == 'code' and token.value in aliases and tokens[index + 1].value == '=':
            start, end = index + 2, index + 2
            while end < len(tokens):
                if tokens[end].kind == 'code' and tokens[end].value in (';', 'let', 'return'):
                    break
                # assignments can follow on the next line without a let.
                if end > start and end + 1 < len(tokens) and tokens[end + 1].value == '=':
                    break
                if tokens[end].kind == 'code' and tokens[end].value in ('(', '[', '{'):
                    end = matching(tokens, end) + 1
                else:
                    end += 1
            return tokens[start:end]
    # statsMemoriesLine only has special case nouns for East Slavic languages.
    if selector == 'noun':
        return lex('""')
    raise Unsupported(f'missing assignment {selector}')


def extract_logic(generator, source_functions):
    from generate import LANGUAGES, Resource, plural_categories
    for name, specs in SPECS.items():
        source, function = source_functions[name]
        switches = []
        for index in range(len(function.body) - 2):
            if (function.body[index].value == 'switch' and function.body[index + 1].value in ('lang', 'l')
                    and function.body[index + 2].value == '{'):
                end = matching(function.body, index + 2)
                switches.append(function.body[index:end + 1])
        for number, selector, suffix in specs:
            cases = language_cases(switches[number], LANGUAGES)
            for variant, conditions in VARIANTS.get(name, [('', {})]):
                values = {}
                for lang in LANGUAGES:
                    expression = selected_expression(cases[lang], selector)
                    if expression and expression[0].value == 'plural':
                        _, named = arguments(expression[2:matching(expression, 1)])
                        forms = {key: text_expression(value, conditions) for key, value in named.items() if key in ('one', 'few', 'many', 'fraction')}
                    else:
                        value = text_expression(expression, conditions)
                        if isinstance(value, tuple):
                            value = generator.resources['string', value[1]].values[lang]['text']
                        forms = {'one': value, 'many': value}
                    values[lang] = forms
                base = '_'.join(p for p in ('logic', name, suffix, variant) if p)
                # Explicit visible-fraction forms cannot go through getQuantityString(Int).
                if name in ('unitDistanceShort', 'unitElevationShort'):
                    for form in ('one', 'few', 'many', 'fraction'):
                        words = {lang: {'text': forms.get(form, forms.get('few', forms['many']))}
                                 for lang, forms in values.items()}
                        generator.add(Resource(base + '_' + form, 'string', words, source, function.line))
                else:
                    is_plural = selector in ('noun', 'pron', 'word')
                    converted = {lang: plural_categories(forms, lang) if is_plural else {'text': forms['one']}
                                 for lang, forms in values.items()}
                    resource = Resource(base, 'plurals' if is_plural else 'string', converted, source, function.line)
                    resource.language_placeholders = True
                    generator.add(resource)

    # DistanceUnit+Current.swift owns these keys, outside the original AppStrings scan.
    path = generator.ios / 'TripTrack/Models/DistanceUnit+Current.swift'
    source, tokens = generator.source(path)
    from swift import calls
    for start, _, args in calls(tokens, 'tr'):
        key, values = generator.tr_values(*args)
        generator.add(Resource(key, 'string', {lang: {'text': value} for lang, value in values.items()}, source, tokens[start].line))

    source, function = source_functions['ukrainianOrdinalFeminine']
    suffixes = list(dict.fromkeys(t.value for t in function.body if t.kind == 'string'))
    for index, value in enumerate(suffixes):
        generator.add(Resource(f'logic_ordinal_{index}', 'string', {lang: {'text': value} for lang in LANGUAGES}, source, function.line))

    source, function = source_functions['achievementsRarityCount']
    for match in re.finditer(r'case\s+\.([A-Za-z]+)\s*:\s*word\s*=\s*("[^"\n]*")',
                             '\n'.join((generator.ios / source).read_text().splitlines()[function.line - 1:max(t.line for t in function.body)])):
        value = lex(match[2])[0].value
        generator.add(Resource('logic_rarityCount_' + match[1], 'string',
                               {lang: {'text': value} for lang in LANGUAGES}, source, function.line))

    path = generator.ios / 'TripTrack/Models/Badge.swift'
    source, tokens = generator.source(path)
    from swift import functions, literal
    titles = [f for f in functions(tokens) if f.name in ('titleRu', 'titleEn')]
    ru = language_cases(titles[2].body, ('common', 'uncommon', 'rare', 'epic', 'legendary', 'exclusive'))
    en = language_cases(titles[3].body, tuple(ru))
    for rarity in ru:
        key = 'badgeRarity.' + rarity
        values = {'ru': {'text': literal(ru[rarity])}, 'en': {'text': literal(en[rarity])}}
        for lang in LANGUAGES[2:]:
            values[lang] = {'text': generator.tables[lang].get(key, (values['en']['text'],))[0]}
        generator.used_keys.add(key)
        generator.add(Resource(key, 'string', values, source, titles[2].line))

    # Swift interpolation is one lexer token; nested tr calls still own real resource keys.
    for source, function in source_functions.values():
        for token in function.body:
            if token.kind != 'string' or not token.interpolated:
                continue
            start = 0
            while True:
                pos = token.value.find('\\(', start)
                if pos < 0:
                    break
                end, depth = pos + 2, 1
                while depth:
                    if token.value[end] == '"':
                        _, end, _ = _literal(token.value, end)
                        continue
                    if token.value[end] == '(':
                        depth += 1
                    elif token.value[end] == ')':
                        depth -= 1
                    end += 1
                for _, _, args in calls(lex(token.value[pos + 2:end - 1]), 'tr'):
                    key, values = generator.tr_values(*args)
                    generator.add(Resource(key, 'string', {lang: {'text': value} for lang, value in values.items()}, source, token.line))
                start = end
