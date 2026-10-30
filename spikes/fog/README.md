# Fog spike

Throwaway prototype that decided how the Android app draws the Atlas fog. Results and the
decision are in [docs/fog-spike.md](../../docs/fog-spike.md).

## Build

Standalone Gradle build, independent of the app's build at the repository root.

```bash
cd spikes/fog
export ANDROID_HOME=~/Library/Android/sdk
./gradlew :app:assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk out/fog-spike.apk
```

Needs a JDK 21 for the toolchain. If Gradle does not find one it downloads it; to point it at a
JDK already on the machine, add `-Porg.gradle.java.installations.paths=<jdk home>`.

## Run

The app opens on Krasnodar with the selected candidate and dataset. The buttons along the top:

- **Fog A/B/C/NONE**: A is the native custom layer, B the polygon fill, C the generated raster
  tiles, NONE the map alone (restarts the screen).
- **Data**: `small` (a dozen drives), `stress` (400 trips × 2,000 points), `ios` (the iPhone
  `-seed-hang-stress` formula). Restarts the screen.
- **Style**: fog, night, cells (cells only in A).
- **street / city / region / country**: camera presets.
- **Bench**: the scripted pan, pinch and zoom; the summary appears at the bottom left.

Everything can be driven by intent extras, which is how `scripts/measure.sh` runs it:

```bash
adb shell am start -n com.onezee.triptrack.fogspike/.MainActivity \
  --es candidate a --es dataset stress --es style fog --es preset city --ez bench true
```

`scripts/measure.sh` runs the whole set of benches and screenshots into `out/measure`;
`python3 -I scripts/summarise.py out/measure/bench` prints the tables.
