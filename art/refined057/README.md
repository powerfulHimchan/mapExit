# Directional preparation walk and native equipment fitting

`camp-walk.png` was created with the built-in image generation tool, using the
existing front hero and obstacle hero atlases as visual references. It contains
four right-facing and four left-facing walk poses, with the articulated lower
arms omitted to match the existing separate arm rig.

Production prompt: create a transparent 4-column, 2-row sheet of the same blond
adventurer in green gold-trimmed tunic, brown belt, trousers and boots. Top row:
four sequential right-profile walking poses (contact, passing, opposite contact,
opposite passing); bottom row: the corresponding left-profile poses. Preserve
head/body proportions, native detailed pixel texture, shared feet baseline,
clear knee and boot shapes, and shoulder caps with empty short sleeves. No lower
arms, hands, weapons, shield, helmet, armor, text, labels, grid or background.

`tools/camp_walk_art.py` only isolates and packs the authored bodies, discarding
isolated gutter specks when measuring their bounds. It preserves their aspect
ratios and detail and packs them into native 192px cells at one common height.
The regular authored-atlas check verifies this production atlas.

Runtime equipment fitting follows the original cloth and foot pixels. Armor and
boot materials change without adding another inventory-item silhouette. Both
sides retain their individual foot shapes, and the foreground boot layer uses
exactly the same native pixels. Helmets attach to a shared neck pivot and the
native collar is retained above the helmet's lower edge.
