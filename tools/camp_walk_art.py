"""Pack the authored directional walk bodies without repainting their pixels."""
import io
from collections import deque
from pathlib import Path
from PIL import Image
from battle_motion_art import fit

def main_body(image):
    # Ignore isolated generated gutter specks when measuring the body's height.
    w,h=image.size;alpha=image.getchannel('A');pixels=alpha.load()
    unseen={(x,y) for y in range(h) for x in range(w) if pixels[x,y]>96}
    largest=[]
    while unseen:
        point=unseen.pop();queue=deque([point]);component=[point]
        while queue:
            x,y=queue.popleft()
            for p in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)):
                if p in unseen:unseen.remove(p);queue.append(p);component.append(p)
        if len(component)>len(largest):largest=component
    xs,ys=zip(*largest)
    return image.crop((min(xs),min(ys),max(xs)+1,max(ys)+1))

def emit(check=False):
    source=Image.open('art/refined057/camp-walk.png').convert('RGBA')
    atlas=Image.new('RGBA',(768,384))
    for i in range(8):
        x,y=i%4,i//4
        frame=source.crop((round(x*source.width/4)+2,round(y*source.height/2)+2,
                           round((x+1)*source.width/4)-2,round((y+1)*source.height/2)-2))
        frame=fit(main_body(frame),(192,192),4)
        atlas.alpha_composite(frame,(x*192,y*192))
    target=Path('app/src/main/res/drawable-nodpi/camp_walk_057.png')
    if check:
        old=Image.open(target).convert('RGBA')
        assert old.size==atlas.size and old.tobytes()==atlas.tobytes()
    else:
        data=io.BytesIO();atlas.save(data,format='PNG',optimize=True);target.write_bytes(data.getvalue())
    print('Directional camp walk: four right and four left full body frames')

if __name__=='__main__':
    import sys
    emit('--check' in sys.argv)
