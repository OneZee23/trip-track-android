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
JDK already on the machine, add `-Porg.gradle.java.installations.paths=<jdk home>`. The APK is
debug-signed, arm64 only, and installs next to the real app (`com.onezee.triptrack.fogspike`).

## Run

The app opens on Krasnodar with candidate A, the small dataset and the fog style. The buttons
along the top:

- `Fog A/B/C/NONE` switches the candidate and restarts the screen: A is the native custom layer,
  B the polygon fill, C the generated raster tiles, NONE the map alone.
- `Data` switches between `small` (a dozen drives), `stress` (400 trips × 2,000 points) and `ios`
  (the iPhone `-seed-hang-stress` formula), also with a restart.
- `Style` cycles fog, night and cells (cells only in A).
- `street`, `city`, `region` and `country` move the camera.
- `Cov full/half` draws A's coverage at full or half resolution.
- `Bench` runs the scripted pan, pinch and zoom. The summary at the bottom left shows frame times
  and, for A, CPU and GPU time per frame (GPU only where the driver has a timer query).

Everything can be driven by intent extras, which is how `scripts/measure.sh` runs it:

```bash
adb shell am start -n com.onezee.triptrack.fogspike/.MainActivity \
  --es candidate a --es dataset stress --es style fog --es preset city --ez bench true
```

`scripts/measure.sh` runs the benches (`bench`, or `rounds` for repeated interleaved rounds) and
the screenshots (`shots`) into `out/`. `python3 -I scripts/summarise.py <folder> [--rounds]`
prints the tables, and `scripts/composite.py` builds the images in `docs/assets/fog-spike/`.
