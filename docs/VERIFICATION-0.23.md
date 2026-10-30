# 0.23.0 requested improvements

Scope: ten requests approved for implementation. Achievement conditions/rewards and additional NPC roles remain pending user review and have no runtime implementation.

- World fixtures use event-specific world dimensions and screen-relative height/width caps.
- Doors are drawn only on the wall face adjacent to the actual exit cell.
- Persistent encountered-only bestiary: name, matching art, tactics, encounter/kill/death counters, first/last floor. Death, return and inheritance preserve records; training/debug battles are excluded. Prior saves load with empty default history.
- Six ordinary species in each of five 10-floor regions (30 total), plus region-specific elite/boss appearances. Map assignments persist so visible monsters match the battle.
- Growth screen: eight cards in a four-column grid, including full healing for one point. Full HP/zero points cannot consume a point. Actual equipped maximum HP is used. All seven stat explanations appear underneath.
- Equipment return action is resolved before looking up item/slot arguments, preventing unrelated helmet artwork and stats from appearing on the back button.
- Smaller claw, shared per-variant/tier weapon art, six ring identities, five necklace effects, and visible worn armor/accessories in battle.
- Player-facing enemy terminology and accessibility labels consistently use 몬스터.

Validation is performed by the Android verification workflow: authored atlas check, all core tests, Android lint, debug APK assembly and emulator instrumentation tests. New regression tests cover records, serialization, training, inheritance, map species, face orientation, projection caps, growth healing and artwork identity.

## Validation

Local authored-atlas, distinct-silhouette, palette-bound and whitespace checks pass.
Kotlin compilation, all core tests, Android lint, APK assembly and emulator tests are
checked in the Android verification workflow before main merge. The workflow run
linked from the pull request is the source of truth for the current result.
