# w20: the keyboard covered the fields

The tester couple of w15 signed in on build 13 (4 Nov 2026) and wrote back: «появилась проблема с клавиатурой,
перекрывает окно с вводимым текстом. Когда ввожу имя или Юзернейм». Their screenshot: «Мой профиль» with the
keyboard up and no field anywhere.

## Why

Every field in a house sheet was under the keyboard, not only these two. `ContentSizedSheet` is a Compose dialog
drawn edge to edge (`decorFitsSystemWindows = false`), and for such a dialog Compose asks the system for no resize at
all: `SOFT_INPUT_ADJUST_NOTHING` from Android 12 (`DialogWrapper`, compose-ui 1.12.1), `ADJUST_RESIZE` below it. The
sheet stood on the bottom edge whatever the keyboard did, so the keyboard covered it whole: the name, «О себе», the
username, the trip's title and description, checkpoints and segments, the journey's name, the car form, a place's
name, the DRIVE2 link, comments, the report and the companions' search. Three sheets padded their own content by
the keyboard: comments and the trip summary's description, which floated their content a navigation bar's height
over the keys, and the car form, which padded inside its scroll: that lets the list scroll further but does not
make the viewport shorter, so a focused field low in the form was not moved above the keys.

The same was true of a field in a screen's own list: the history search on «Я» stayed under the keyboard when it
sat in the lower half of the screen, and the last people found in «Найти людей» could not be scrolled to.

Nobody saw it before a tester did because the emulators on this Mac have a hardware keyboard, and with one attached
Android shows no on-screen keyboard at all. Every field looked fine.

## Fixed

- `ContentSizedSheet` stands on the keyboard, as an iPhone sheet rides up on it: the sheet's layout area ends on the
  keyboard's top (`imePadding()`), so a `.content` sheet sits right above the keys and a `.large` one gets shorter by
  their height and scrolls what it holds. The navigation bar's padding is a `windowInsetsPadding` now, consumed under
  the keyboard (the keyboard's inset already holds the bar). The three sheets' own keyboard paddings are gone.
- «Я» and «Найти людей»: the list ends on the keyboard, so the focused field scrolls into view.
- `Modifier.revealOnFocus()` (designsystem): once a field has the focus and the keyboard is up, its whole frame
  scrolls into view, not only the line of text. On `MeField` (the car form, the username's old field) and the
  history search.
- «Собрать путешествие»: the trip list gives way to the keyboard (`weight(1f, fill = false)` under its 280 cap), so
  the name field and «Создать путешествие» stay in sight while the name is typed.
- The username editor is the iPhone's (`UsernameEditorSheet`): the name editor's Отмена · Юзернейм · Готово over a
  bordered field with the «@» glued to the handle, the rules and a counter, a line saying what is wrong, and the
  caret in the field at once with a Latin keyboard. Build 13 opened a bare «@» with no caret and a button at the
  bottom, unlike the name editor one row above it. The header is shared: `ProfileEditorHeader`.

## Checked

Emulator API 35, three-button navigation, dark theme, Russian, on-screen keyboard forced
(`adb shell settings put secure show_ime_with_hard_keyboard 1`); screenshots in the QA folder `w20`:

- reproduced on build 13's code: «Имя» opens the keyboard and no field;
- after the fix, above the keyboard: the name, the username (new editor), «О себе», the trip's title and
  description, «Собрать путешествие» with its button, the car form's name, plate and fuel price (number pad), a
  place's name with «Отмена» and «Сохранить», the trip summary's description, the Places search, the people search,
  and the history search when it sits at the bottom of «Я».
- Comments, the report and the companions' search need a signed-in account; they use the same sheet and were not
  seen on the device.

Tests:

- `SheetKeyboardTests` (designsystem): with the keyboard up the sheet stands on it and its content ends on the
  keyboard's top, without a bar's padding; without it the ground runs to the bottom edge and the content clears the
  bar; a `.large` sheet keeps its top and ends on the keys; a sheet drawing into the bar still stands on the keyboard.
  Without `imePadding()` three of the five fail.
- `MeInteractionTests`: the username editor takes the focus, normalises «@Road.Max» to «road.max» and saves it; it
  says «Слишком коротко» and «Только латиница…» and keeps Готово off; an unchanged handle says nothing.
- `./gradlew :core:designsystem:testDebugUnitTest :app:testDebugUnitTest` with the screenshot baselines.

## Not done

- Comments, the report and the companions' search were not seen with a real keyboard (they need a signed-in
  account on the emulator).
- Fields are still reached one tap at a time: the car form's keyboard has no «Далее» from one field to the next.
