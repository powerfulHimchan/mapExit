#!/usr/bin/env bash
set -uo pipefail

# The emulator action runs each script line in a separate shell. Keep the test
# status and screenshot loop together so failures still leave visual evidence.
test_status=0
./gradlew :app:connectedDebugAndroidTest --no-daemon || test_status=$?
mkdir -p app/build/device-screenshots
for name in explore battle trap preparation compact shop equipment wall-depth third-person enemy-damage player-damage victory trap-guide-0 trap-guide-1 trap-guide-2 trap-guide-3 trap-guide-4 compact-victory compact-guide; do
  adb pull "/sdcard/Download/mapexit-${name}.png" "app/build/device-screenshots/${name}.png" || true
done
exit "${test_status}"
