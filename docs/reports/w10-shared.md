# w10-shared

Wave 1 of the design-parity fixes: the shared components that touch many screens. The reference is
the 3 November 2026 audit of 50 iPhone/Android pairs (`differences.md`, "Shared components", the top 20
and ST-01/ST-02). Clone `w10-shared`, master at `ca0b248`, not pushed. The iPhone app was only read.

## Per audit item

| ID | Status | What changed | Before → after |
|---|---|---|---|
| SH-04 sheet scrim | fixed | `AtlasColors.sheetScrim`, black 0.12 in light and 0.29 in dark, used by `ContentSizedSheet`. The Atlas scrim stays for the Places search. | Checkpoint editor page behind the sheet 248 → 156, now 216 (iOS DRIVE2 sheet 218). Dark 0.55 → 0.29 (iOS Atlas «Вид карты» sheet, measured on the fog) |
| SH-05 sheet corners | fixed | `AppRadius.sheet` 28 → 10 on a continuous curve (`ContinuousTopRoundedRectangle`, the top two corners of the dialog curve). `ContentSizedSheet(cornerRadius)`: 28 for the Atlas appearance, explanation and home sheets, 22 for the sync status sheet, as iOS sets them | Radius 28 → 10, the iPhone's (its curve meets the edge 15 pt in). On the checkpoint editor the top row now starts 11.0 dp in, from 27.8, and the left edge 7.6 dp down, from 23.6 |
| SH-03 map chrome glass | fixed | `ChromeGlass` alpha 0.62 → 0.34 | Back, «…» and expand over the light hero (109, 109, 108) → (170, 169, 167); iOS (170, 169, 167) |
| SH-01 one nav bar | fixed | `CustomNavBar` in `core:designsystem` replaces `MeNavBar` and `SocialNavBar` at all 19 call sites; both are deleted. Controls 20 from the edge, top 10 (20 in a sheet, set by `ContentSizedSheet` itself and by the garage list, which iOS marks as a sheet even when pushed), bottom 12, title 18 heavy centred with 72 clearance, optional 11 subtitle | Stats: back circle at 16 → 20.2 dp (iOS 20); first label 76.2 → 79.2 dp (iOS 79); left-aligned 17 semibold titles on the garage, vehicle page, profile, country, levels, achievements, badge, clubs, auto-record and the settings pages are now centred 18 heavy |
| ST-01 large title | fixed | `largeTitle = true` for Статистика, Достижения and Уровни: 28 heavy, tracking −0.56, shrinks to 70 % before it truncates; the owner's name moved from a loose caption into the bar's subtitle, as on iOS | Stats title first-letter height 13.0 → 20.2 dp (iOS cap height 21 pt) |
| SH-09 tab bar | fixed | `MeRoute.keepsTabBar` (garage, vehicle page, drafts, clubs). `MeNavHost` hides the bar only for the other routes, which are the ones iOS hides it on. The garage list and the vehicle page now reserve the bar's room. | Drafts list: no bar and an empty 100 dp band → the bar, with «Все мои · N» 12 dp above it (iOS 12). The delete dialog still hides it |
| SH-06 press feedback | fixed for the default, partly per site | `HouseIndication` (an `IndicationNodeFactory`) is the theme's `LocalIndication`: every `clickable`, `selectable` and `toggleable` that names no indication dims its content to 0.55, like `plainPressFeedback`. The Статистика record rows are a `PressableCard`; the badge overlay's scrim takes no feedback | All 84 call sites without an indication (the audit's count; 85 with `core:map`) no longer draw the grey rectangle. The other card-like rows (journal trip cards, garage vehicle cards, achievement tiles) get the plain dim, not iOS's shrink and fade: they belong to the waves of those screens |
| SH-07 glyph size | fixed, three shapes by area | `AppGlyph.opticalScale` (1.0 to 1.35 per glyph): the artwork is drawn that much larger inside the unchanged box, so its ink matches its SF Symbol's at box / 1.2. The factor comes from the symbol's measured ink box over the artwork's own (geometric mean, 0.05 steps). Strokes grow with it | Owner «…» popover: trash 12.6×12.6 → 15.6×16.4 dp (iOS 14.7×16.7), tablecells 14.1×11.8 → 17.1×14.1 (iOS 17.3×13.7); Atlas share 10.3×14.1 → 13.3×18.3 by the same method (iOS 14.3×18.0). Stroke thickness on 22 outline glyphs now lands at 81–128 % of the semibold symbol, most at 90–115 % |
| SH-08 glyph shapes | fixed | `pencil` (tip, body, gap, eraser cap), `tablecells` (two columns, three rows, one frame), `houseFill` (roof line, chimney, door cut-out) and `bubbleRight` (rounded frame with a tail) redrawn by eye as clean paths; the old ones were jagged tracings | The four read as their symbols in the popover, the settings row, the Atlas home tile and the feed card |
| ST-02 record CTA | fixed | The route glyph (the Atlas `WorldGlyph.curvePath`, the sibling of the iOS symbol), 17 dp, the 15 bold label, and the accent shadow 0.3 / 3 / y 1 | Text only → glyph, label and shadow |
| SH-02 | not mine | Kept under TD-01 (the trip top bar's share capsule) | |

## Tests

New, and each fails on the old behaviour: `CustomNavBarTests` (10: geometry of the bar, sheet inset,
centring, large title ratio and clearance, subtitle, heading, back), `SheetLookTests` (6: scrim alpha
0.12 / 0.29, top corners cut and bottom square, the Atlas radius), `HouseIndicationTests` (a press dims
red to (255, 114, 114) instead of darkening it, the release brings it back), `GlyphSizingTests` (64
glyphs rendered against the symbols' ink boxes, ±15 %; mountain, road lanes and pause by area, ±25 %),
`MapChromeGlassTests`, and in the app `MePresentationTests.theTabBarStaysOnlyOnTheFourScreensIosKeepsItOn`
plus tab-bar assertions in `TripsBehaviourTests` (the list keeps the bar, the trip and the delete
dialog hide it).

Whole suite: 2,629 tests, 0 failures (app 867, designsystem 270, map 150, logic 678, model 95, data
242, network 89, sync 127, location 111); +83 in designsystem (81 above and the two shots of the new
nav bar specimen) and +2 in the app. 274 of the 357 screenshots were re-recorded and 2 are new. I
compared the changed screens with the iPhone shots: Статистика, the vehicle page, the drafts list, the
trip hero, the DRIVE2 sheet against the checkpoint editor, and the owner menu.

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew --no-daemon --offline -Proborazzi.test.record=true test
./gradlew --no-daemon --offline -Proborazzi.test.verify=true test lintDebug
```

Both pass. Lint reports nothing on the new files.

## Where Android differs and why

`docs/divergences.md`: the «Sheet» row no longer lists the scrim and radius as Android's own; «Glass
background» and «Symbols» say how the tint and the optical scale stand in for the material and the font
size; new rows «Press feedback» (one indication cannot tell a card from a button, and it dims only what
sits inside the `clickable`) and «Pushed-screen bar» (no ground of its own, shrink in 0.5 dp steps, the
sheet inset set by the house sheet).

## Left, and not matched

- Sheets still draw no grabber (SE-02 asks for one on the sheets that show it on iOS).
- The loading car is 28.1×25.2 dp against 33.7×27: its call site passes a 30 dp box for a 28 pt symbol.
  The paused pill's bars (10.7×8.4 vs 16.7×12.7) are drawn by the recording screen, not by `AppGlyph.pause`.
  Both are for the Atlas and recording waves. The Atlas `WorldGlyph` and the social glyph sets have
  their own sizes and are not scaled (AT-02).
- Per-site `PressableCardStyle` (shrink and fade) on the remaining card rows, and `TapOnlyButtonStyle`'s
  0.72 on the recording buttons: the default is the plain dim.
- The map glass is a tint, not a blur: over a route the line shows through the button.
- `docs/parity.md` is not regenerated. It was already stale before this wave (the report changes 300
  lines unrelated to it); regenerate it when the waves are merged.
