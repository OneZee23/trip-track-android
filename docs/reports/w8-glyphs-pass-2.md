# w8-glyphs, pass 2

Updated `pencil`, `pencilLine`, `bolt`, `fuelpump`, `roadLanes`, `arrowTriangleSwap`, `photo`, `arrowClockwise` and `camera` in the Android glyph set. The matching iOS references are SF Symbols `pencil`, `pencil.line`, `bolt`, `fuelpump.fill`, `road.lanes`, `arrow.triangle.swap`, `photo`, `arrow.clockwise` and `camera` from `.codex-out/compare/symbol-map.tsv`. No Swift source changed.

The pencil glyphs now use a slim diagonal with a dot; `pencilLine` adds its baseline. The bolt is outlined. The fuel pump has a filled body, transparent display and right nozzle. The road lane marks are thinner. The swap arrows follow one linked U-shaped route, down at left and up at right. The clockwise arrowhead now faces right from the upper-right of the open arc. The camera lens is smaller, with radius 3.5 instead of 4.2. Paths were drawn by eye; no SF Symbol outlines were copied, traced or converted.

`photo` is outlined to match iOS. The macOS-rendered `photo` reference in the sheet appears filled. `mapSlash` still has no local SF Symbols reference because this Mac cannot render `map.slash`. No other clear differences stood out in the 24 px and 32 px review.

Comparison sheets were regenerated with `render-symbols.swift`, `render-android-svg.swift` and `make-sheets.py`. Roborazzi screenshots were recorded with `-Proborazzi.test.record=true`.

Tests and lint passed:

```text
JAVA_TOOL_OPTIONS=-Duser.home=/private/tmp/w8-robolectric-home GRADLE_USER_HOME=/Users/onezee/.gradle JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon --offline -Proborazzi.test.record=true test
JAVA_TOOL_OPTIONS=-Duser.home=/private/tmp/w8-robolectric-home GRADLE_USER_HOME=/Users/onezee/.gradle JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon --offline -Proborazzi.test.verify=true test lintDebug
```
