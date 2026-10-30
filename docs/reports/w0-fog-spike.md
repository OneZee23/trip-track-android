# w0-fog-spike: report

Branch `w0-fog-spike`. Decision and numbers: [docs/fog-spike.md](../fog-spike.md).

## Result

MapLibre Native for Android, OpenGL ES build 13.7.1, OpenFreeMap tiles, and the fog as a native
custom layer that runs the iPhone 0.8.0 Metal passes in GLES 3.0 inside MapLibre's frame
(candidate A). B (fill polygon with holes from JTS) and C (raster tiles drawn on the phone) were
built and measured and fail the bar; Google Maps was evaluated without building (no way to draw
inside its frame, and billing cannot be opened from Russia).

The open risk is GPU fill on real phones. The APK for the owner's phone is
`spikes/fog/out/fog-spike.apk` (gitignored; rebuild with the commands below).

## What was ported, for the spike only

| Android (spikes/fog) | iOS |
|---|---|
| `cpp/fog_layer.cpp`: coverage and composite shaders, frame, chunk translation, cells | `FogShaders.metal`, `FogFrameEncoder.swift`, `FogMetalVeil.swift` (frame parameters), `FogCellGrid.swift` |
| `fog/FogRules.kt` | `FogVeilRenderer.swift` (`streetHalfWidthMetres`, `haloHalfWidthPoints`, `haloHalfWidth`, `lod`), `FogMetalVeil.featherRatio`, `FogMesh.chunkSize` |
| `fog/FogStyle.kt` | `FogVeilPainter.Palette` (`mist`, `night`), `AtlasMapAppearance.Style` |
| `fog/FogMesh.kt` | `FogMesh.swift` |
| `data/RevealLite.kt` (trimmed) | `RevealGrid.walkCells`, `RevealBuilder.patches`, `RoadFog.decimate`, `RevealedLayer.stitch` |
| `data/Datasets.kt`, `ios` dataset | `DebugMapSeed.runHangStressIfNeeded` (`-seed-hang-stress`) |

None of this is production code: no tests, no strings resources, plain Android views. The real
port of the fog and of the reveal builder, with the iPhone tests, belongs to wave 2.

## Commands

```bash
cd spikes/fog
export ANDROID_HOME=~/Library/Android/sdk
# JDK 21 for the toolchain: here WebStorm's bundled JBR 21, to avoid a 190 MB download.
export JAVA_HOME=/Applications/WebStorm.app/Contents/jbr/Contents/Home
./gradlew :app:assembleDebug -Porg.gradle.java.installations.paths=$JAVA_HOME
cp app/build/outputs/apk/debug/app-debug.apk out/fog-spike.apk
adb install -r out/fog-spike.apk
scripts/measure.sh bench out/measure        # one pass per candidate and dataset
scripts/measure.sh rounds out/rounds 3      # three interleaved rounds of none, A, B, C
scripts/measure.sh shots out/measure        # screenshots
python3 -I scripts/summarise.py out/rounds/bench --rounds
python3 -I scripts/composite.py out/measure/shots ../../docs/assets/fog-spike out/look
```

Results: `BUILD SUCCESSFUL`; all bench runs completed: 12 single runs, 24 in the three rounds,
4 with fine chunks only (A full and half resolution) and 6 with coarse chunks (A full, A half, map
alone); tables in `docs/fog-spike.md`, raw JSON and the host load per run in
`docs/assets/fog-spike/bench/`. Two screenshots timed out: B at region and country zoom on the
stress set, where B crashed with `OutOfMemoryError` (576 MB large heap) in the JTS union.

## Where Android differs from the iPhone, and why

- The mid and far levels use 2^20 map point chunks; the fine level keeps 32,768. A draw call per
  chunk costs more on GL than on Metal: about 900 chunks per level on the stress set meant
  9-11 ms of CPU per zoomed-out frame on the emulator, and 9 chunks bring it to 0.5 ms. Precision
  stays under a thousandth of a screen point. This goes into `docs/divergences.md` when the port
  adopts it.
- Half-resolution coverage (`Cov half`, off by default) shades a quarter of the coverage fragments
  and samples bilinearly. It is not part of the iPhone picture, only an option to measure on
  phones.
- The screen scale is the length of the frame's first column. iOS divides `bounds.width` by
  `visibleMapRect.width`, which shrinks when the map is rotated; without rotation the two agree.
- There is no attribution carve. MapLibre's logo and attribution are Android views above the map,
  so the fog never covers them, and the iPhone carve is switched off anyway (owner, 26 September).
- There is no display link. MapLibre renders only when something changes and calls the layer
  inside that frame, so the "rest frame" logic and the presentation sync of iOS have nothing to do.
- Points are dp: 18 pt becomes 18 dp. Widths are computed in dp and textures in pixels, as iOS
  does with points and pixels.
- `RevealLite` is trimmed: no regions, no persistence, tiles kept in memory. On the iPhone stress
  seed it produces 51,445 segments (iOS docs: 51,444) and 267 far chunks (iOS: 267).

## Environment notes for the orchestrator

- The android-36 system image is not installed: `system-images/android-36/google_apis_playstore`
  holds only an `.installer` folder from a failed install in September 2025. The spike used a new
  AVD `FogSpike_API35` on the installed android-35 arm64 image.
- Build-tools 36.0.0 (required by AGP 9.5) is now in the SDK. Gradle's own downloads of it were
  corrupted twice: several Gradle daemons (two of mine, one from another agent) wrote the same
  temp file at once through a proxy that drops connections. It was downloaded with resume,
  checked against the SHA-1 in Google's repository index and installed with its `package.xml`.
- The network was down between about 21:10 and 21:45 MSK; after that the emulator resolved hosts
  only when started with `-dns-server` pointing at public resolvers.
- Host load average was 18-31 on 12 cores during all measurements, with other agents building.

## Left for later

- GPU time on a real phone (the APK records it), and the choice of fill fixes from that run.
- The live reveal circle, the style crossfade and the poster (`FogOffscreen`) in GL.
- Instrumented pixel tests in place of `FogMetalPixelTests` and `FogMetalCarveTests`.
- Reachability of OpenFreeMap from Russian networks.
