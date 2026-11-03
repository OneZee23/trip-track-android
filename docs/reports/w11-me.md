# w11-me

Wave 2a of the design-parity fixes: the «Я» tab and the drafts entry. The reference is the 3 November
2026 audit of 50 iPhone/Android pairs (`differences.md`, "Me/profile" ME-01…ME-37 and "Drafts"
DR-01…DR-05). Clone `w11-me`, master at `a46b2e1` (wave 1 included, so the shared nav bar, sheet look,
house press feedback, glyph sizing and the tab bar on pushed «Я» screens are reused, not redone), not
pushed. The iPhone app was only read. Numbers are dp on the Android side and points on the iPhone's;
the renders are 411 dp wide against the iPhone's 393 pt.

## Per audit item

| ID | Status | What changed | Before → after |
|---|---|---|---|
| ME-01 title above the hero | fixed | The «Я» title row and its two 12 dp gaps are gone from `MeContent`; the hero card is the first item, 4 dp under the status bar (a cover starts at the very top of the screen). | Hero top 58 → 4 dp (iOS 4 pt) |
| ME-02 level capsule | fixed | `LevelRow`: «LVL n» in Handjet Black 17 (`roadHeroAccent`; white on a chosen background or a cover), the rank in Inter 12 medium secondary and a 9 pt chevron, one row that opens Levels. The gold capsule is gone from the own hero. | Gold Inter 11 capsule, no rank → three parts on one line, as the stranger's hero already draws it |
| ME-03 gear | fixed | `MeGlyph.gearshape`, an outline of eight round-rooted teeth and a ring, 19 pt (a 22.8 dp box), secondary ink, 44 dp target. | Solid cog 23 dp → outline |
| ME-04 name | fixed | Inter 21 bold, tracking −0.3, also on the cover panel. | 20 bold, none → 21 bold, −0.3 |
| ME-05 stats | fixed | `HeroStats`: the first column at the content edge, the last at the right edge, `SpaceBetween`; value 22 bold monospaced digits (19 heavy on a cover), label 11 medium, 4 dp apart. | First value at x 81 → 36 dp (iOS 36); the last label ends at 375 = 411 − 36 |
| ME-06 cover prompt | fixed | `ProfileAddCoverPrompt`: the picture-with-plus and «Добавить обложку» in accent, Inter 14 medium, a 48 dp row; a plain 12 pt `xmark` in the secondary ink in a 48 dp target; no chevron. The glyph is drawn after `photo.badge.plus`: a frame with a filled sun and hills and a badge with a gap round it, 19 × 14 pt as the symbol is at 14 pt. | Row 56 → 48 dp, the 40 dp shadowed circle and bold ✕ gone; glyph 16 dp wide and outline only → 19 × 14 pt with filled hills, checked against the iPhone's crop at 8× |
| ME-07 signed out with trips | fixed | `JournalSignInRow`: «Войдите, чтобы журнал увидели другие» Inter 14 medium and a dark «Войти» pill (13 semibold, card colour on ink, radius 12, padding 13/9) in a card; the expired-session text when it applies. The full guest card stays for zero trips. | Big `GuestSignInCard` → one line; 12 above and 12 below |
| ME-08 double gap | fixed | Each block carries its own iOS padding and the list has none; the account slot is not emitted when empty. | Prompt to sections 24 → 12 dp |
| ME-09 sections row | fixed | `ProfileSectionsToggle`: «Гараж · Достижения · Клубы» Inter 14 semibold in the secondary ink, a 13 pt `chevron.down` that turns 180° when open, a plain card without shadow, 52 dp. «PRO» stays absent, as on the Russian storefront. | 56 dp ink row with a shadow and a right chevron → 52 dp |
| ME-10 expanded sections | fixed | Three blocks of their own: `AchievementsSection` (label, «N из M ›», the featured badge row and the strip with «+N»), `GarageSection` (label, «Весь транспорт ›», the main vehicle on a sprite plate with its level in the colour of its decade; an empty garage says what a car is for), `ClubsSection` (44 dp accent tile with the people glyph, 14.5 bold title, the waitlist line). | Three `MeRow`s in one card → three labelled blocks |
| ME-11 dashed box | fixed | 1 dp border dashed [5, 4], radius 16, secondary at 40 %, a thin 24 pt plus. | Solid border → dashed |
| ME-12 car caption | partly | Own card: `heroCaption` gives «Volkswagen Polo · 2018 · 110 л.с.» (make and model joined by a space, the catalogue power of the variant from `HeroCarStage.powerHp`), the name carries an 11 pt chevron, and the caption's 14 dp bottom padding is gone. The stranger's road card is not touched: SO-01 rewrites the same lines and needs the power from its own view model. `heroCaption` is ready for it. | «Volkswagen · Polo · 2019», no chevron → «Volkswagen Polo · 2018 · 110 л.с.», chevron; the divider 19 → 4.9 dp under the caption's ink (iOS 4.6) |
| ME-13 road fade | fixed | `ProfileRoadScene`: the scene and a 25 dp gradient from clear to `roadHeroCard` at 42 % (20 % in dark) over its foot. Used by the own card, the no-vehicle road and the stranger's `RoadScene` (one line in `PublicProfileScreen.kt`). | Flat asphalt → the foot lighter by more than 0.1 of the brightness, the row 35 dp above unchanged (test) |
| ME-14 home and journey cards | not done | Whole features (the question, its answer and the 30-day «Нет», the suggestion banner): the brief leaves missing features out except ME-32. The strings and `JourneySuggester` exist; nothing draws or stores them. | |
| ME-15 history header | fixed | `ProfileHistoryHeader`: «История» Inter 22 bold and two 44 dp icon toggles (grid and list glyphs at 16 pt, accent when on, secondary otherwise, no plate), a selectable group with a radio role. At the accessibility sizes the toggles go under the title. | 16 bold + two text chips → 22 bold + two icons |
| ME-16 search bar | fixed | `HistorySearchBar`: one 48 dp bar 8 dp under the header, a magnifier, a 15 pt field with its prompt in secondary, a 44 dp clear button while typing, card fill radius 14 without a shadow; the whole bar focuses the field and the keyboard's search drops focus. | About 71 dp captioned form field → 48 dp bar |
| ME-17 calendar header | fixed | Collapsed «Эта неделя» 15 bold and «Ноябрь ⌄» (13 semibold, 16 pt chevron) at the right, which opens at the month of the filter; opened: 32 dp step buttons with 14 pt chevrons at both ends and a centred «Ноябрь ⌃», the year only outside the current one; swipe still steps. | One left title «Октябрь 2026» → the iOS header |
| ME-18 day cells | fixed | Radius 8; today has a 2 dp ring; future days are `cardAlt` at 40 % with the numeral at 35 % and do not respond; an endpoint inverts (ink fill, card numeral, the ring turns card-coloured), days between take the ink at 16 %; empty-day numerals are tertiary; every heat step has a darker twin in dark. | Radius 9, no ring, accent selection border → the iOS states |
| ME-19 weekdays | fixed | «Пн Вт Ср…» capitalised, Inter 11 semibold, tertiary. | «пн вт» 10 medium secondary → as iOS |
| ME-20 filter line | fixed | «N поездок» 12 semibold secondary and «Сбросить» 12 bold accent inside the calendar card. | A `MeRow` → one line |
| ME-21 history cards | fixed | The journal draws the shared `ProfileTripCard`: «LVL n» held at the time, `d MMM`, region, the 22 × 14 sprite of the car and its name, «● Закреплено», a lock or the green globe, no «…». A hold opens the journey sheet; the average-speed setting and the real map snapshot stay. Pinning is offered to TalkBack as a custom action; the iPhone pins from the trip's own «…» (see the divergences). `tripMetrics` takes an optional average speed, `ProfileTripCard` a vehicle tag, a map slot and a hold label. | Title about 26 dp lower than iOS (the 40 dp «…» set the line) → the iOS meta line; long date and no car → short date, car, privacy glyph |
| ME-22 margins | fixed | Cards, tiles and pinned cards sit at 14, headings at 16. | Card edge 16.3 → 14 dp (iOS 14.6 pt) |
| ME-23 month heading | fixed | 24 heavy, tracking −0.5; 12 dp from the summary line to the first card. | Gap 16 → 12 dp |
| ME-24 repeated trips, folded | fixed | `ProfileRoutineTripsView`: a 34 dp `cardAlt` tile (radius 10) with `arrow.clockwise`, title 15 semibold that scales to 85 %, subtitle 13, `chevron.down` 11 that turns into `chevron.up`; opens in place. | `MeRow` with a right chevron → the iOS row |
| ME-25 repeated trips, opened | fixed | Rows 44 dp: title 14, «2 нояб» 12 tertiary, distance 13 semibold, a hairline inset 14, no chevron; seven rows and a centred accent «Ещё N» (14 medium). | Full-width divider and `MeRow`s → compact rows |
| ME-26 cover edge to edge | fixed | The 232 dp pager starts at the top of the screen under the status bar; the page does not pad the status bar when photos exist, and a strip of the page colour covers the clock once the cover has scrolled away. | Inset strip under a title → pager at x 0, width 411, top 0, height 232 |
| ME-27 page counter | fixed | `CoverPageCounter`: «1 / 4», Inter 12 semibold, white on near-black, a capsule, 16 from the left; 8 under the status bar on one's own cover and 54 on a stranger's, under the back button (one line in `PublicProfileScreen.kt`). | None → on both covers; the stranger's stands 50 dp under the top of the back button |
| ME-28 gear on the cover | fixed | A white 40 dp circle with the near-black outline gear at 18 pt, 16 from the right edge, 4 under the safe area. | 44 dp 30 % black with a white solid cog → as iOS |
| ME-29 cover panel | fixed | Avatar disc 46 (white at 16 %) with a 26 pt emoji, name 21 bold, the «LVL n» row, the car line «Name ›» 15 semibold and its caption on one centred row, stats 19 heavy 4 above; the car is pulled 82 up over the photo. | Bare emoji, small gold LVL, «Name · Make · Model» line → the iOS panel |
| ME-30 chosen background | fixed | Off the road the stage is min(0.67 × the width, 300) with no extra band; the avatar disc and the divider are white at 16 % and 22 %; the car name is 16 semibold. | Stage 300 + 20, opaque disc, accent divider, 17 → as iOS |
| ME-31 large text | fixed | From a font scale of 1.6 the name and the level go under the avatar row and the stats form a column 16 dp apart whose values shrink to 70 % before they clip. | Squeezed beside the avatar and clipped numbers → restacked |
| ME-32 zero trips | fixed | `FirstTripWelcomeCard` («Запишите первую поездку», a 52 dp accent car disc, 15 semibold title, 12 body, chevron, opens recording) and the garage and clubs already open; no stats, history header or calendar; a guest keeps the whole sign-in card. | Zero stats, history header and a «нет поездок» card → the iOS first run |
| ME-33 journeys in the history | fixed | `PublicJourneyCard` (the shared `JourneyCard`: the routes of every leg on one map, the pill, the legs' thumbnails) in the list and in the grid, where a journey is a card of its own between runs of tiles. | A `MeRow` in a card → the journey card |
| ME-34 grid | fixed | `ProfileTripTile`: a 72 dp map flush with the top, the title 12 bold and «2 нояб · 46 км» 10 semibold tertiary, padding 9/7, radius 14; two to a row, 8 dp apart. Long press combines into a journey. | Two full list cards 12 dp apart → tiles: left 14, gap 8, right 411 − 14 |
| ME-35 empty filter | fixed | `ProfileHistoryEmpty`: «Нет поездок за период» (or the no-match line) 17 semibold and «Сбросить фильтры» as a 44 dp accent text button in a card. | Filled full-width accent button → text button |
| ME-36 pins elsewhere | fixed | `PinsElsewhereRow`: a 13 secondary line without a card and an accent «Открепить» link in a 44 dp target. | A `MeRow` → the iOS line |
| ME-37 no name | fixed | `heroDisplayName`: a signed-in account without a name shows «Добавьте имя» in the secondary ink; «Вы» stays for a guest. | «Вы» in ink → the prompt |
| DR-01 drafts entry | fixed | `DraftsSection`: the «Черновики» heading and `ProfileDraftsRow` (40 dp accent tile with the tray, «4 поездки» over «Последняя вчера, 18:03», chevron); no drafts, no section. The row also shows on the first-run page. The old `profile_drafts` tag stays on the section. | 56 dp `MeRow` → 68 dp card |
| DR-02 | not mine | Done in wave 1 as SH-09 (the tab bar stays on the drafts list). | |
| DR-03 header tone | fixed | `headerGlass()`: a flat tint of the iPhone material, 239/239/236 at 0.8 in light and 46/44/46 in dark. | Over the page 254/253/252 → 241/240/237 (iOS 241/240/237); dark 33/33/36 → 40/39/41 (iOS 40/39/41) |
| DR-04 swipe plates | fixed | `SwipeRow`: a detached plate of 74 dp, 8 dp from the row, rounded like a card; the row moves 82; a full swipe past 55 % still acts. | Plate glued to the row, 74 → plate 74 wide, gap 8, row 82 |
| DR-05 empty block | fixed | `DraftsEmpty(windowHeight)` stands at 30 % of the window height from the top of the content. | One status-bar height too high on a device → 274.2 dp on a 914 dp window |

Everything keeps its behaviour and its accessibility: headings are headings, the two view toggles are a
radio group, the sections row says «развёрнуто / свёрнуто», the calendar days read their date, and a card
with a hold says so.

## Tests

New, and their numbers are the iPhone's, so the old geometry fails them: `MeScreenTests` (31: hero top,
the stats edges, the cover pager with its counter and gear, the prompt and the gaps between the blocks,
the compact sign-in row, the toggle and the three sections, the first run, the pins line, the drafts
card, the history header, the search bar, the calendar and its filter line, a history card's car and
margins, the hold, the TalkBack pin action, the grid, the month gap, the empty result, the hero at font
scale 2), `MeLogicTests` (15: name and caption rules, day looks, heat steps, month names),
`DraftsLookTests` (5: the header tone in both themes, the plates of 74 at 8, the empty block at 30 %),
`ProfileRoadSceneTests` (1: the fade, by pixels against the same drawing without it; a build without the
gradient fails it) and `ForeignHeroLookTests` (4: a stranger's counter 16 in and 50 under the back
button, none without photos, and the free road card in both themes).

