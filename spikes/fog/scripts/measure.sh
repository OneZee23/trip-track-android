#!/usr/bin/env bash
# Runs the fog spike on the connected device or emulator: the scripted bench for every
# candidate and dataset, then screenshots at street, city and country zoom and in the middle of
# a pinch. Results land in out/measure (gitignored); the doc copies what it cites.
#
#   scripts/measure.sh [bench|shots|all] [output dir]
set -euo pipefail

PKG=com.onezee.triptrack.fogspike
WHAT=${1:-all}
OUT=${2:-out/measure}
mkdir -p "$OUT/bench" "$OUT/shots"

launch() {
  adb shell am force-stop "$PKG"
  adb logcat -c
  adb shell am start -W -n "$PKG/.MainActivity" "$@" >/dev/null
}

wait_for() {
  local pattern=$1 timeout=${2:-180} start=$SECONDS
  until adb logcat -d -s FOGSPIKE:I | grep -q -- "$pattern"; do
    if (( SECONDS - start > timeout )); then
      echo "timeout waiting for '$pattern'" >&2
      adb logcat -d -s FOGSPIKE:I AndroidRuntime:E | tail -20 >&2
      return 1
    fi
    sleep 1
  done
}

shot() {
  adb exec-out screencap -p > "$1"
  echo "  $1"
}

bench() {
  local cand=$1 data=$2 style=${3:-fog}
  echo "bench $cand $data $style"
  launch --es candidate "$cand" --es dataset "$data" --es style "$style" --es preset city --ez bench true
  wait_for "bench-done" 300 || return 0
  adb exec-out run-as "$PKG" cat "files/bench-$cand-$data-$style.json" > "$OUT/bench/bench-$cand-$data-$style.json"
  adb shell dumpsys meminfo "$PKG" > "$OUT/bench/meminfo-$cand-$data-$style.txt"
}

if [[ $WHAT == bench || $WHAT == all ]]; then
  # One pass first to fill MapLibre's tile cache, so the network does not land in the numbers.
  bench none stress fog
  for data in small stress; do
    for cand in none a b c; do bench "$cand" "$data"; done
  done
  bench a ios fog
  bench a stress night
  bench a stress cells
fi

if [[ $WHAT == shots || $WHAT == all ]]; then
  for cand in a b c none; do
    for preset in street city region country; do
      echo "shot $cand $preset"
      launch --es candidate "$cand" --es dataset stress --es style fog --es preset "$preset"
      wait_for "ready" 180 && shot "$OUT/shots/$cand-stress-$preset.png" || true
    done
  done
  for style in night cells; do
    launch --es candidate a --es dataset stress --es style "$style" --es preset city
    wait_for "ready" 180 && shot "$OUT/shots/a-stress-city-$style.png" || true
  done
  launch --es candidate a --es dataset small --es style fog --es preset street
  wait_for "ready" 180 && shot "$OUT/shots/a-small-street.png" || true
  # A frame in the middle of a slow pinch: is the fog still crisp and still on the roads?
  for cand in a b c; do
    launch --es candidate "$cand" --es dataset stress --es style fog --es preset city --es gesture pinch
    wait_for "gesture-start" 180 && sleep 2.5 && shot "$OUT/shots/$cand-stress-midpinch.png" || true
  done
fi
