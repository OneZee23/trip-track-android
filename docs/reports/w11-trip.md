# w11-trip

Wave 2b of the design-parity fixes: the trip screen, the feed and the social screens. The reference is
the 3 November 2026 audit of 50 iPhone/Android pairs (`differences.md`: FD-01 to FD-06, TD-01 to TD-13,
SO-01 to SO-06 and SH-02). Clone `w11-trip`, master at `a46b2e1` (wave 1), not pushed. The iPhone app
was only read. Wave 1's shared pieces are reused as they are: `CustomNavBar`, the sheet look, the map
chrome glass, `HouseIndication`, the glyph optical scale, and the owner's reaction icons, tally pill,
picker and list.

## Per audit item

| ID | Status | What changed | Before → after |
|---|---|---|---|
| FD-01 header | partly | `FeedHeader` draws «Лента», then a 34 dp circle (card fill, shadow 0.03 / 2 / y 1): the bell (22 dp) when signed in, which opens the Inbox, or the «Войти» capsule (14 bold, padding 16 / 9) for a guest. `FeedTab` takes the shell's sign-in door; with neither a door nor an account service a guest has no button. The search circle is built (`onSearch`, 18 dp magnifier) but not drawn: Discover is not ported and a circle that goes nowhere is a dead tap | Title alone → title and bell (34 dp) or «Войти» (35 dp tall, as iOS). The Inbox was unreachable; now it opens from the bell |
| FD-02 card menu | fixed | Every card ends its author row with «…». Someone else's: «Поделиться», «Пожаловаться». Own: «Редактировать» (opens the trip, as it did), «Поделиться», «Сделать приватной», «Удалить». Share mints the link into the compact link sheet, «Пожаловаться» opens the report sheet; a guest is sent to sign in for either | Foreign card: no «…» → two rows. Own card: no share → share in iOS order |
| FD-03 avatar frame | fixed | The 36 dp avatar carries `Modifier.avatarFrame(…, 2.dp)` from `author.avatarFrame`; a frame this build does not know (the audit fixture's short «gold») reads as none | Plain disc → the gold ring; read back from the pixels |
| FD-04 comment glyph | fixed by wave 1 | Nothing in this wave: the redrawn `bubbleRight` and the optical scale already give it | Measured 17.5 × 16.0 dp on the feed shot (iOS 17.3 × 16.3 pt) |
| FD-05 guest «Подписки» | fixed | The same sign-in door as FD-01 gives the page its «Войти» (padding 22 / 16, radius 14, accent shadow 0.3) | Page without the button → with it |
| FD-06 guest reacts | fixed | A reaction, the «+», a hold or the share of a guest opens `SignInPromptSheet` and remembers what was tapped (`PendingSocialActions`, iOS pendingSocialAction). After a real sign-in it is done once; a sheet closed without signing in, a session that is gone again, or a newer prompt drops it | Info toast → sheet, then the reaction |
| TD-01 top bar (and SH-02) | fixed | `TripTopBarActions`: on one's own trip share and «…» are one capsule (`MapChromeCapsule`: two `MapChromeCell`s of 44 × 36 and a 0.5 dp divider) on the same glass as the circle, solid card-alt once the bar has filled in. On someone else's public trip share stands alone as the 36 dp circle; a private foreign trip offers none (iOS `canOfferShare`). Share mints the link (a private own trip is published first and shared after it, a guest is asked to sign in) | One «…» circle, nothing on foreign trips → capsule about 88 × 35.4 dp with its edge 12 dp from the screen (iOS 88 × 36 pt, 12) |
| TD-02 car chip | fixed | `ChipChevron` (10.8 dp, 50 %) ends the own chip and a foreign chip that has a car id. The own chip opens its passport (`feature.openVehicle`, wired in `AppGraph` to the Me stack), the foreign one the public passport. A trip without a car has nothing to open | Label → button with a chevron; chevron 4.6 × 7.6 dp |
| TD-03 movement card | fixed | The label row fills the card, so `SpaceBetween` pushes «Без движения» to the inner edge | Label ended 64 dp from the edge → on the padding (0 gap to the inner edge) |
| TD-04 companions | not done | The section, picker and roster are a feature (server calls, three screens). A card that opens nothing would be a dead tap, so it is left out and listed in `divergences.md` | Not changed |
| TD-05 description prompt | partly | `DescriptionPrompt` (dashed accent box, radius 12, dash 5 / 4 at 35 %, `squareAndPencil` 18 dp, «Добавьте описание поездки») on one's own trip with nothing written, under the «Описание» heading. It opens `TripDescriptionSheet`: «Отмена · Описание · Готово», a 108 dp field, «n / 500», the comment content filter. Someone else's trip has no prompt | No prompt → prompt and sheet. A written description still cannot be edited: the iOS edit sheet (name, description, car, access) is not ported |
| TD-06 photos heading | fixed | The «+» is a 20 dp footprint (its 24 dp artwork may pass it by two), as iOS keeps its 44 pt target out of the layout (−12 padding) | Heading to strip 54 dp → about 31 (the 20.6 line plus the 10 gap); «+» 20 × 20 on the heading's line |
| TD-07 achievements | partly | `TripAchievementsSection` after the photos: «Достижения поездки», cards three across at 10 dp (two across from font scale 1.5, iOS accessibility sizes), engraving 56, name Inter 11 semibold (3 lines, shrinks to 0.7), padding 14 / 6, radius 14. A tap opens the house badge card. An id this build does not know is skipped | Nothing → the grid; card 120.7 dp tall (iOS 120). The card lacks the earned-on date, the trip's name, the personal figure and the earn count (`BadgeManager.lastEarnedDates`, `Badge.recordValue` are not ported) |
| TD-08 share card and flag | fixed | The details column ends with the reactions and the discussion: no «Поделиться» card, no report flag. Report stays on the cards and the profile, as on iOS | Row with a share card (and a flag on foreign trips) → nothing between reactions and discussion |
| TD-09 discussion | fixed | `TripDiscussion` rebuilt on the iOS layout: `DetailSectionHeader` (17 bold) heading 10 above one card of radius 16 without padding; rows of a 34 dp avatar column, the name (13 semibold, PRO badge) with «· 2 ч» (11) on one baseline, the text (14) beside the avatar, 1 dp dividers; the write row is a bordered pill (card-alt, 1 dp border, radius 12) with a 30 dp accent-12 % disc and the send glyph; «Всё обсуждение · N ›» is 32 tall with a chevron; the read-only line is Inter 12 at 14 / 12. The thread sheet follows: rows in one card, the iOS-style composer. All test tags kept | Card padded 12 with 10 gaps, no dividers, text under the name at the card edge → the iOS rows |
| TD-10 owner menu | partly | «Закрепить в профиле» / «Открепить» (public, finished, not a draft, with the showcase wired to `feature.pins`, three places, a fourth asks to free one). «Редактировать» is left out: it opens the edit sheet. The thumbtack is drawn with the flag, as the Я journal menu does | Five rows → six on a public finished trip. iOS has seven |
| TD-11 journey row | fixed | Someone else's leg shows `JourneyLinkRow` from the feed's `social.journey` and opens their journey (`TripsDestination.SocialJourney`) | No row → the row of one's own trips (28.6 dp tall) under the chips |
| TD-12 suitcase | fixed | `suitcaseFill` box 15 → 14.4 dp: iOS draws 12 pt bold at 14.3 × 12.0 pt, and the box is 1.2 × the point size | Ink 11.0 × 10.3 → 13.3 × 12.6 dp (area equals iOS) |
| TD-13 load failed | fixed | Header: 34 dp dark back circle, «Поездка» 16 bold centred, a 36 dp balancing spacer; below, the 108 dp ring (card-alt stroke 10) with 26 under it, the title, the body and the retry; one spacer above the block and two below | Back only, an empty box, block centred → iOS |
| SO-01 car on the cover | fixed | On a cover the car block is pulled up 82 dp over the photo (`pulledUp`: shifts and shortens the layout), the stage 250 wide in a 155 frame aligned to the bottom (a taller sprite rises over the photo), and the name (15 semibold), the chevron and the caption (12) stand on one centred line. The caption is now iOS's `VehicleCatalogCaption` form «Haval Jolion · 2022 · 143 л.с.»: make and model one name, then the year, then the catalogue power (resolved on the worker in `PublicProfileViewModel`) | Block below the photo, 270 dp of panel → 99 dp (181 less 82). Caption «Haval · Jolion · 2022» → «Haval Jolion · 2022 · 143 л.с.» |
| SO-02 numbers | fixed | Under a cover photo the numbers are 19 heavy; the plain card keeps 22 bold | Always 22 bold |
| SO-03 journal rhythm | fixed | The list has no uniform spacing: each item carries the gap that precedes it (header 10 above its content, pinned cards, chapter headers and cards 12 apart, 16 around the sections and journeys) | 16 everywhere |
| SO-04 layout toggle | fixed | Two 26 × 22 frames 2 dp apart in a padding of 2 | Centres 45 dp apart, 32 from the edge → 28 and 25 (iOS) |
| SO-05 hero card | fixed | The free card stands 10 below the bar (the card's 4 and the hero section's 6) | 12 dp from the bar's circle → 22 (iOS 23) |
| SO-06 country line | fixed | One's own preview prints «🇷🇺 Россия» (12, secondary) under the level, from `SocialFeature.ownCountry` (the device setting; the profile DTO carries none, so someone else's has none, as on iOS) | No line → line |

