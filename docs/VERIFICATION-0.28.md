# MapExit 0.28.0 — unified buttons

All actionable surfaces share GameButtonStyle: 8dp rounded rectangles, dark navy fill, a 1dp mint border, a common pressed fill, amber selection and muted disabled surfaces. Sizes remain appropriate for context controls, compact shortcuts, menu cards and icon tiles.

GameButton replaces the Material filled/text/outlined variants in dialogs, records, achievements, challenge controls, shop detail and navigation. gameClickable provides the same style and pressed interaction for camp menus, equipment slots, growth/trade cards, loot equip, level-up banners, dungeon shortcuts and generic menu rows. The existing latched down/up input path for the two gameplay buttons is preserved and consumes the shared surface style directly.

Verification: source audit leaves direct click styling only in the shared button module and retains the gameplay gesture handler. The existing full Android workflow checks core regressions, lint, APK assembly and real device navigation, quantity selection, disabled controls, dialogs, equip actions and all challenge controls. Existing normal/compact captures provide visual QA across these surfaces; no gameplay rules or save schema change.
