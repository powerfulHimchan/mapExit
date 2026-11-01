# MapExit 0.33.0 — sparse wall landmarks, level milestones, achievement order

Special-stage wall runes appear only at 90-degree corners and dead ends, once per landmark on one deterministic wall face. Straight corridors, junctions and isolated cells have no runes. A dead end uses its end wall; a corner uses one of its two solid faces. Existing rune identities, projection, fog, door preservation and save compatibility remain intact.

Level-ups award 3P normally, and an extra 2P when reaching LV5, LV10, LV15 and subsequent multiples of five. Each crossed milestone contributes independently to multi-level rewards and accumulated level-up banners. Loading a save does not grant points again. The growth menu explains the rule.

Achievement cards and engine choices use the same stable order: unlocked/unclaimed, still locked, then claimed. Catalogue order is retained within each group. Claim and title actions retain their original catalogue indices, and card keys retain achievement IDs so sorting cannot select another reward. Sorting does not change saved progress or reward eligibility.

Validation covers all 16 neighbour configurations and one marked face per landmark, save-stable identities, milestone boundaries and multi-level rewards, pending banner accumulation, stable achievement ordering and correct rewards/title IDs. Android tests exercise sparse corner walls on normal/compact screens and after recreation, a real LV4→5 victory banner and growth menu, and claim/reorder/reload flows. Full core tests, atlas consistency, Android lint, debug assembly and device regressions run before merge.