## Tests

New, and each fails on the old behaviour (mutated one at a time and watched fail: TD-03 `fillMaxWidth`,
TD-06 the 44 dp box, FD-03 the frame, SO-01 the pull, TD-09 the dividers, TD-07 two columns and the
accessibility threshold, TD-01 the share, FD-02 the report row): `TripScreenDesignTests` (32: capsule geometry, foreign circle, private
foreign trip, share minting and the chooser intent, guest, publish first, no share card, achievements,
description prompt and sheet, pin rules and the limit, journey row, car chips, stopped label, photos
heading, load-failed state), `FeedHeaderMenuTests` (15: bell, guest door, menus per card kind, share,
report, guest prompts, avatar ring, four `PendingSocialActions` rules), `ForeignProfileLayoutTests`
(12: pulled-up car and its caption, numbers, journal gaps, toggle, hero card, country rules),
`TripDiscussionLayoutTests` (7: age words, teaser, dividers, write row, pill, empty). Screenshots: 11 new
trip and feed frames and 3 new social ones, each light and dark, and the re-recorded trip, feed, shell
and profile frames. I compared the changed screens with the iPhone shots: the trip top, the movement /
description / photos / achievements column, the owner menu, the foreign leg with its journey row, the
discussion, the feed header and menus, and the foreign cover with its car.

