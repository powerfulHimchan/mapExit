# 0.24.0 additional NPCs

The approved roles join the existing quest traveler at the same one-NPC-per-3/4-floor schedule:

- Beggar: donate exactly 10G, with a 30% chance to receive one potion in thanks; decline freely.
- Healer: free healing of 20% of equipped maximum HP, capped at full HP, with no use when HP is full.
- Herbalist: receive one inventory potion for later use, with no automatic healing.

Roles and the beggar gift are fixed per cycle/floor and persisted on the map. Reopening, saving/loading or regenerating the floor cannot reroll them. Successful service is recorded separately from map cells and survives death/return, preventing duplicate rewards on revisits. Inheritance starts a new cycle and resets that record. Declining, insufficient gold and full HP do not consume service.

Quest NPCs retain existing offer, acceptance and reward behavior. Support NPCs neither offer/replace quests nor claim an existing completed quest. Old save cells default to quest travelers, retaining their current offers. New role fields and service records have backwards-compatible defaults.

Each role has its own authored clothing/props, title, dialogue and action label in both dungeon and encounter scenes. Unavailable actions stay visible but disabled, with a caption explaining insufficient gold or full HP. All three are accessible from the training menu with isolated training resources.

Validation: authored-atlas check, core tests (role/action isolation, costs/rewards, healing cap, save/revisit/inheritance, stable roles/gifts, approximate distribution, legacy saves and training restoration), Android lint, APK build, and Android 11 instrumentation tests including normal and compact NPC dialogs and distinct role rendering. CI is authoritative for the current result.
