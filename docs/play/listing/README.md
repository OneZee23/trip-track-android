# Play listing

Paste each UTF-8 file into the matching Play Console field, without the final file newline.
Title: 30 characters; short description: 80; full description: 4,000.
The checker counts UTF-16 units conservatively, including spaces and internal line breaks.

| App language | Play locale | Language in Console |
|---|---|---|
| en | en-US | English (United States), default |
| ru | ru-RU | Russian |
| de | de-DE | German |
| es | es-ES | Spanish (Spain) |
| fr | fr-FR | French (France) |
| it | it-IT | Italian |
| pl | pl-PL | Polish |
| id | id | Indonesian |
| tr | tr-TR | Turkish |
| fil | fil | Filipino |
| uk | uk | Ukrainian |
| kk | kk | Kazakh |
| pt | pt-BR | Portuguese (Brazil) |

The iPhone's `Translations+PT.swift` explicitly says Portuguese (Brazil). Its copy uses
“você”, “app”, “Garagem” and “Sincronização na nuvem”; Android's locale facade uses
`pt_BR`. `values-pt` is the Android resource qualifier, not a Play locale code.
The English and Russian originals are inline in `AppStrings*.swift`; the other eleven
languages use `Translations/Translations+XX.swift`.
Play's [supported locale list](https://support.google.com/googleplay/android-developer/answer/9844778?hl=en)
confirms the mapping.

The feature set is the current Android diary: manual/automatic recording, background recording,
trip map, replay, statistics, photos/notes/checkpoints, GPX/CSV export, Atlas, Garage, levels and
badges, optional Cloud Sync. Recording works offline; loading maps needs a connection.
Social screens and Places runtime are being completed in other branches and are omitted.
There are no claims about purchases, watch apps, widgets, car displays or discoveries.

Copy references in the read-only iPhone checkout: `docs/store/aso-live.md` (names and road-journal
positioning), `docs/store/screenshots-2026-10.md` (route, Atlas, photos, statistics, Garage,
achievements), `docs/releases/0.8.4/app-store.md` (which core features remain free), and
`docs/releases/0.6.2/app-store-locales.md` (localized vehicle/journal terminology).
Android availability was checked against `docs/port-status.md`, `docs/reports/` and the actual
feed, trip-detail, replay, export, Atlas, Garage, profile and account screens.

```sh
python3 tools/play/check_listing.py
python3 -m unittest discover -s tools/play -p 'test_*.py' -v
```

The workflow `.github/workflows/android.yml` runs both commands. The checker needs no iPhone
checkout, Gradle, network or third-party Python packages. Final copy should receive a native
speaker pass before public release, especially fil and kk; automated checks verify completeness
and limits, not language quality.
