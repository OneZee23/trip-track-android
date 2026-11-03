# w12-garage

Wave 3a of the design-parity fixes: the garage (the vehicle page, its pushed pages, the garage list, the
foreign vehicle page, the avatar card of the vehicle form), Настройки with its sub-sheets and the
auto-record screens, and one onboarding detail. The reference is the 3 November 2026 audit
(`differences.md`: "Garage" GA-01…GA-34, "Settings" SE-01…SE-16, "Onboarding" ON-01…ON-03). Clone
`w12-garage`, master at `87bca74` (wave 1 and wave 2a included), not pushed. The iPhone app was only read
(0.9.3 for `AutoRecordSettingsView` and `BluetoothScanSheet`). Numbers are dp on the Android side and
points on the iPhone's; the renders are 411 dp wide against the iPhone's 393 pt.

## Per audit item

| ID | Status | What changed | Before → after |
|---|---|---|---|
| GA-01 tab bar | not mine | Done in wave 1 as SH-09. This wave adds the four new pushed pages (level, trips, map, photos) to `MeRoute.keepsTabBar`, so the bar stays on every garage screen as on the iPhone. | |
| GA-02 nav title | not mine | Done in wave 1 as SH-01. The own vehicle page and the garage list use the shared `CustomNavBar`; the new pages use `GaragePageBar` (centred title with the car's name under it). | |
| GA-03 hero frame | fixed | `VehicleHeroCard`: radius 20; own card 18 across, 22 above, 16 below, the stage 4 under the top and the name 16 under the stage; someone else's 14 across, 18 above and below, the name 12 under the stage. The stage is 0.77 of the screen width, at most 340. | Stage 39.6 dp from the card top, name 4.6 dp under the platform → 26 and 16 (iOS 50 pt from the card top to the sprite, 20.7 pt to the name); foreign 16 → 12 |
| GA-04 model line | fixed | `catalogModelLine`: make and model joined by a space, the body type in lower case after them unless the model names it already, only the year after « · »; 13 regular, 4 under the name. Used by both pages. | «Volkswagen · Polo · 2019» → «Volkswagen Polo седан · 2018» |
| GA-05 spec capsule | fixed | `CarCatalog.Variant.specLine`: «1.6 110 л.с. · АКПП 6 · 0–100 за 11.7 с» (displacement and power, gearbox, 0–100; no top speed) in a card-alt capsule, radius 10, 6 and 10 inside, 12 semibold ink, 10 under the model line. An unknown gearbox is not printed. | Plain 12 regular caption with power, 0–100 and top speed → the capsule |
| GA-06 level pill | fixed | `VehicleLvlPill`: the drawn star and «LVL 6» in Handjet Black 15, gold on the gold wash, 12 before, 20 after (14 and the empty rank text SwiftUI still spaces), 2 above and below; a button when it has a screen (the own page opens the level page). | Inter 11 pill, 29 × 53 dp, no star → the iPhone's pill |
| GA-07 numbers | fixed | Three equal thirds, value 20 bold over an 11 tertiary label, 3 apart; «—» while they are counted and «Биография пока пустая» at zero trips. | Value 23 semibold, label secondary 5 apart, zeros while loading → as iOS |
| GA-08 dates | fixed | «В гараже с марта 2025» through `StatsPeriodFormat.monthYearGenitive` (11 regular); a sold car reads «Продана в марте 2026 · биография закрыта» (11 semibold, accent), the month in the prepositional case from a table for Russian and Ukrainian (`monthYearPrepositional`). | «с май 2024 г.», «Продана в 1 марта 2026 г.» → iOS wording |
| GA-09 plate | fixed | `VehiclePlateChip`: 11 semibold, tracking 0.3, card fill with a 1 dp border, radius 4, 6 and 2 inside. | 13 medium, card-alt, radius 5, 8 and 5 → as iOS |
| GA-10 photo on the stage | fixed | A main photo is clipped to radius 20 and is 0.60 of the stage width high; the foreign page uses the same stage. | Square 300 × 180 / 200 dp tall, radius 16 → rounded 20 |
| GA-11 «Где была» | fixed | `VehicleWhereWasCard`: one card (14 inside, radius 16), «Где была» 15 bold over «4 региона · 7 городов» 12 tertiary and a chevron, a 130 dp map clipped to 12, the first three regions as chips (radius 7, 10 medium) and «+N». On one's own page the header opens the map, on someone else's the whole card. Absent without routes. | Section label + row + flush square map → the card |
| GA-12 trip photos | fixed | `GarageTripPhotos`: a card (16 inside, radius 20), «Фото из поездок» 16 bold, the count and a chevron over up to four thumbnails 64 high, radius 9, 7 apart; the tap opens the full-screen grid. Absent without photos. The car's own photos open from the stage. | Two list rows, no thumbnails → the strip card |
| GA-13 trips of the car | fixed | `VehicleTripsCard`: one card, «Поездки этой машины» 14 semibold, the count and a chevron, three `CompactTripRow`s (lock for a private trip, title 13 medium, «2 нояб., 18:01 · 39 мин» 11 tertiary, distance 13 bold, chevron, 38 high) under hairlines. | Journal cards with a map each → compact rows |
| GA-14 about | fixed | `VehicleAboutCard`: the owner's line 13 regular, the footer «Это пишет владелец…» 11 tertiary and an accent «Редактировать»; after the trip photos. On someone else's car the footer says who wrote it. | Section label + 14.5 semibold row → card |
| GA-15 records | fixed | `vehicleRecords` + `VehicleRecordsCard`: one card of up to four rows (longest trip, highest, best day named by the date of the longest trip, longest day), label 11 tertiary over the name 14 semibold, value 15 bold, 46 high; a chevron only on the longest trip. A row appears only when it has something to say. | Two `MeRow`s → the card |
| GA-16 recording block | fixed | `VehicleRecordingSection`: the stereo status row (34 disc tinted at 14 %, title 14 bold, line 12, accent capsule «Привязать магнитолу» / «Настройки», nothing to press once it is connected), a hairline and «Автозапись · Вкл ›». `rememberStereoStatus` reads Bluetooth, the saved stereo and the audio outputs; the decision is the pure `stereoStatus`. | One «Автозапись ›» row → the card in its three faces |
| GA-17 fuel and electricity | fixed | `VehicleEnergySections`: «Расход топлива» with city, highway and price rows (13 medium, 13 semibold value, chevron, rules), «Расход электричества» for electric and plug-in cars; an unset number is «—». Every row opens the edit form. | Nothing on the page → both blocks |
| GA-18 «Кто видит» | fixed | The section and its sheet (`VehicleSheet.privacy`) are gone from the vehicle page; visibility stays in the edit form. | Extra section → none |
| GA-19 section labels | fixed | `GarageSectionLabel` (the heading of «Я»: 16 bold ink) for «Рекорды», «Запись», «Расход топлива»; the other blocks carry their titles inside. | 13 semibold secondary → 16 bold ink |
| GA-20 odometer caption | fixed | 4 between the number and «НА ОДОМЕТРЕ». | 9.1 dp → iOS 13.7 pt baseline to caption |
| GA-21 foreign bar | fixed | `PublicCarScreen` draws the iPhone's bar: a bare chevron and a bare «…» in 44 dp frames, the car's name 17 bold over the owner 11. | White circles, 18 heavy → bare |
| GA-22 rules | fixed | A rule 16 above the three numbers and one 14 above «В гараже с…» (also when the numbers are hidden). | 18 dp spacer → rules |
| GA-23 car photos | fixed | `ForeignPhotoStrip`: the other photos as a bare strip, 110 × 84, radius 12, 8 apart, no heading, no card; a tap opens the viewer. | Titled card of 72 dp tiles → strip |
| GA-24 foreign records | fixed | `ForeignRecordCards`: one card per record with an 8 dp dot (green for the longest, accent for the highest), label over trip, value. | One `MeRow` → cards |
| GA-25 tags | fixed | `VehicleTagsRow`: accent text 11 semibold on accent 8 %, radius 8, 4 and 8 inside; it wraps into a column when the row is too wide. | 12 regular capsules in one row → as iOS |
| GA-26 photo grid | fixed | `GaragePhotoGridCover`: a full-screen cover with the bar (back circle, centred title), three columns of cells 112 high, radius 9, 6 apart, 14 from the edges; a tap opens the viewer. | Large-detent sheet, square cells, 8 apart, 16 → cover |
| GA-27 DRIVE2 sheet | fixed | `Drive2Sheet`: a card-coloured sheet, 20 inside, «Бортжурнал на DRIVE2» 19 bold and a 44 grey close; help 14; the link in a card-alt box (radius 12, 12 inside, 2–3 lines) with an accent clipboard button; a status line (green «✓ Ссылка на машину: Volkswagen · Polo» or the hint); the note 12 tertiary; «Сохранить» 52 high; «Отвязать» centred in accent. | Page-coloured sheet, `MeField`, red left-aligned unlink → as iOS |
| GA-28 avatar block | fixed | `VehicleAvatarCard` (the iPhone's `avatarCard`): headed «Аватар»; the sprite 168 × 106 on the dark plate (208 × 130, radius 18); «Тип» with tiles of 68 on the same plate (the chosen one: accent 16 % and a 2 dp frame), absent when the vehicle has one silhouette; «Цвет». | Asphalt stage, light 64 dp tiles, no labels, lone tile → as iOS |
| GA-29 swatches | fixed | 34 dp dots in 44 dp targets, 10 apart, in a row that scrolls; the chosen one has a 2 dp ring 4 outside the dot; only a swatch with luminance above 0.72 (white) gets a hairline. | 32 dp spread, 3 dp edge, every dot bordered → as iOS |
| GA-30 «Двигатель» | fixed | `PowertrainCard`: a card of its own, no heading, «Двигатель» 15 medium over «Чем машина едет» 12 tertiary, the value 13 medium and a chevron. | Label over a repeating row → the card |
| GA-31 all trips | fixed | `VehicleTripsScreen`, a pushed page: the bar with «Поездки» over the car's name, a Дата / Расстояние / Высота control, one card of compact rows with a 38 dp route thumbnail, the climb under the distance, and the note that private trips are counted for the owner alone. | Sheet of journal cards, no sort → page |
| GA-32 map, level, photos | partly | Pushed pages, not sheets. `VehicleLevelScreen` (level card, the two text sections, the decade colours), `VehiclePhotosPage` (bar with an accent «+», the main photo with «Главная», a grid of 104 dp tiles radius 12, the hint). `VehicleMapScreen` has the iPhone's header and summary capsules but draws the route lines on the house map: iOS reuses the Atlas map with the fog, the roads and the regions (`divergences.md`). The «Показывать фотографии этой машины другим?» dialog after the first photo is a missing feature and is not here. | Large sheets with less → pages; map engine differs |
| GA-33 garage card | fixed | `GarageVehicleCard`: name 16 bold with a lock, a «Продана» capsule or a check, the plate chip and no make-and-model line, the XP bar in the colour of the level's decade with «LVL 6» in Press Start 2P 9, facts with the map-pin and flag icons. | Make-model line, gold bar, no icons → as iOS |
| GA-34 list labels | fixed | «Активная», «Остальные», «Проданные» in `GarageSectionLabel`; the hints 11 tertiary. | Grey section style, 12 captions → 16 bold ink, 11 |
| SE-01 footer | fixed | `SettingsFooter`: the wordmark in Press Start 2P 8 (accent) with «· v0.9.2-dev · OneZee» (12 secondary), the two legal links side by side 16 apart (11 medium, underlined), the underlined OpenStreetMap credit and the Natural Earth line (11), 10 apart, centred, 12 under the cards. The «О приложении» row and its sheet (`SettingsSheet.about`) are gone. | Loose row on the page → footer |
| SE-02 grabber and header | fixed | `ContentSizedSheet(grabber = true)` draws the system handle (34 × 5, 8 from the edge, the ink at 18 %) on Настройки, its sub-pages, auto-record and the stereo picker; `MeSheetHeader(title, 12.dp)` pads 18 above and 12 below. | No handle, the first card 66 dp under the edge → handle, 60 |
| SE-03 notifications row | partly | `SettingsToggleRow`: the 30 dp tile with the bell (`bell.fill` drawn), 14.5 semibold title, the switch; 56 high, the title in the icon column. The «?» hint bubble is a missing feature (`SettingsHintButton`) and is not drawn. | Title at 29 dp → 70 dp; no tile → tile |
| SE-04 account row | fixed | `SettingsAccountRow`: a `SettingsIconRow` with the cloud in its tile (slashed while sync is off), the title, the live sync marker and a chevron, 56 high, a divider above it; `AccountEntry` no longer wraps it in a card. | 48 dp bare cloud → 56 dp tile row |
| SE-05 symbols | fixed | New `AppGlyph`s drawn by eye: hand, ruler, envelope, ladybug, bell, cloud and slashed cloud (also speaker, music note, antenna, map pin, clipboard, star for the garage); each sized by ink in `GlyphSizingTests`. | Info, gear, pencil, flag → the iPhone's meanings |
| SE-06 «Автозапись» row | fixed | Removed from the app card; auto-record opens from the vehicle page, as on the iPhone. | Four rows → three |
| SE-07 subtitle rows | fixed | `SettingsIconRow`: 11 above and below, a gap of 2, title 14.5 semibold, subtitle 11. | 60 dp → 55 |
| SE-08 chevrons and values | fixed | Язык and Тема show only the value; «Единицы и формат» shows value and chevron; values 14 medium. | Chevron on every row, 14 regular → as iOS |
| SE-09 switch | fixed | `SettingsSwitch`: the house `HouseSwitch` at 0.9 in its 51 × 44 frame; `MeToggle` uses it too. | Outlined 46 × 28 → the iPhone's switch |
| SE-10 sub-pages | fixed, one stop differs | Each sub-page is a sheet of its own over the one before (`SettingsRoute` stacks `state.sheets`; pickers draw over the last page), with the settings header (22 bold, grey close). «Приватность» opens as tall as its rows instead of at the medium detent (`divergences.md`). | Content swapped inside one sheet → stacked sheets |
| SE-11 units | fixed | `SettingsPreferencesCard`: «Единицы» (ruler tile) and «Расчёт средней скорости» (speedometer tile) with the value and no chevron, the 11.5 footnote under the card. | No tiles, chevrons, no footnote → as iOS |
| SE-12 section labels | fixed | The auto-record headings are `GarageSectionLabel` (16 bold ink). | 12 semibold secondary → 16 bold |
| SE-13 toggle card | fixed | The antenna (accent while on, tertiary off), «Автозапись» 15 semibold and a full-size switch over a hairline and the description, 14 inside. | No icon, 46 × 28 → as iOS |
| SE-14 stereo card | fixed | «+ Привязать магнитолу» is an accent text button; a linked device shows the music note (accent), the name 14 medium, the green «Привязана» capsule and the unlink circle. | Tile row with a chevron, bare green word → as iOS |
| SE-15 extra row | fixed | The «Открыть настройки» row after the auto-record screen is gone (`onSettings` removed). | Appended row → none |
| SE-16 stereo picker | partly | `StereoPickerContent`: «Привязать магнитолу» 17 bold with the grey close, «Текущий аудиовыход» (speaker in accent, name 14 semibold, «Bluetooth-аудио» / «Android Auto» 12 tertiary, accent «+» or the green «Добавлена» capsule) and «Устройства рядом» with the refresh button and the centred scan hint, in white cards with a 1 dp border. Android keeps its own affordances and leaves the «Bluetooth выключен» page undrawn (`divergences.md`). | Plain settings list → the iPhone's sheet |
| ON-01 real trip on the card | not done | A whole feature: the card would have to project the newest recorded route and print its numbers. The brief leaves missing features out. | |
| ON-02 end dots | fixed | `MockMap`: a 9 dp disc with a 2 dp white ring centred on its edge. | 13 dp white under 9 dp colour → 11 under 7 |
| ON-03 Android copy | kept | Required by the Android permission rules, as the audit says. | |

Not drawn, because each is a whole feature rather than a difference: the «Админ» card, the «?» hint bubbles,
the route-colour row, «Сказать спасибо», the car-photos visibility dialog and the Bluetooth-off page of the
stereo picker.

## Shared files touched

- `core/designsystem` — `ContentSizedSheet` (the `grabber` parameter), `Glyph.kt` and 14 new glyphs
  (`glyphs/*.svg`, `glyph_*.xml`), `GlyphSizingTests`, `SheetLookTests`.
- `feature/settings/MeComponents.kt` — `MeSheetHeader(title, bottom, onClose)` overload, `MeToggle` draws
  `HouseSwitch` (SE-09). `MeToggle` is used elsewhere too: the baseline `recording_summary_*` moved with it
  (the toggle on the finish card is now the iPhone's switch); no other screen of the recording wave changed.
- `feature/social/SocialViewModels.kt` and `feature/social/ui/SocialScreens.kt` — `PublicCarScreen` is the
  foreign vehicle page (GA-21…GA-25); one default parameter on `VehicleBiography`.
- `feature/me/MeNavigation.kt` and `MeTab.kt` — four routes and their pages, `keepsTabBar`, the passport reload
  when it is on top.
- `feature/account/AccountEntry.kt` — the signed-in row is `SettingsAccountRow`.
- `feature/settings/SettingsManager.kt` — `SettingsSheet.about` removed.

## Tests

New, with the iPhone's numbers, so the old geometry fails them. I checked that they bite: twenty deliberate
regressions applied together (a padding or a size put back, a rule dropped, a table entry spoiled, a sort
reversed) turned 16 of the 26 look tests red, each of them guarding a line I had spoiled; the other ten cover
lines I left alone. The sources were restored before the commit.

- `GarageLookTests` (17): the Russian and Ukrainian months, the model line, the spec line, the trip title
  ladder, the clock line, the four records and when each appears, the three sorts, the swatch rule, the
  stereo card's three faces; the own and the foreign hero numbers (26 / 16 / 18 / 16 and 18 / 12 / 14 / 18);
  the thirds of the three numbers; the avatar card (seven silhouettes or none, both axes, 68 / 44 / 54 / 208 × 130).
- `SettingsLookTests` (8): rows of 56 and 55 and the toggle row of 56, nine tiles of 30 at 28 from the edge,
  the guest's first card, the footer's 16 / 16 / 10 / 10 and its centring, the grabber table, the privacy sheet
  and the units picker standing over the settings.
- `OnboardingLookTests` (1): by pixels, the colour at 2 dp, the ring at 4 dp and the map again at 6 dp from each end.
- `GarageScreenshotTests` (18: the vehicle page in six states, its level, trips, photos and photo-grid pages, the
  DRIVE2 sheet and the vehicle form in three), `AutoRecordTriggerShotTests` (+2 picker shots, +1 behaviour test: a
  linked output is not linked twice), `SettingsStackShotTests` (4: the sheet with its grabber, privacy over it,
  the preferences, the units picker over them), each in both themes.

Whole suite: 2,801 tests, 0 failures (app 1,021, designsystem 288, map 150, logic 678, model 95, data 242,
network 89, sync 127, location 111), against 2,707 before; `lintDebug` has no errors. In the app +76: the 26 look
tests above and 50 screenshot tests (36 garage, 8 settings stack, 6 auto-record, each in both themes). In
designsystem +18: 14 glyph sizings and the 4 grabber checks. 74 baselines changed, 26 re-recorded and 48 new.
Four of the 26 are not this wave's screens: the glyph sheet (new glyphs) and `recording_summary_*` (its toggle
is now the iPhone's switch). I compared each changed render of the garage and the settings sheet with the
iPhone's shot of the same state where there is one (garage-03, -04, -04b, -06, -08), in light and dark; the
pages the iPhone has no shot of (the pushed pages, the form, the stereo picker, the stacked sheets) against
the iPhone's code, measured, and the new look tests carry those numbers.
