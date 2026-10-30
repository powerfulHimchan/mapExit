# 0.25.0 records navigation and victory rewards

The bestiary is reached only inside the records/help menu. The standalone camp tile and direct surface/exploration choices are removed; records still offers the bestiary and back-navigation returns to records.

Victory and chest loot now share the same reward-row component: actual variant/tier item icon, item name, stat summary and a quick-equip action for equipment. Worn items update to the equipped state without closing the victory card. Potions retain their item icon/name/description with no equipment action. Boss affix details remain available; old saves with name-only rewards retain their fallback description. The existing combat, rewards, equipment and save rules are unchanged.

Validation uses core navigation checks and Android instrumentation tests for records-to-bestiary navigation, victory item art and in-place quick-equipping, and compact multiple-reward/potion layouts, plus the full existing core/lint/APK/emulator workflow. CI records the current result.

Special stages are deferred. The user's random appearance / roughly one per fifteen floors request is recorded in PENDING-SPECIAL-STAGES.md; no stage logic is implemented.
