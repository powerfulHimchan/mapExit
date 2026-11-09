"""Pack the authored front hero, dialogue people and growth symbols without repainting."""
import io
import json
from pathlib import Path
from PIL import Image
from battle_motion_art import fit

PEOPLE = ['hero_front', 'npc', 'npc_beggar', 'npc_healer', 'npc_herbalist', 'merchant']
ICONS = ['HP', 'ATTACK', 'DEFENSE', 'AGILITY', 'CRIT', 'CRIT_DAMAGE', 'LUCK', 'HEAL']

def emit(check=False):
    generated = {}
    metadata = []
    for source, target, columns, rows, extent in [
        ('people', 'social_people_049', 3, 2, (128,192)),
        ('growth', 'growth_icons_049', 4, 2, (128,128)),
    ]:
        image = Image.open(Path('art/refined049') / (source+'.png')).convert('RGBA')
        assert image.getchannel('A').getextrema()[0] == 0
        atlas = Image.new('RGBA',(columns*extent[0],rows*extent[1]))
        bounds = []
        for i in range(columns*rows):
            x,y = i%columns,i//columns
            # Uniform source cells; trim two gutter pixels so a generated grid edge cannot leak.
            region = image.crop((round(x*image.width/columns)+2,round(y*image.height/rows)+2,
                                 round((x+1)*image.width/columns)-2,round((y+1)*image.height/rows)-2))
            icon = fit(region,extent,4)
            bounds.append(list(icon.getchannel('A').getbbox()))
            atlas.alpha_composite(icon,(x*extent[0],y*extent[1]))
        generated[Path('app/src/main/res/drawable-nodpi') / (target+'.png')] = atlas
        metadata.append(bounds)
    def strings(v): return 'listOf('+','.join(json.dumps(x) for x in v)+')'
    def rects(v): return 'listOf('+','.join('intArrayOf('+','.join(map(str,b))+')' for b in v)+')'
    text = 'package com.powerfulhimchan.mapexit\n\n/** Generated from art/refined049. */\ninternal object SocialAtlas {\n'
    text += '    val people = '+strings(PEOPLE)+'\n'
    text += '    val peopleBounds = '+rects(metadata[0])+'\n'
    text += '    val icons = '+strings(ICONS)+'\n'
    text += '    val iconBounds = '+rects(metadata[1])+'\n}\n'
    generated[Path('app/src/main/java/com/powerfulhimchan/mapexit/SocialAtlas.kt')] = text.encode()
    for path, data in generated.items():
        if check:
            assert path.exists(), str(path)+' is missing'
            if isinstance(data, Image.Image):
                old=Image.open(path)
                assert old.size==data.size and old.mode==data.mode and old.tobytes()==data.tobytes(), str(path)+' differs from source'
            else: assert path.read_bytes()==data, str(path)+' differs from source'
        else:
            path.parent.mkdir(parents=True,exist_ok=True)
            if isinstance(data, Image.Image):
                buffer=io.BytesIO();data.save(buffer,format='PNG',optimize=True);data=buffer.getvalue()
            path.write_bytes(data)
    print('Social/menu art: front hero, 5 dialogue people, 8 growth icons')

if __name__=='__main__':
    import sys
    emit('--check' in sys.argv)
