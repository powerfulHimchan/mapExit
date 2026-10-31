# MapExit 0.30.0 — reward inventory and timing improvements

Chest and victory receipts offer a bag shortcut only when equipment (rather than just potions/gold) was obtained. Opening the bag preserves the receipt and return mode across menu navigation and save/recreation. Equipping inside the bag and returning does not duplicate rewards. The chest acquisition confirmation button is removed; normal movement or Android back dismisses its card, and victory keeps its existing continue action.

Equipment receipts show occupied-slot comparisons using the same non-mutating full-loadout previews as the shop. They include the current item, its icon and complete ability summary, displaced equipment and its effects, and resulting player stat changes. Both ring slots and hand/two-handed replacement rules are represented. Receipt cards scroll within the available scene, including compact screens.

The healing fixture is consistently called 회복의 수정, matching its existing crystal artwork. The level-up growth action has a 12dp right inset and an 8dp gap from the text. Compact banners use bounded line counts and smaller explicit line heights to preserve the dungeon scene.

The falling-beam obstacle rejects crouching before 40% of its active duration and at/after impact. The boundary scales with depth and trap necklaces, while the beam keeps its existing rendered motion and single-tap crouch. Too-early failures give a reason and follow the ordinary one-time damage/result rules. Blade and special-stage hazard timing is unchanged.

Validation covers equipment-only bag eligibility, menu/save/return receipts, dual ring and two-handed comparisons, both beam boundaries/depth/charm, and one-time early-failure damage. Device cases cover real chest/victory bag actions, comparisons on normal/compact screens, potion-only receipts, early beam failures, crystal interaction, and growth-button insets. The full workflow validates sprites, core tests, lint, debug APK assembly and Android device regressions before merge.
