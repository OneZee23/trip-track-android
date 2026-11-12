# W30: draggable hidden-route locks

The editor now uses route handles for start, end and section boundaries. It supports drag snapping, parked-stop states, edge detents, section creation/removal with undo, live preview, accessibility actions and focused entry from the owner's trip map. The stored `TripHiddenParts` values and server payload are unchanged.

The Android port follows iOS `TripHiddenParts.swift` and `HiddenPartsGeometry.swift` for resolution and odometer prefix, `HiddenPartsEditorModel.swift` and `HiddenPartsEditorView.swift` for handle math and editing, and `TripDetailView.swift` for lock-pin entry. Strings use `AppStrings+HiddenLocks.swift` and `Translations+HiddenLocks.swift`.

When MapLibre cannot initialize, Android draws a projected route without map tiles and keeps editing active. This fallback is recorded in `docs/divergences.md`. No wire or storage differences were introduced.

The Robolectric API 29/30 editor check and all automated tests pass. A physical POCO F1 session remains to be done.

Verification:

- `./gradlew --no-daemon --offline -Porg.gradle.java.installations.paths=$JAVA_HOME testDebugUnitTest` — 2,467 tests passed.
- `./gradlew --no-daemon --offline -Porg.gradle.java.installations.paths=$JAVA_HOME :core:logic:test` — 709 tests passed.
- `python3 tools/strings/validate.py` — passed; 1,980 strings and 33 plurals per language.
- `python3 -m unittest discover -s tools/strings -p 'test_*.py'` — 29 tests passed.
- `python3 tools/strings/generate.py --check --ios /Users/onezee/OneZeeProjects/trip-track-095` — passed.
