"""Pack the authored 0.48 camp, menu icons and rear equipment; no repainting."""
import io
import json
from pathlib import Path
from PIL import Image
from battle_motion_art import cell, fit

REAR_KEYS = ['shield', 'armor_buckler', 'armor_tower', 'armor_spiked',
             'head', 'armor_leathercap', 'armor_headband', 'armor_heavyhelm',
             'armor_leatherarmor', 'armor_chainmail', 'armor_platearmor', 'armor_leatherpants',
             'armor_scoutpants', 'armor_platepants', 'armor_leatherboots', 'armor_ironboots',
             'armor_windboots', 'ring', 'necklace', 'collar']
MENU_KEYS = ['equipment', 'inventory', 'shop', 'growth', 'bank', 'help']

def emit(check=False):
    root = Path('art/refined048')
    output = Path('app/src/main/res/drawable-nodpi')
    generated = {}
    bounds = []
    for source, name, columns, rows, extent in [
        ('rear_armor', 'rear_equipment_048', 4, 5, (128, 160)),
        ('menus', 'preparation_icons_048', 3, 2, (128, 128)),
    ]:
        image = Image.open(root / (source + '.png')).convert('RGBA')
        assert image.getchannel('A').getextrema()[0] == 0, source + ' requires genuine transparency'
        atlas = Image.new('RGBA', (columns * extent[0], rows * extent[1]))
        regions = []
        for i in range(columns * rows):
            icon = fit(cell(image, i, columns, rows), extent, 4)
            regions.append(list(icon.getchannel('A').getbbox()))
            atlas.alpha_composite(icon, (i % columns * extent[0], i // columns * extent[1]))
        generated[output / (name + '.png')] = atlas
        bounds.append(regions)
    generated[output / 'preparation_camp_048.png'] = Image.open(root / 'camp.png').convert('RGB').resize((1200, 480), Image.Resampling.NEAREST)
    def strings(values): return 'listOf(' + ','.join(json.dumps(v) for v in values) + ')'
    def rectangles(values): return 'listOf(' + ','.join('intArrayOf(' + ','.join(map(str, v)) + ')' for v in values) + ')'
    text = 'package com.powerfulhimchan.mapexit\n\n/** Generated from the authored art/refined048 sources. */\ninternal object PreparationAtlas {\n'
    text += '    val rearKeys = ' + strings(REAR_KEYS) + '\n'
    text += '    val rearBounds = ' + rectangles(bounds[0]) + '\n'
    text += '    val menuKeys = ' + strings(MENU_KEYS) + '\n'
    text += '    val menuBounds = ' + rectangles(bounds[1]) + '\n}\n'
    generated[Path('app/src/main/java/com/powerfulhimchan/mapexit/PreparationAtlas.kt')] = text.encode()
    for path, data in generated.items():
        if check:
            assert path.exists(), str(path) + ' is missing'
            if isinstance(data, Image.Image):
                old = Image.open(path)
                assert old.size == data.size and old.mode == data.mode and old.tobytes() == data.tobytes(), str(path) + ' differs from source'
            else:
                assert path.read_bytes() == data, str(path) + ' differs from source'
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            if isinstance(data, Image.Image):
                buffer = io.BytesIO(); data.save(buffer, format='PNG', optimize=True); data = buffer.getvalue()
            path.write_bytes(data)
    print('Preparation art: 20 rear equipment parts, 6 menu icons, camp background')

if __name__ == '__main__':
    import sys
    emit('--check' in sys.argv)
