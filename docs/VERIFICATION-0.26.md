# MapExit 0.26.0 verification

## Scope

- Fifteen lifetime achievements under Records, including a second 50F boss victory in a distinct cycle. Counters, unlocked/claimed rewards and selected titles survive death, return, save/load and inheritance. Training/debug actions do not count.
- Rewards are manually claimed once at camp. Gold, consumables and growth points retain normal loss/reset rules. Claiming the second-final-boss reward grants the unique Exit Seal ring: +25% attack, +20% HP and -15% agility. Its own authored icon is shared by inventory and combat equipment presentation; normal equipment/death/sale/inheritance rules apply.
- Basic potions remain 35% HP / 25G. Greater potions heal 70% equipped maximum HP / 75G, have distinct bottle art, can be bought in quantities 1/3/5, appear among random potion drops from floor 11, and replace selected achievement rewards. The combat header offers each grade separately, costing one attack turn. Potion use cannot consume the other grade.
- Legacy saves default potions to BASIC. Achievement migration uses actual bestiary, devices, completed cycles and current-run chest/trap counters only; unavailable past NPC/quest and prior-run event history is not invented. Distinct final-boss cycles are inferred only from completed inheritance cycles and a current 50F device, not maximum depth or repeated boss defeat totals.
- The special-stage proposal is documented separately; no portal or special-stage runtime is implemented in this version.

## Validation

The Android verification workflow checks the authored sprite atlas, all core tests, Android lint and debug assembly, then emulator instrumentation including normal/compact achievement claims, title selection, a real second-final-boss special-ring claim/equip, greater-potion bulk purchase, battle use and save restoration. CI is authoritative for the result.

New core regressions cover event confirmation, stale input, mimic counts, trap save boundaries, surface-only claims, legacy evidence, title/reset behavior, training/debug isolation, distinct boss cycles, one-time equipment rewards and grade-specific potion economy/healing.

Screenshots: records-achievements-entry, achievements-first-reward, achievement-title-selected, achievement-special-ring-compact, achievement-special-ring-equipped-compact, greater-potion-shop, greater-potion-combat.
