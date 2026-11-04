# w15: a tester's report on build 8

A creator couple tested build 8 on a Xiaomi phone (dark theme, three-button navigation) on 4 Nov 2026 and sent
two screenshots: «При нажатии кнопки Атлас, в середине карты на экране горит иконка и никуда не убирается» and
«не даёт войти в аккаунт для синхронизации».

## The icon in the middle of the Atlas

It was the empty Atlas: the `empty_map` drawing, «Карта пока пустая» and its line (iOS `MyMapView.emptyState`).
The person saw only the drawing, because the title was invisible:

- The words took the app theme's ink, but they stand on the fog, and the fog follows the map style, not the theme:
  «Туман» (the default) and «Клетки» lay the pale mist (`EEEEEC`) over the light map in either theme. In the dark
  theme the title (`EBEBEB`) vanished on it, and the drawing read as an icon stuck over the map. The iPhone has the
  same defect (`AtlasTheme.ink` is adaptive, the mist is not), owners with trips never see this state.
- The screenshot tests could not show it: without MapLibre the Atlas stub painted the THEME's fog colour, dark in the
  dark theme. It now paints the style's fog, so the dark Atlas baselines show what the phone shows (re-recorded).
- A three-button navigation bar lifts the sheet and the «где я» button by its height; the drawing stayed where it
  was, and its last line ran under the button.

Fixed in `AtlasScreen.kt`:

- `emptyMapInk`: the words take the fog's colours (light palette on the mist, dark on «Ночь», light without fog).
- `emptyPlacement`: iOS centring, with the bottom held 16 above the «где я» button.
- The empty map yields to the person: the first gesture on the map or a tap on «где я» fades it out (200 ms, none
  under «Remove animations»), so the dot of «где я» is not left under the drawing. It comes back with the screen.
  This is new against iOS and listed in `docs/divergences.md`.

Tests: `EmptyMapInkTests`, `WorldWithoutTripsScreenshotTests.locatingYieldsTheEmptyMap`. Emulator (API 35, dark,
Russian): readable title on the mist with gesture and three-button navigation, the last line above the button,
«где я» and a pan both fade it out.

## Sign-in with Google

Not a code bug: Google sign-in had never been configured. Release builds had no Web client ID (the sheet failed at
once with «Не получилось войти через Google»), there was no Google Auth Platform project, and production had no
`/auth/google/login`.

Done on 4 Nov 2026 (`docs/sign-in.md`, «What the owner has to do», steps 1–3):

- Google Auth Platform project in production (External; `openid`, `email`, `profile` need no verification), the Web
  client, and Android clients for `com.onezee.triptrack` (the three certificates of the Play app signing key and the
  upload key) and `com.onezee.triptrack.dev` (the debug key of the owner's Mac).
- Backend `work/identity` deployed: the schema SQL first, then the image; `GOOGLE_CLIENT_IDS` on the server.
- The release build reads `GOOGLE_WEB_CLIENT_ID` from the machine-local configuration, never from the repo.

Checked: a debug build reaches Google's account chooser («to continue to TripTrack»); closing it leaves no error.
Not checked here: a full sign-in with a real Google account, which needs a person's phone.

## Places groups followed the phone's calendar

`WorldFeature` built `PlacesTabViewModel` without a clock, so «Сегодня», «На этой неделе» and «Ранее» were counted
from the phone's date: on 4 Nov the test library changed groups and `PlacesDesignTests` failed. The tab now counts
from the trips' clock, like the rest of the world screens.

## Someone else's map was misty

The same stub fix showed that someone else's map (and a car's map) drew the pale mist: `PublicMapViewModel` started
from the default «Туман». iOS keeps these maps on the night look (`MyMapRepresentable.appearance`), and so does
Android now; the light-theme `social_map_*` baselines show the night fog.
