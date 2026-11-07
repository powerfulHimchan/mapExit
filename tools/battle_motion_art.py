"""Native 64px battle art and keyed motion frames; keep the editable pixel originals.

The source silhouette/colour identities come from the existing authored atlas.
Hair, cloth, seams, hardware and moving contours are repainted on the 64px grid.
No bilinear interpolation, random texture, or baked weapon is used on actors.
"""
import copy
import binascii
import json
import math
import struct
import zlib
from pathlib import Path

WEAPONS = ['sword', 'spear', 'hammer', 'claw', 'whip']
HERO_POSES = ['idle_0', 'idle_1'] + [f'{action}_{kind}_{n}' for action in ['attack', 'strong'] for kind in WEAPONS for n in range(6)] + [f'{action}_{n}' for action in ['guard', 'dodge'] for n in range(6)] + [f'{action}_{n}' for action in ['hit', 'death'] for n in range(4)]
MONSTER_POSES = ['idle_0', 'idle_1'] + [f'attack_{n}' for n in range(6)] + ['guard_0', 'guard_1'] + [f'{action}_{n}' for action in ['hit', 'death'] for n in range(4)]


def detailed(source, name, Art):
    a = Art(source.w * 2, source.h * 2)
    a.p = [[source.p[y // 2][x // 2] for x in range(a.w)] for y in range(a.h)]
    # One-pixel bevels and short colour clusters add actual native-grid detail.
    ramps = [(2, 3, 4, 5, 6), (7, 8, 9, 10, 11), (12, 13, 14), (15, 16, 17, 18), (26, 27, 28, 29, 30), (31, 32, 33), (34, 35, 36), (37, 38, 39), (40, 41, 42), (43, 44, 45), (46, 47, 48)]
    lookup = {c: r for r in ramps for c in r}
    original = copy.deepcopy(a.p)
    for y in range(1, a.h - 1):
        for x in range(1, a.w - 1):
            c = original[y][x]
            if c not in lookup:
                continue
            ramp = lookup[c]
            i = ramp.index(c)
            if original[y - 1][x] not in ramp and (x + y) % 4 != 0:
                a.p[y][x] = ramp[min(i + 1, len(ramp) - 1)]
            elif original[y + 1][x] not in ramp and x % 3:
                a.p[y][x] = ramp[max(0, i - 1)]
    if name == 'heroBack':
        # Coherent cloth panels on the finer grid, with sparse seams rather than noise.
        a.rect(8, 29, 49, 46, 0)
        a.poly([(21, 29), (43, 29), (54, 37), (56, 53), (51, 61), (51, 73), (39, 75), (32, 71), (25, 75), (12, 73), (13, 61), (8, 53), (9, 38)], 1)
        a.poly([(21, 31), (43, 31), (51, 38), (53, 51), (47, 58), (48, 70), (39, 72), (32, 68), (25, 72), (15, 70), (18, 57), (11, 51), (12, 39)], 7)
        a.poly([(23, 36), (41, 35), (45, 43), (43, 59), (46, 69), (38, 69), (32, 65), (25, 70), (18, 68), (21, 56), (18, 43)], 8)
        a.poly([(23, 40), (29, 42), (27, 57), (25, 66), (21, 68), (24, 54)], 9)
        a.poly([(37, 41), (42, 42), (39, 58), (40, 66), (35, 64)], 9)
        a.line(18, 40, 14, 50, 9); a.line(48, 40, 50, 50, 9)
        a.rect(10, 53, 8, 8, 12); a.rect(48, 53, 8, 8, 12)
        a.rect(11, 54, 6, 1, 14); a.rect(49, 54, 6, 1, 14)
        a.rect(11, 61, 7, 5, 13); a.rect(48, 61, 7, 5, 13)
        for x in [20, 38]:
            a.rect(x, 70, 10, 14, 2); a.rect(x + 2, 71, 3, 11, 3)
        # Hood, two cloth panels and individually shaded hanging folds.
        a.poly([(19, 29), (44, 29), (49, 35), (43, 41), (32, 45), (21, 41), (15, 35)], 7)
        a.line(18, 33, 31, 40, 10); a.line(31, 40, 46, 33, 10)
        a.line(21, 36, 31, 42, 8); a.line(31, 42, 43, 36, 8)
        for x, top, bottom in [(21, 43, 65), (27, 45, 67), (36, 44, 65), (43, 41, 66)]:
            a.line(x, top, x - 2, bottom, 7)
            a.line(x + 1, top + 2, x, bottom - 2, 10)
        for y in range(44, 67, 4):
            a.rect(18, y, 1, 2, 11); a.rect(46, y - 2, 1, 2, 9)
        a.rect(17, 59, 31, 5, 12); a.rect(18, 59, 29, 1, 14)
        a.rect(32, 58, 8, 7, 22); a.rect(33, 59, 6, 5, 24); a.rect(35, 60, 3, 3, 12)
        a.rect(35, 61, 4, 1, 18)
        for x in [18, 38]:
            a.rect(x, 84, 10, 4, 12); a.rect(x, 84, 10, 1, 14)
            a.rect(x + 2, 84, 3, 3, 23); a.rect(x + 3, 85, 1, 1, 18)
            a.line(x + 1, 89, x + 8, 89, 14)
        # Tousled locks replace the block-shaped original head, not an overlay.
        a.rect(12, 0, 40, 29, 0)
        a.poly([(23, 4), (20, 0), (29, 3), (36, 2), (42, 7), (47, 7), (46, 12), (51, 17), (47, 20), (47, 27), (40, 29), (34, 28), (27, 30), (20, 26), (16, 27), (17, 21), (13, 20), (17, 16), (16, 11)], 1)
        a.poly([(23, 5), (26, 4), (30, 5), (36, 4), (43, 9), (45, 9), (44, 14), (48, 17), (44, 20), (45, 25), (38, 27), (32, 26), (27, 28), (21, 24), (18, 24), (20, 19), (17, 19), (20, 15), (19, 12)], 15)
        for pts in [[(25, 5), (33, 5), (28, 14), (21, 19), (20, 16)], [(34, 6), (40, 8), (37, 19), (30, 25), (31, 19)], [(42, 10), (45, 15), (42, 24), (37, 25), (40, 18)], [(24, 15), (28, 12), (26, 23), (23, 25), (21, 21)]]:
            a.poly(pts, 16)
        for start, end in [((26, 6), (23, 13)), ((33, 6), (28, 17)), ((38, 9), (34, 20)), ((43, 15), (41, 22))]:
            a.line(*start, *end, 17)
            a.rect(start[0], start[1], 2, 1, 18)
    # Equipment uses the same bevel/cloth detail in every stance and tier.
    if name.startswith(('weapon_', 'armor_')) or name in WEAPONS + ['shield', 'head', 'body', 'pants', 'boots']:
        for y in range(5, a.h - 3, 6):
            for x in range(5, a.w - 3, 8):
                if a.p[y][x] in [3, 4, 5]:
                    a.rect(x, y, 2, 1, 6)
                elif a.p[y][x] in [8, 9, 13, 14]:
                    a.rect(x, y, 1, 2, 10 if a.p[y][x] in [8, 9] else 23)
        if 'sword' in name or name in ['sword', 'weapon_sabre', 'weapon_falchion', 'weapon_rapier']:
            for y in range(15, 42):
                if a.p[y][32] in [3, 4, 5, 6]:
                    a.p[y][32] = 3
                    if a.p[y][30] in [3, 4, 5, 6]: a.p[y][30] = 6
    return a


def posed(base, family, pose, Art):
    action, n = pose.rsplit('_', 1); n = int(n)
    a = Art(base.w, base.h)
    phase = [0, -.6, -1, 1, .6, .1][min(n, 5)]
    if action == 'idle': phase = .15 if n else -.15
    if action.startswith('strong'): phase *= 1.5
    for y in range(base.h):
        for x in range(base.w):
            if not base.p[y][x]: continue
            dx = dy = 0
            if family in ['heroBack', 'knight', 'boss']:
                if action == 'idle' and n and 30 < y < 58: dy -= 1
                if 30 < y < 72:
                    dx = round(phase * (2 if y < 58 else 1))
                    if 58 < y < 72: dx += round(phase * math.sin((y - 58) * .4))
                if y > 74:
                    dx = round(phase * (2 if x < 32 else -2))
                    dy = -round(abs(phase) * (1 if x < 32 else 2))
                if action == 'dodge' and y < 73: dy = round(7 * abs(phase))
                if action == 'guard' and y < 60: dx -= round(abs(phase))
            elif family == 'bat' and (x < 23 or x > 41):
                dy = round((y - base.h * .48) * (.35 * phase))
            elif family == 'slime':
                dx = round((x - 32) * phase * .07)
                dy = round((base.h - 1 - y) * phase * .08)
            elif family == 'wisp':
                dx = round(math.sin(y * .10 + n * .9) * (3 if y < base.h * .5 else 1))
            elif family == 'spider' and (x < 21 or x > 43):
                dy = round(math.sin(x * .22 + n) * 3)
                dx = round(phase * (1 if x < 32 else -1))
            elif family == 'golem':
                if y > base.h * .7: dx = round(phase * (2 if x < 32 else -2))
                elif x < 20 or x > 44: dy = round(-abs(phase) * 2)
            if action == 'hit': dx += round(math.sin(y * .13) * (n + 1) * .6)
            if action == 'death':
                # Draw a collapsing contour within the same contact box.
                dy += round((base.h - 1 - y) * (n + 1) * .12)
                dx += round((x - 32) * (n + 1) * .025)
            xx, yy = x + dx, y + dy
            if 0 <= xx < a.w and 0 <= yy < a.h: a.p[yy][xx] = base.p[y][x]
    if family == 'heroBack':
        # Intentional frame-specific seam/cape folds and a turning hair lock.
        if action not in ['idle', 'death']:
            for y in range(45, 60):
                x = 29 + round(phase * (y - 43) / 8)
                if a.p[y][x] in range(7, 12): a.p[y][x] = 7
                if a.p[y][x + 2] in range(7, 12): a.p[y][x + 2] = 10
        if 'hammer' in action:
            a.line(22, 43, 25 + n % 2, 49, 11)
        if action == 'guard': a.line(16, 40, 19, 48, 10)
    elif family in ['slime', 'wisp'] and action.startswith('attack') and n in [2, 3, 4]:
        for x, y in [(5, base.h - 10), (57, base.h - 14)]: a.rect(x, y - n, 2, 3, 41 if family == 'wisp' else 29)
    elif family == 'golem' and action.startswith('attack'):
        a.line(31, 37, 30 + n % 3, 53, 41)
    sx, sy = 46, 46
    sx += round(phase * 2)
    if action == 'idle' and n: sy -= 1
    if action == 'dodge': sy += round(7 * abs(phase))
    if action == 'guard': sx -= round(abs(phase))
    if action == 'hit': sx += round(math.sin(46 * .13) * (n + 1) * .6)
    if action == 'death':
        sy += round((base.h - 1 - 46) * (n + 1) * .12)
        sx += round((46 - 32) * (n + 1) * .025)
    a.shoulder = (sx, sy)
    return a


def rgba(a, colors):
    palette = [bytes.fromhex(c[2:] + c[:2]) for c in colors]
    return b''.join(palette[c] for row in a.p for c in row)


def png(width, height, scanlines):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', binascii.crc32(kind + data) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(scanlines, 9)) + chunk(b'IEND', b'')


def pixels(encoded):
    offset = 8; chunks = []
    while offset < len(encoded):
        size = struct.unpack_from('>I', encoded, offset)[0]
        if encoded[offset + 4:offset + 8] == b'IDAT': chunks.append(encoded[offset + 8:offset + 8 + size])
        offset += size + 12
    return zlib.decompress(b''.join(chunks))


def emit(sprites, colors, Art, check=False):
    root = Path('app/src/main/res/drawable-nodpi')
    generated = {}
    def sheet(name, frames, cols):
        width, height = cols * 64, math.ceil(len(frames) / cols) * 96
        stride = width * 4 + 1
        scanlines = bytearray(height * stride)
        for i, a in enumerate(frames):
            assert a.w <= 64 and a.h <= 96
            data = rgba(a, colors)
            for y in range(a.h):
                start = ((i // cols) * 96 + y) * stride + 1 + (i % cols) * 64 * 4
                scanlines[start:start + a.w * 4] = data[y * a.w * 4:(y + 1) * a.w * 4]
        generated[root / (name + '.png')] = png(width, height, scanlines)
    hero = detailed(sprites['heroBack'], 'heroBack', Art)
    hero_frames = [posed(hero, 'heroBack', p, Art) for p in HERO_POSES]
    sheet('combat_hero_motion', hero_frames, 12)
    monsters = [f'monster_{region}_{family}' for region in range(5) for family in ['slime', 'knight', 'bat', 'spider', 'golem', 'wisp', 'boss']]
    monster_frames = []
    for name in monsters:
        base = detailed(sprites[name], name, Art)
        monster_frames += [posed(base, name.split('_')[-1], p, Art) for p in MONSTER_POSES]
    sheet('combat_monster_motion', monster_frames, 24)
    gear = [k for k in sprites if k.startswith(('weapon_', 'armor_')) or k in WEAPONS + ['shield', 'head', 'body', 'pants', 'boots', 'ring', 'necklace']]
    gear_frames = [detailed(sprites[k], k, Art) for k in gear]
    bounds = []
    for a in gear_frames:
        points = [(x, y) for y in range(a.h) for x in range(a.w) if a.p[y][x]]
        bounds.append([min(x for x, y in points), min(y for x, y in points), max(x for x, y in points) + 1, max(y for x, y in points) + 1])
    # Sleeve and bracer pixels are separate movable layers, never blue line arms.
    for metal in [False, True]:
        for part in ['upper', 'lower', 'grip']:
            a = Art(16, 32 if part != 'grip' else 16)
            a.poly([(5, 0), (11, 0), (14, 5), (13, a.h - 3), (10, a.h - 1), (4, a.h - 2), (2, 5)], 1)
            ramp = [3, 4, 5, 6] if metal else ([7, 8, 9, 10] if part == 'upper' else [12, 13, 14, 23])
            a.rect(4, 2, 8, a.h - 5, ramp[0]); a.rect(5, 3, 5, a.h - 7, ramp[1]); a.rect(5, 4, 2, a.h - 8, ramp[2]); a.rect(5, 4, 1, 3, ramp[3])
            for y in range(7, a.h - 3, 5): a.line(5, y, 11, y + 1, ramp[2])
            if part == 'grip':
                a.rect(7, 4, 3, 9, 0)  # The weapon grip stays visible between fingers.
                a.rect(5, 5, 6, 2, 14); a.rect(5, 10, 6, 2, 14)
            gear.append(('steel_' if metal else 'cloth_') + part); gear_frames.append(a); bounds.append([0, 0, a.w, a.h])
    sheet('combat_equipment_motion', gear_frames, 10)
    def strings(values): return 'listOf(' + ','.join(json.dumps(v) for v in values) + ')'
    text = '''package com.powerfulhimchan.mapexit

/** Generated by tools/battle_motion_art.py through build_pixel_sprites.py. */
internal object MotionAtlas {
'''
    text += '    val heroPoses = ' + strings(HERO_POSES) + '\n'
    text += '    val heroShoulders = listOf(' + ','.join('intArrayOf(' + ','.join(map(str, a.shoulder)) + ')' for a in hero_frames) + ')\n'
    text += '    val monsterPoses = ' + strings(MONSTER_POSES) + '\n'
    text += '    val monsters = ' + strings(monsters) + '\n'
    text += '    val monsterHeights = intArrayOf(' + ','.join(str(sprites[k].h * 2) for k in monsters) + ')\n'
    text += '    val gear = ' + strings(gear) + '\n'
    text += '    val gearHeights = intArrayOf(' + ','.join(str(a.h) for a in gear_frames) + ')\n'
    text += '    val gearBounds = listOf(' + ','.join('intArrayOf(' + ','.join(map(str, b)) + ')' for b in bounds) + ')\n}\n'
    text = text[:-2] + '    val palette = intArrayOf(' + ','.join('0x' + c + '.toInt()' for c in colors) + ')\n}\n'
    generated[Path('app/src/main/java/com/powerfulhimchan/mapexit/MotionAtlas.kt')] = text.encode()
    for path, data in generated.items():
        if check:
            if not path.exists(): raise SystemExit(f'{path} is missing')
            existing = path.read_bytes()
            equal = pixels(existing) == pixels(data) if path.suffix == '.png' else existing == data
            if not equal: raise SystemExit(f'{path} differs from authored battle motion art')
        else:
            path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
    print(f'Authored battle frames: hero {len(HERO_POSES)}, monsters {len(monster_frames)}, equipment/limbs {len(gear_frames)}')