Whole suite on the committed code, `./gradlew --no-daemon --offline --rerun-tasks
-Proborazzi.test.verify=true test lintDebug`: BUILD SUCCESSFUL in 4 min 29 s, 2,719 tests, 0 failures, none
skipped (app 957, core:logic 678, core:designsystem 270, core:data 242, core:map 150, core:sync 127,
core:location 111, core:model 95, core:network 89). That is +90 over wave 1, all in the app: 66 behaviour
tests and 24 screenshot runs (12 tests, each in light and dark). 38 screenshots were re-recorded and 28 are
new (14 frames, each in light and dark). Lint: 0 errors in every module (app 76 warnings and 2 hints, designsystem
42 warnings, map 6 warnings and 1 hint, location 2 warnings); the four warnings in files this wave touched sit on lines it did not
write.

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew --no-daemon --offline -Proborazzi.test.record=true test
./gradlew --no-daemon --offline -Proborazzi.test.verify=true test lintDebug
```

## Where Android differs and why

`docs/divergences.md`: «Own vehicle on the trip screen» now says both chips open their passports and
where the owner's lives; new rows «Trip share» (the link, not the poster), «Trip editing» (no edit
sheet, the description-only sheet, the flag for the thumbtack), «Trip achievements» (the badge card
without the earn lines), «Trip companions» and «Feed header» (no search circle). «Publish sheet» points
to them.

## Left, and not matched

- Companions (TD-04), Discover and the search circle (FD-01), the edit sheet and «Редактировать» on the
  trip menu (TD-05, TD-10), the poster share, the «Поездка закрыта» state and the feed banners: whole
  features, outside this wave.
- The badge card's earn lines (TD-07).
- Glyph stand-ins: the thumbtack is the flag, the discussion's send is the social set's paperplane, not
  SF `paperplane.fill`.
- The «1 / N» counter on a foreign cover is ME-27, not this wave. The cover avatar's frame ring could not
  be shot: the audit fixture's frame id «gold» does not parse, so it was checked in the code.

## Shared files touched

`app/src/main/kotlin/com/onezee/triptrack/ui/DetailComponents.kt` (new `MapChromeCell`,
`MapChromeCapsule`, `MapChromeDivider`; the suitcase of `JourneyLinkRow`),
`.../ui/cards/TripCards.kt` (the feed card's author row only: the avatar frame and its tag),
`.../AppGraph.kt` (wires `feature.pins`, `feature.openVehicle` and `ownCountry`),
`.../feature/TripsFeature.kt`, `.../feature/TripsTabs.kt`, `.../feature/social/SocialFeature.kt`.
Nothing under `feature/me` or `feature/drafts`.