Whole suite: 2,707 tests, 0 failures (app 945, designsystem 270, map 150, logic 678, model 95, data 242,
network 89, sync 127, location 111), against 2,629 before. In the app +78: the 56 above and 22
screenshot tests (11 new shots of the Я page and the hero, each in both themes). 48 baselines changed,
24 re-recorded and 24 new. I compared each changed render with the iPhone's shot of the same state, in
light and dark: the hero (vertical structure and text lines within 2 dp), the page, the history, the
calendar, the cards and tiles, the drafts list and its card; the photo glyph and the gear against crops
at 8×.

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew --no-daemon --offline -Proborazzi.test.record=true test
./gradlew --no-daemon --offline -Proborazzi.test.verify=true test lintDebug
```

Both pass.

## Where Android differs and why

`docs/divergences.md`: the «History cards» row (no historical level) is gone, since `TripLevelHistory`
feeds the card; «Trip previews in lists» says the history cards and tiles on «Я» draw a map snapshot;
«Drafts swipe actions» says the plates are detached; new rows «Drafts header tone», «Drafts on an empty
«Я»», «Pinning a trip», «Text size on «Я»» and «Я» glyphs».

## Shared files touched

- `ui/cards/TripCards.kt`: only `ProfileTripCard` and `tripMetrics` (an optional average speed, a vehicle
  tag, a map slot, a hold label); the feed, journey and other cards are unchanged.
- `ui/cards/ProfileCardVehicle.kt`: new, the car tag of the card.
- `feature/garage/VehicleStage.kt`: `VehicleSpritePlate` takes a corner radius (default as before).
- `feature/social/ui/PublicProfileScreen.kt`: three lines, outside the car block that SO-01 rewrites:
  the cover counter after `coverOverlay()`, and `RoadScene` delegating to `ProfileRoadScene`.
  `social_profile_cover_*` changed and `social_profile_road_*` are new, so the baselines of the two
  branches will need a re-record after the merge.
- `feature/drafts/ui/DraftsListScreen.kt`: the swipe, the header tint, the empty block and the `Я` row.

## Left, and not matched

- ME-14: the home question and the journey suggestion (see above).
- ME-12 on the stranger's card: the caption format and the 14 dp gap, with SO-01.
- Pinning has no button the eye can see until TD-10 puts «Закрепить в профиле» into the trip's «…» menu;
  TalkBack has the action on the card.
- The search field has no label for TalkBack beyond its placeholder.
