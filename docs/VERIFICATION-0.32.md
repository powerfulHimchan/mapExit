# MapExit 0.32.0 — physical challenge wall marks

All special-stage dungeon views now paint the existing location rune onto real wall faces: one of six chalk pictures (sun, moon, star, mountain, water, flower) plus its number. Art is sampled in the ray-hit wall's UV coordinates, sharing perspective, lighting, torch fog and occlusion with the masonry. A face identifies the walkable cell directly in front of it, so opposite sides of a wall can carry different marks. Doors retain their unobstructed artwork. Ordinary dungeons keep their existing torches and unmarked walls.

The six-picture legend is explained before a challenge. Existing textual rune names and numbers stay consistent with the art. Rune identity is derived from the existing cell index; revisits and old saves need no migration. Rendering cache keys include whether challenge marks are enabled.

Validation includes wall-face identity and save round trips, unchanged existing clue IDs, actual raster changes and cache toggling, all six unique pictures and numbers, occlusion, door preservation, and real normal/compact challenge screens after recreation. The full verification workflow runs core tests, pixel atlas consistency, Android lint, debug assembly and Android 11 device regressions before merge.
