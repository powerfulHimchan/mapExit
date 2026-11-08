"""Build battle atlases from the painted production sources in art/refined045.

Source images are the reference-matched artwork, not enlarged legacy sprites.
Pillow only crops, packs, places and selects those images for the existing rig.
"""
import io
import json
import math
from pathlib import Path
from PIL import Image, ImageChops

WEAPONS = ['sword', 'spear', 'hammer', 'claw', 'whip']
HERO_POSES = ['idle_0', 'idle_1'] + [f'{action}_{kind}_{n}' for action in ['attack', 'strong'] for kind in WEAPONS for n in range(6)] + [f'{action}_{n}' for action in ['guard', 'dodge'] for n in range(6)] + [f'{action}_{n}' for action in ['hit', 'death'] for n in range(4)]
MONSTER_POSES = ['idle_0', 'idle_1'] + [f'attack_{n}' for n in range(6)] + ['guard_0', 'guard_1'] + [f'{action}_{n}' for action in ['hit', 'death'] for n in range(4)]
ROOT = Path('art/refined045')
NEAREST = Image.Resampling.NEAREST
# Body source cell coordinates, in a normalized 192x192 cell. Actor origin is x=32.
RIGHT = [(123, 90), (116, 93), (147, 115), (141, 118), (142, 131), (121, 92), (87, 138), (129, 90)]
LEFT = [(80, 88), (80, 89), (111, 111), (100, 114), (96, 127), (82, 90), (66, 133), (87, 88)]
# head, body, pants, boots; native x,y,w,h and local rotation. These follow each painted pose.
WORN = [
 [(69,8,69,66,0),(70,77,67,63,0),(77,132,60,41,0),(64,155,81,30,0)],
 [(44,11,65,63,-8),(64,78,69,65,-12),(70,132,72,39,-10),(54,155,95,30,0)],
 [(99,30,73,61,15),(82,91,74,60,15),(56,135,87,39,10),(38,152,110,32,0)],
 [(94,44,75,61,15),(78,103,75,51,10),(58,141,82,33,0),(35,157,110,28,0)],
 [(92,60,73,59,25),(56,117,94,46,25),(43,144,111,31,0),(10,163,147,25,0)],
 [(27,5,75,64,-20),(57,69,78,73,-20),(54,131,94,43,-10),(37,155,113,30,0)],
 [(12,84,59,51,-60),(45,108,69,49,-70),(91,128,62,29,-70),(115,145,57,31,-75)],
 [(76,8,69,66,0),(77,77,67,63,0),(84,132,60,41,0),(71,155,81,30,0)],
]

_ROWS = {}
def cell(image, i, cols, rows):
    x,y=i%cols,i//cols
    key=(id(image),rows)
    if key not in _ROWS:
        # Generated source sheets can have unequal transparent gutters. Choose row cuts
        # inside those gutters instead of cutting a horn, flame tip or weapon shaft.
        hist=list(image.getchannel('A').point(lambda a:255 if a>96 else 0).resize((1,image.height),Image.Resampling.BOX).getdata())
        boundaries=[0]
        for n in range(1,rows):
            center=round(n*image.height/rows);radius=round(image.height/rows*.25)
            lo,hi=max(boundaries[-1]+1,center-radius),min(image.height-1,center+radius)
            minimum=min(hist[lo:hi]);runs=[];start=None
            for yy in range(lo,hi+1):
                empty=yy<hi and hist[yy]<=minimum+1
                if empty and start is None:start=yy
                if not empty and start is not None:runs.append((start,yy));start=None
            best=min(runs,key=lambda r:abs((r[0]+r[1])/2-center)-min(r[1]-r[0],20)*.5)
            boundaries.append(round((best[0]+best[1])/2))
        _ROWS[key]=boundaries+[image.height]
    boundaries=_ROWS[key]
    return image.crop((round(x*image.width/cols),boundaries[y],round((x+1)*image.width/cols),boundaries[y+1]))

def silhouette(image):
    alpha=image.getchannel('A').point(lambda a: 255 if a>96 else 0)
    box=alpha.getbbox()
    if not box: raise ValueError('Empty source cell')
    return image.crop(box)

