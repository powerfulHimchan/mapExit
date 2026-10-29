#!/usr/bin/env bash
set -uo pipefail

# The emulator action runs each script line in a separate shell. Keep the test
# status and screenshot loop together so failures still leave visual evidence.
test_status=0
./gradlew :app:connectedDebugAndroidTest --no-daemon || test_status=$?
mkdir -p app/build/device-screenshots
for name in explore battle trap preparation compact shop equipment wall-depth third-person enemy-damage player-damage victory trap-cue-0 trap-cue-1 trap-cue-2 trap-cue-3 trap-cue-4 compact-victory compact-cue npc-encounter chest-encounter chest-ambush merchant-encounter sell-equipped sell-removed trap-auto-success trap-auto-failure weapon-sword weapon-spear weapon-hammer weapon-claw weapon-whip enemy-knight enemy-bat enemy-boss fine-heal fine-door fine-inheritance fine-inventory-top fine-inventory-bottom fine-atlas retreat-confirm retreat-result equipment-empty equipment-body-layout death-result compact-death compact-equipment growth-grid growth-empty shop-grid shop-purchased quantity-grid sell-grid walk-0 walk-1 walk-2 walk-3 walk-4 turn-0 turn-1 turn-2 turn-3 smooth-corridor compact-growth compact-shop; do
  adb pull "/sdcard/Download/mapexit-${name}.png" "app/build/device-screenshots/${name}.png" || true
done
exit "${test_status}"
