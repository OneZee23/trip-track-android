# w14: real maps on cards, map labels, background location

Found on build 7 on a device (3 Nov 2026), checked on the emulator against iOS 0.9.3.

## Real maps under routes

The owner on the feed: «карты не видно, только линия». iOS draws every trip card over a real street
map (`MapSnapshotPreview.swift`, his decision of 6 Aug 2026); Android drew `LightRoutePreview`, which on
iOS is only the placeholder while the snapshot loads. The audit's iOS reference came from a simulator
without MapKit tiles, so the placeholder passed for the design.

- `ui/MapSnapshotPreview.kt` ports it on MapLibre's `MapSnapshotter`: the 0.6 line under a shimmer while
  loading, `mapSlash` at the bottom end after a first miss, retries after 1.5 s and 4 s, a 350 ms fade in.
  The region is the route's bounds × 1.4 with a 0.005° floor; the line (accent .75, 2 dp), the green start
  dot and the checkered finish flag are drawn as iOS draws them.
- One process-wide 50 MB LRU keyed by trip, theme, pixel size, a hash of the route and the label
  language; cleared on low memory. At most two snapshots render at once; leaving composition cancels one.
- The style follows the theme (`LiveMaps.style`); liberty's POI layers (`poi_r20`, `poi_r7`, `poi_r1`,
  `poi_transit`) are hidden, like MapKit's `.excludingAll`.
- Call sites as on iOS: feed and «Я» trip cards (`TripCards.kt`), history and public-profile tiles, stats
  memories, journey legs and cards. `LightRoutePreview` stays where iOS keeps it.
- Without live maps (Robolectric, no native library) nothing snapshots and the line stays, so
  screenshots are deterministic.

## Map labels in the app's language

OpenFreeMap's styles print non-Latin names twice («KUZNECHNAYA STREET КУЗНЕЧНАЯ УЛИЦА»). `MapLabels`
rewrites every name label on style load, live maps and all three snapshotters: the app's language, then
the Latin spelling for Latin-script readers, then the local name. Road numbers stay.

## Background location

Onboarding and the recording permission sheet opened the app details page, and the person had to find
Permissions → Location. Background location is now requested like the other permissions: from Android 11
the system answers with the app's own location page. The hint says only which choice to make (13 languages).

## Someone else's trip

`/social/trip` returns the track thinned to about a kilometre a step (395 points over 408 km, median
1081 m), and Android split it at RouteGeometry's 1 km gap: the line fell apart into short coloured dashes.
iOS draws such a trip from its preview, one solid line. A server track is now one line too
(`isPreviewRoute` for every remote-backed trip), keeping its speed colours. Test:
`TripDetailStateTests.remoteTrackThinnedPastTheGapRuleDrawsAsOneLine`.

## Checked on the emulator, build 7

- Onboarding in Russian, every system question in order, nothing stacked.
- A simulated drive at 50 km/h: recording in the foreground, in the background, with the screen off in
  forced Doze (the notification kept counting), and after `kill -9` of the process: the service came back
  in about 20 s and the reopened screen picked up the running trip.
- Not an app issue: a 19 s emulator stall delivered queued fixes with fresh timestamps, so one trip got a
  max speed of 40 m/s. The recording logic is the iOS one; real GNSS fixes carry their own times.

## Tests

`MapSnapshotRulesTests` (region, flag direction, ladder, cache key, eviction) and `MapLabelsTests`.