def fit(image, size, margin=4):
    image=silhouette(image)
    scale=min((size[0]-margin*2)/image.width,(size[1]-margin*2)/image.height)
    image=image.resize((max(1,round(image.width*scale)),max(1,round(image.height*scale))),NEAREST)
    out=Image.new('RGBA',size)
    out.alpha_composite(image,((size[0]-image.width)//2,size[1]-margin-image.height))
    return out

def hero_cell(pose):
    action,n=pose.rsplit('_',1);n=int(n)
    if action=='idle': return 7 if n else 0
    if action.startswith(('attack','strong')): return [0,1,1,2,2,0][n]
    if action=='guard': return [0,3,3,3,3,0][n]
    if action=='dodge': return [0,4,4,4,4,0][n]
    if action=='hit': return [5,5,5,0][n]
    return [5,6,6,6][n]

def regional(image, region):
    if region==0:return image.copy()
    colors=[(0,0,0),(160,104,65),(77,130,184),(145,86,175),(180,71,60)]
    tint=colors[region];out=image.copy();pixels=[]
    for r,g,b,a in image.getdata():
        if a:
            lum=(r*3+g*6+b)/10
            amount=.31 if 30<lum<220 else .13
            r,g,b=[round(v*(1-amount)+(lum*(t+55)/200)*amount) for v,t in zip((r,g,b),tint)]
        pixels.append((min(255,r),min(255,g),min(255,b),a))
    out.putdata(pixels)
    return out

def emit(sprites, colors, Art, check=False):
    sources={k:Image.open(ROOT/(k+'.png')).convert('RGBA') for k in ['hero','monsters','weapons','armor','arena']}
    generated={};outdir=Path('app/src/main/res/drawable-nodpi')
    def sheet(name,frames,cols,cell_size):
        out=Image.new('RGBA',(cols*cell_size[0],math.ceil(len(frames)/cols)*cell_size[1]))
        for i,im in enumerate(frames):out.alpha_composite(im,(i%cols*cell_size[0],i//cols*cell_size[1]))
        generated[outdir/(name+'.png')]=out
    body=[sources['hero'].crop((round(i%4*sources['hero'].width/4),round(i//4*sources['hero'].height/4),round((i%4+1)*sources['hero'].width/4),round((i//4+1)*sources['hero'].height/4))).resize((192,192),NEAREST) for i in range(8)]
    # Keep the painted body scale and shared feet baseline; only add a small breathing/cloth shift.
    hero_frames=[]
    for pose in HERO_POSES:
        image=body[hero_cell(pose)].copy()
        action,n=pose.rsplit('_',1);n=int(n)
        if action!='idle' and n in [2,4]:
            shifted=Image.new('RGBA',image.size);shifted.alpha_composite(image,(0,-1 if n==2 else 1));image=shifted
        hero_frames.append(image)
    sheet('combat_hero_motion',hero_frames,12,(192,192))
    families=['slime','knight','bat','spider','golem','wisp','boss']
    monsters=[f'monster_{r}_{f}' for r in range(5) for f in families]
    monster_frames=[];monster_hands=[]
    for name in monsters:
        _,region,family=name.split('_');region=int(region)
        idle=cell(sources['monsters'],families.index(family)*2,4,4)
        attack=cell(sources['monsters'],families.index(family)*2+1,4,4)
        height=sprites[name].h*3
        idle=silhouette(idle);attack=silhouette(attack)
        common_scale=min(138/max(idle.width,attack.width),(height-6)/max(idle.height,attack.height))
        for pose in MONSTER_POSES:
            action,n=pose.rsplit('_',1);n=int(n)
            striking=action=='attack' and n in [2,3,4]
            chosen=attack if striking else idle
            if family=='wisp' and action in ['hit','death']:chosen=silhouette(cell(sources['monsters'],14 if action=='hit' else 15,4,4))
            resized=chosen.resize((max(1,round(chosen.width*common_scale)),max(1,round(chosen.height*common_scale))),NEAREST)
            frame=Image.new('RGBA',(144,height));offset=((144-resized.width)//2,height-3-resized.height)
            frame.alpha_composite(resized,offset);frame=regional(frame,region)
            fx,fy=((.06,.50) if striking else (.11,.72)) if family=='knight' else ((.08,.46) if striking else (.18,.70))
            hand=[round(offset[0]+resized.width*fx)-24,round(offset[1]+resized.height*fy)]
            if action=='idle' and n:
                shifted=Image.new('RGBA',frame.size);shifted.alpha_composite(frame,(0,-1));frame=shifted;hand[1]-=1
            elif action=='hit':
                dx=(-1 if n%2 else 1)*(n+1);shifted=Image.new('RGBA',frame.size);shifted.alpha_composite(frame,(dx,0));frame=shifted;hand[0]+=dx
            elif action=='death' and family!='wisp':
                hh=max(8,round(height*(1-.13*(n+1))));collapsed=frame.resize((144,hh),NEAREST)
                frame=Image.new('RGBA',(144,height));frame.alpha_composite(collapsed,(0,height-hh));hand[1]=round(height-hh+hand[1]*hh/height)
            monster_frames.append(frame);monster_hands.append(hand)
    sheet('combat_monster_motion',monster_frames,24,(144,144))
    gear=[k for k in sprites if k.startswith(('weapon_','armor_')) or k in WEAPONS+['shield','head','body','pants','boots','ring','necklace']]
    weapons=['longsword','rapier','sabre','falchion','longspear','halberd','trident','lance','warhammer','sledgehammer','mace','morningstar','knuckle','hookclaw','windclaw','katar','leatherwhip','chainwhip','thornwhip','flexiblewhip']
    armors=['shield','buckler','tower','spiked','head','leathercap','headband','heavyhelm','leatherarmor','chainmail','platearmor','leatherpants','scoutpants','platepants','leatherboots','ironboots','windboots','ring','necklace','collar']
    aliases={'sword':'longsword','spear':'longspear','hammer':'warhammer','claw':'knuckle','whip':'leatherwhip','body':'leatherarmor','pants':'leatherpants','boots':'leatherboots'}
    gear_frames=[];bounds=[];heights=[];grips=[]
    for key in gear:
        short=aliases.get(key,key.removeprefix('weapon_').removeprefix('armor_'))
        source=cell(sources['weapons'],weapons.index(short),4,5) if short in weapons else cell(sources['armor'],armors.index(short),4,5)
        height=sprites[key].h*4
        image=fit(source,(128,height),6)
        box=image.getchannel('A').getbbox();bounds.append(list(box));heights.append(height)
        # Grip points match the actual leather handle on each painted silhouette.
        grip=(64,round(height*.84))
        if short in ['rapier','sabre']:grip=(64,round(height*.86))
        if short in ['knuckle','hookclaw','windclaw','katar']:grip=(64,round(height*.85))
        if short.endswith('whip'):grip=(round(128*.24),round(height*.76))
        grips.append(list(grip));gear_frames.append(image)
    # Separate rig pieces are cropped from the same painted character atlas.
    for key,source_idx,size in [('cloth_upper',8,(32,64)),('cloth_lower',9,(32,64)),('cloth_grip',10,(32,32)),('steel_upper',11,(32,64)),('steel_lower',12,(32,64)),('steel_grip',13,(32,32))]:
        image=fit(cell(sources['hero'],source_idx,4,4),size,0)
        gear.append(key);gear_frames.append(image);bounds.append([0,0,*size]);heights.append(size[1]);grips.append([0,0])
    sheet('combat_equipment_motion',gear_frames,10,(128,192))
    generated[outdir/'combat_arena_refined.png']=sources['arena'].resize((960,640),NEAREST)
    def strings(v):return 'listOf('+','.join(json.dumps(x) for x in v)+')'
    def ints(v):return 'intArrayOf('+','.join(str(x) for x in v)+')'
    text='package com.powerfulhimchan.mapexit\n\n/** Generated from the painted production sources in art/refined045. */\ninternal object MotionAtlas {\n'
    text+='    val heroPoses = '+strings(HERO_POSES)+'\n'
    text+='    val heroSourceCells = '+ints([hero_cell(p) for p in HERO_POSES])+'\n'
    for field,points in [('heroShoulders',RIGHT),('heroLeftShoulders',LEFT)]:
        text+='    val '+field+' = listOf('+','.join(ints([points[hero_cell(p)][0]-32,points[hero_cell(p)][1]]) for p in HERO_POSES)+')\n'
    text+='    val worn = listOf('+','.join('listOf('+','.join(ints(rect) for rect in row)+')' for row in WORN)+')\n'
    text+='    val monsterPoses = '+strings(MONSTER_POSES)+'\n'
    text+='    val monsters = '+strings(monsters)+'\n'
    text+='    val monsterHeights = '+ints([sprites[k].h*3 for k in monsters])+'\n'
    text+='    val monsterHands = listOf('+','.join(ints(p) for p in monster_hands)+')\n'
    text+='    val gear = '+strings(gear)+'\n'
    text+='    val gearHeights = '+ints(heights)+'\n'
    text+='    val gearBounds = listOf('+','.join(ints(b) for b in bounds)+')\n'
    text+='    val gearGrips = listOf('+','.join(ints(g) for g in grips)+')\n'
    text+='    val palette = '+ 'intArrayOf('+','.join('0x'+c+'.toInt()' for c in colors)+')\n}\n'
    generated[Path('app/src/main/java/com/powerfulhimchan/mapexit/MotionAtlas.kt')]=text.encode()
    for path,data in generated.items():
        if check:
            if not path.exists():raise SystemExit(f'{path} is missing')
            equal=Image.open(path).convert('RGBA').tobytes()==data.tobytes() and Image.open(path).size==data.size if isinstance(data,Image.Image) else path.read_bytes()==data
            if not equal:raise SystemExit(f'{path} differs from painted source assets')
        else:
            path.parent.mkdir(parents=True,exist_ok=True)
            if isinstance(data,Image.Image):
                encoded=io.BytesIO(); data.save(encoded,format="PNG",optimize=True); payload=encoded.getvalue()
            else:payload=data
            temporary=path.with_suffix(path.suffix+".tmp"); temporary.write_bytes(payload); temporary.replace(path)
    print(f'Refined painted battle art: hero {len(hero_frames)}, monsters {len(monster_frames)}, equipment/limbs {len(gear_frames)}')
