# w12-maps

Wave 3b of the design-parity fixes: the record screen and the finish card, the Atlas and the Places tab.
The reference is the 3 November 2026 audit of 50 iPhone/Android pairs (`differences.md`: RC-01 to RC-34,
AT-01 to AT-19, PL-01 to PL-11). Clone `w12-maps`, master at `ffa56da` (waves 1, 2a and 2b), not pushed. The
iPhone app was only read; where a number is not in the audit I took it from the iPhone's screenshots or
measured the ink of its symbol with a small macOS tool. Wave 1's shared pieces are used as they are:
`Glyph` with its optical scale, `iosShadow`, `PressableCard`, `NavCircleButton`, `HouseSwitch`,
`ContentSizedSheet`, the glass tokens.

Commits, oldest first: `feat: recording glyph set`, `fix: recording screen parity`, `fix: trip summary
parity`, `feat: map area layer`, `fix: atlas parity`, `fix: places parity`, and this report with the
divergences.

## Recording and finish card (RC-01 to RC-34)

| ID | Status | What changed |
|---|---|---|
| RC-01 idle card | fixed | The card is the iPhone's material colour (40, 40, 42) at .72 with its shadow (black .35, radius 24, y 8); the old haze is gone. No backdrop blur: a tint stands in (divergences, «Blurred surfaces»). Test: the map darkens above the card edge, not below it |
| RC-02 map buttons | fixed | `MapButton`: 44, radius 12, fill (.2, .2, .2) at .85, a centred 1 dp white .3 stroke, glyph 18; + and − dim to .3 at the zoom limits (`canZoomIn` / `canZoomOut` come from the camera's 1 to 19 range) |
| RC-03 location button | fixed | `location`, `location.fill` (accent) and `location.north.line.fill` (accent), drawn as `RecordingGlyph`s: ink 17.1 square, 17.1 × 16.3 and 13.1 × 21 |
| RC-04 vehicle chip | fixed | The vehicle's own pixel avatar at 16 × 16 (nearest neighbour), the up-and-down chevron (10 bold) only when the garage has vehicles, the person in the passenger state, the minus circle without a vehicle; stroke white .14 in the top bar and .08 in the idle card |
| RC-05 passenger toggle | fixed | `person.fill` 12 before the label, padding 7, the stroke when off: 29.7 tall like the chip (was 31.6 next to 29.7) |
| RC-06 GPS pill | fixed | Padding 6, fill .72, stroke white .08: 27.3 tall |
| RC-07 slider thumb | fixed | `SpokeSpinner` (eight spokes, traced from the iPhone's render) on the held thumb; the live thumb glows accent .4, radius 8, and the track is not clipped any more |
| RC-08 pulse ring | fixed | The outer ring starts at .5 and fades with its growth; a still frame (reduce motion) shows .5, not 1 |
| RC-09 docking | fixed | Both blocks end 24 above the physical bottom, and above a tappable navigation bar only when there is one (`dockInset = tabBarTappableInset()`); the map's inset follows. Test with and without the inset |
| RC-10 haze | fixed | `edgeScrims` on top only |
| RC-11 stat icons | fixed | The curve between two end points and `mountain.2`, canvas-drawn at the iPhone's ink (11.5 × 14 and 22.4 × 11.3) |
| RC-12 control row | fixed | Pause/Resume radius 14 and the checkpoint circle with the white .12 stroke; stop, pause and checkpoint cast black .25 / 6 / y 2; stop square 15.7, flag 20.4 (17 pt of ink); the play and pause marks are `RecordingShape`s |
| RC-13 panel glass | fixed | (40, 40, 42) at .72 |
| RC-14 paused pill | fixed | Inter 14 bold, padding 16 / 9, shadow black .28 / 10 / y 3 |
| RC-15 draft label | fixed | Inline after the vehicle chip, Inter 12 semibold in secondary ink on the opaque card colour, padding 24 / 12 a side. The audit's «about 26 pt tall» does not follow from its own padding: the code gives 38.5, which is what stands. The separate row is gone and the divergence row with it; the chip gives way first (`weight(1f, fill = false)`) |
| RC-16 signal banners | fixed | The lost banner has the slashed antenna in amber (padding 12 / 10); the weak toast the 22 red disc with a white «!» and its .35 stroke |
| RC-17 legend | fixed | Always the dark card, 280 wide, shadow black .3 / 16 / y 6, the antenna icon in the signal colour, the 10 bold cross on a 24 disc, details in Inter 12 medium |
| RC-18 stop pill | fixed | Three texts 8 apart: Inter 14 semibold at .75, the dot at .3, padding 14 / 8 |
| RC-19 stop card | fixed | The action icons at 15 bold, the return row 62 tall in semibold, shadow black .45 / 24 / y 8: the card is 342.5 (iPhone 342) |
| RC-20 refusal toast | fixed | An amber icon (antenna or exclamation circle), radius 18 or 22, fill .92, padding 14 / 10; «start anyway» is a full-width capsule, Inter 13 bold, accent on accent .16, padding 8 |
| RC-21 discard toast | fixed | Full width, the trash at .7, padding 14 / 10, fill .92, stroke white .14 |
| RC-22 denied subtitle | fixed | White .42 |
| RC-23 finishing bar | fixed | Removed: the idle card shows at once and the summary opens over it |
| RC-24 recovery | fixed | The chip starts with the check circle and uses monospaced digits; the second button is Inter 15 semibold, 48 tall |
| RC-25 badge | partly | A tapped badge opens the iPhone's card in place (`PublicBadgeOverlay`: 300 wide, radius 24, scrim .4, art 96, name in the badge colour, ×) with the date earned. The trip's own record figure («recordValue») is not ported |
| RC-26 description row | fixed | `text.alignleft` 15 in a 38 tile (accent .08, radius 10), the row top-aligned, the placeholder medium: 70 tall (was 54) |
| RC-27 publish row | fixed | The globe in a 38 tile, one column (title 14 bold, hint and footnote 11 in the tertiary colour), the switch top right, padding 16 |
| RC-28 bottom buttons | fixed | «Фото» and «Готово» in Inter 14 bold, 44 tall, the camera turns into the check circle with photos, the border on «Фото», the accent shadow .3 / 1.5 / y 1 on «Готово» |
| RC-29 «Открыто» | fixed | `seal.fill` (#F0A070) before the title, the card shadow black .03 |
| RC-30 streak rows | fixed | The flame and the circling arrows at 14 in accent |
| RC-31 rewards | fixed | The accent glow .12 / 16 / y 4, the fade-and-scale entrance (still under reduce motion), the pill in PressStart2P at its only weight |
| RC-32 notes editor | fixed | «Отмена», «Описание», «Сохранить» in one row (16 medium secondary, bold, bold accent), the text on the card colour, the «N / 500» counter in graphemes, red at the limit. It opens at the large detent (divergences) |
| RC-33 card shadows | fixed | Black .03 / 2 / y 1 on the three cards, the captions at .4 tracking |
| RC-34 photo strip | not changed | The strip stays: Android's picker cannot preselect the earlier photos (the existing «Recording photos» divergence). The trash control now stands inline (44) instead of under each thumbnail |

## Atlas (AT-01 to AT-19)

Measured on the render, ink in dp against the iPhone's points; the glyph tests accept 1.1.

| ID | Status | What changed |
|---|---|---|
| AT-01 share glyph | fixed | Box 19.2 → 18.8: ink about 13.6 × 18.7 against 14.3 × 18.0 (the artwork is a little taller for its width than the symbol; at 19.2 it was 14.3 × 19.4). Offline chip too |
| AT-02 map controls | fixed | `WorldGlyph.layers` and `location` traced from the iPhone's render: three equal diamonds 4.6 apart with the stubs of the two under the first, the arrow with its notch over the tail, stroke 2 pt. Ink 18.7 × 20.2 and 17.1 × 17.5 (iPhone 18.2 × 20.0 and 17.25 × 17.3) |
| AT-03 «Бета» chip | fixed | The chevron stands in a 7 wide box: the pill is 53.0 (it was 55.2). Places has the same chip |
| AT-04 section counts | fixed | `SectionHeader` puts the count at the right edge; only «Поездки здесь» keeps it beside the heading (`countInline`). Region, list and cities |
| AT-05 skeleton | fixed | Three columns of bars straight on the sheet (x 16 / 145 / 274, 36 tall), no card. The Social public map wraps it in its own 16 dp card as the iPhone does, so that screen's skeleton lost its second card (100 → 68 dp) |
| AT-06 loading car | partly | Box 30 → 33.6 (1.2 × 28). The house car artwork is squarer than `car.fill`: 32.0 × 29.7 against 33.7 × 27.0, equal in area to 2 %. Also on Social and Stats |
| AT-07 controls with home | fixed | The controls follow only a PLACE card; with the home card they stay at the peek and the 96 card hides the capsule, as on the iPhone |
| AT-08 card content | fixed | The row is centred in the 96: the tile 16.4 under the top, the action 16.4 over the bottom. Place and home |
| AT-09 house | fixed | Box 22 → 21: ink 22.1 × 18.7 against 21.7 × 19.0 (the artwork is wider than the symbol) |
| AT-10 cross | fixed | `CLOSE_CROSS_GLYPH` 15.6 → 10.7 × 10.7 of ink, exactly the iPhone's. On the place and home cards, the shared sheet header (style, home, explanation) and the Places selection card |
| AT-11 list gaps | fixed | The extra 4 on the two headings is gone: 10 between the numbers, «Регионы», its card and «Города» |
| AT-12 trip and road cards | fixed | The trip row starts 39 under the panel's top, the road heading 41 |
| AT-13 close | fixed | `NavCircleButton`: 40, card colour, soft shadow, 14 from the top and 16 from the edge |
| AT-14 road rows | fixed | `AtlasRoadRow`: 16 inset, 6 above and under (52 tall), the line Inter 12, gap 3, the chevron 12 semibold in the full secondary colour |
| AT-15 «Как мы считаем» | partly | Opens at the medium detent (453 of 852, as a share of the window), the cards scroll inside and the third runs under the edge. It cannot be dragged up to full height (divergences) |
| AT-16 line spacing | fixed | `withLineSpacing` (the font's height plus 3, none above the first or under the last line) on the beta body (Inter 14) and the explanation bodies (Inter 15): the pitch is 19.9 and 21.2 |
| AT-17 home toggles | fixed | `ToggleRow(minHeight)`: 44 on the two home cards (the first card is 91.5, the iPhone's 91), 56 stays in «Вид карты» |
| AT-18 zone disc | fixed in code | New `MapArea` layer in `core:map` (fill layer and edge layer under the fog): the accent at .16 inside and .7 along a 2 pt edge, `homeZone()` in the sheet. Robolectric has no MapLibre, so the disc itself is not rendered here: tested as data and as ring geometry; look at it on a device |
| AT-19 poster progress | fixed | The white spoke spinner (20) before «Готовим снимок…», the pill's foot 286 above the bottom (`collapsedHeight + 8`) plus a tappable navigation bar |

## Places (PL-01 to PL-11)

| ID | Status | What changed |
|---|---|---|
| PL-01 group headers | fixed | Every header gets the 6 once there are two groups, the first included: 12 + 6 under the card above. The heading and its count share a first baseline |
| PL-02 placeholder | fixed | The system tertiary label: (235, 235, 245) at .3 in dark, (60, 60, 67) at .3 in light |
| PL-03 clear button | fixed | The 28 dp target stays and moves right by 5: the ink ends 13.4 from the field's edge |
| PL-04 results padding | fixed | `max(keyboard + 16, tab bar clearance)`: the last match clears the tab bar with the keyboard down |
| PL-05 «Обычно занимает» | fixed | No padding on the header or the row: header, 2, a row of 40 at least, 6 |
| PL-06 chip baseline | fixed | The chip is one `Text` with modifiers, so its baseline reaches the row: it sits on the baseline of «Здесь N раз» |
| PL-07 scrolled header | partly | The back button is always in the header and fades with it; it takes taps above one half and is only the drawing below (the disc on the map still holds the tap). The blur is not possible: the page colour at .97 stays for the iPhone's .92 over `.ultraThinMaterial` (divergences) |
| PL-08 sheet bottom | fixed | `tabBarContentClearance()`: the footnote ends 110 above the physical bottom |
| PL-09 rename buttons | fixed | 52 tall, `AppType.button`, radius 16 (`RenameButton`; the beta card keeps its own 44) |
| PL-10 hint | fixed | `pulledUp(4)`, SwiftUI's negative padding: 10 under the field, what follows unmoved |
| PL-11 «Отметить в поездке» | fixed | Title and card are one column with 10 between |

## Tests

New and each fails on the old behaviour: I put the old code back for 21 items at once, in one build
(PL-01 to PL-11, AT-03, AT-04, AT-05, AT-07, AT-12 to AT-17), and all 21 matching tests failed; AT-08 and
AT-11 were put back in a second build. The glyph tests failed on the first run against the old
sizes, and I tuned the boxes to them. The new classes: `RecordingGlyphTests` (14 ink sizes and a specimen
sheet), `RecordingDesignTests` (28), `AtlasDesignTests` (17), `AtlasLoadingDesignTests` (2),
`PlacesDesignTests` (10), `PlacesNewcomerDesignTests` (1), `MapAreaTests` in `core:map` (3), and the
interaction test of the dock rewritten. `WorldDesignTest` holds the measuring tools (a node's box, the ink
of a glyph at half coverage). A node outside its scroll viewport has empty `boundsInRoot`; those tests
read `positionInRoot` and `size`.

Screenshots: 19 new frames (six recording and summary states in both themes, the glyph specimen, the
Atlas explanation sheet and the home sheet with and without a home, both themes) and 82 re-recorded, among
them the Social loading frames that share the skeleton and the car. I compared the changed screens with the iPhone's shots side by side: idle,
recording, paused and stop card; the Atlas summary with its controls and share button; the home card;
the home sheet's cards (91.5 against 91, 86.5 against 86.5); the place screen's «Обычно занимает» card
(158 against 159 px at 2 px a point); the search field.

Commands, from the clone with `JAVA_HOME` at JDK 17:

```
./gradlew --no-daemon --offline -Proborazzi.test.record=true :app:testDebugUnitTest --tests 'com.onezee.triptrack.world.*'
./gradlew --no-daemon --offline :app:testDebugUnitTest --tests 'com.onezee.triptrack.world.*DesignTests'
./gradlew --no-daemon --offline --rerun-tasks -Proborazzi.test.verify=true test lintDebug
```

Whole suite on the committed code: BUILD SUCCESSFUL in 6 min 43 s, 2,891 tests, 0 failures, none
skipped (app 1,126, core:logic 678, core:designsystem 270, core:data 242, core:map 153, core:sync 127,
core:location 111, core:model 95, core:network 89). That is +172 over wave 2b (2,719): +169 in the app and
+3 in `core:map`.

## Differences that remain

In `divergences.md`: the tint for the blur (recording surfaces and the scrolled place header), the
description editor's first frame, the explanation sheet's missing drag to full height, the drawn spinner and
the recording symbols. The «Recording draft label» row is gone. Not in the file because they are sizes, not
behaviour: the share, house and car artwork differ in shape from the iPhone's symbols (AT-01, AT-06, AT-09;
they belong to the shared glyph set, so I matched area and the larger share of the ink), and the audit's
RC-15 height. The 3 November pairs are light and dark at 411 × 914; no other density was rendered.

## Shared files touched

- `core/map`: `MapHost` has a new `area` parameter, `MapController` installs it, `area/AreaLayers.kt` is new
  (nothing else in the map changed: no fog and no camera code).
- `feature/atlas/ui/CarLoading.kt` (the car: Social and Stats show it) and `AtlasStatsSkeleton` (the
  Social public map shows it; its screen supplies the card).
- `AtlasBetaChip` (Places has the same chip), `AtlasControlsHeader`, `CircleButton` (new `_glyph` tag),
  `ToggleRow` (a `minHeight` parameter, defaulted to 56), `SectionHeader` (`countInline`, defaulted to
  false), `withLineSpacing` (public, any screen can use it).
- `feature/places/ui/PlaceCards.kt`: `PlaceChipLabel` takes a modifier (the list cards use it unchanged).
- `feature/social/ui/PublicBadgeOverlay.kt`: an overload with the date earned; the old signature stays.
- Nothing in `core/designsystem`, and none of the garage, settings or onboarding files.

## What is left

- Not in this wave's scope: the record screen's map-loading overlay (`CarLoadingView` until the first
  render) and the «Сказать спасибо» tip card.
- Needs a device: the zone disc on the home picker (AT-18), the dock above a three-button navigation bar
  (RC-09: tested with an inset in the unit test only) and the shadows of the idle card on a busy map.
- RC-25's record figure, RC-34's strip and the explanation sheet's drag to full height, as above.
