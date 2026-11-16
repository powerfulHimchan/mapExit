#!/usr/bin/env bash
set -uo pipefail
status=0
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.powerfulhimchan.mapexit.Graphics052Test,com.powerfulhimchan.mapexit.GroundContact053Test --no-daemon || status=$?
mkdir -p app/build/graphics-preview
adb shell 'ls /sdcard/Download/mapexit-review052-*.png' | tr -d '\r' | while IFS= read -r path; do
  [ -n "$path" ] && adb pull "$path" app/build/graphics-preview/ || true
done
adb shell 'ls /sdcard/Download/mapexit-ground053-*.png' | tr -d '\r' | while IFS= read -r path; do
  [ -n "$path" ] && adb pull "$path" app/build/graphics-preview/ || true
done
exit "$status"
