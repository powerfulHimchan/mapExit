#!/usr/bin/env python3
"""Author the small, deterministic 32-pixel sprite atlas used by the native renderer.

The palette and pixel geometry below are the editable originals. This does not
resize an old sprite: the clothing seams, hair clusters, buckles and equipment
are painted on the finer grid. Run from the repository root after editing.
"""
from pathlib import Path
import json
import sys

COLORS = ['00000000','ff101a24','ff1b2931','ff344550','ff6c7a7c','ffaab7ad','ffe3e7c8',
 'ff203c38','ff325b4b','ff4a7762','ff7b9b78','ffabc597',
 'ff3c2d28','ff6d4c35','ff9b7549','ffa87842','ffd5a756','fff2cf80','ffffe4a3',
 'ffa47659','ffd7ab7b','fff1d2a1','ff77532d','ffbc9048','ffe7c477','fffff0aa',
 'ff355849','ff5a8960','ff85b977','ffb4dc93','ffe0efb3',
 'ff302d49','ff635277','ff9a7d97','ff5d313a','ffae5c50','ffe39770',
 'ff2c3e48','ff536774','ff8b9c97','ff357f7b','ff8dd9bc','ffd7f4c7',
 'ff422f2b','ff77533c','ffb38b54','ffb14a2e','ffed8a38','ffffcc64']
CHARS='0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'
class Art:
 def __init__(self,w=32,h=48): self.w,self.h=w,h; self.p=[[0]*w for _ in range(h)]
 def rect(self,x,y,w,h,c):
  for yy in range(max(0,y),min(self.h,y+h)):
   for xx in range(max(0,x),min(self.w,x+w)): self.p[yy][xx]=c
 def poly(self,pts,c):
  for y in range(self.h):
   for x in range(self.w):
    inside=False;j=len(pts)-1
    for i,(xi,yi) in enumerate(pts):
     xj,yj=pts[j]
     if (yi>y+.5)!=(yj>y+.5) and x+.5 < (xj-xi)*(y+.5-yi)/(yj-yi)+xi: inside=not inside
     j=i
    if inside:self.p[y][x]=c
 def line(self,x0,y0,x1,y1,c):
  steps=max(abs(x1-x0),abs(y1-y0),1)
  for i in range(steps+1): self.rect(round(x0+(x1-x0)*i/steps),round(y0+(y1-y0)*i/steps),1,1,c)
 def dots(self,coords,c):
  for x,y in coords:self.rect(x,y,1,1,c)
 def rows(self):return [''.join(CHARS[c] for c in r) for r in self.p]

sprites={}
def person(back=False,merchant=False,npc=False,knight=False,boss=False):
 a=Art();dark,mid,light=(22,23,24) if merchant else ((31,32,33) if npc else (7,8,9))
 if knight:dark,mid,light=(2,3,4)
 if boss:dark,mid,light=(34,35,36)
 # Boots and trousers are individually outlined, with heels and stitching.
 a.rect(9,34,6,10,1);a.rect(18,34,6,10,1)
 a.rect(10,34,4,8,3);a.rect(19,34,4,8,2)
 a.rect(11,35,1,6,4);a.rect(20,36,1,5,4)
 a.poly([(9,41),(15,41),(15,47),(7,47),(7,44)],1)
 a.poly([(18,41),(24,41),(26,45),(26,47),(18,47)],1)
 a.rect(9,42,5,2,13);a.rect(19,42,4,2,13)
 a.rect(9,44,5,1,14);a.rect(19,44,4,1,14)
 a.rect(8,46,7,1,3);a.rect(18,46,7,1,3)
 # Cloak, shoulder seams, sleeves, cuffs, hands, belt, metal buckle.
 a.poly([(10,14),(22,14),(28,19),(28,28),(25,32),(26,37),(19,38),(16,36),(13,38),(6,37),(7,31),(4,28),(4,19)],1)
 a.poly([(10,15),(22,15),(26,20),(25,27),(22,29),(24,35),(18,36),(16,34),(13,36),(8,35),(10,28),(6,26),(6,20)],dark)
 a.poly([(10,17),(20,16),(23,20),(21,27),(23,34),(18,35),(15,31),(12,34),(9,34),(11,24),(8,21)],mid)
 a.poly([(11,18),(15,17),(14,24),(13,32),(11,34),(12,24)],light)
 a.poly([(18,17),(21,18),(21,24),(19,29),(18,33),(18,22)],light)
 a.line(15,19,15,33,10 if not knight and not boss and not merchant and not npc else 4)
 a.line(9,20,7,25,light);a.line(24,21,24,26,light)
 a.rect(5,26,4,3,12);a.rect(24,26,4,3,12)
 a.rect(5,29,4,4,19);a.rect(6,29,2,3,20);a.rect(24,29,4,4,19);a.rect(24,29,2,3,20)
 a.rect(8,29,16,3,12);a.rect(9,29,14,1,14)
 a.rect(16,29,4,3,22);a.rect(17,29,2,2,24);a.rect(18,30,1,1,12)
 a.rect(21,31,3,4,13);a.rect(21,31,3,1,14);a.rect(22,32,1,1,23)
 if knight or boss:
  a.poly([(8,15),(14,17),(18,17),(24,15),(27,19),(24,23),(21,21),(21,28),(11,28),(11,21),(7,23),(5,19)],3 if knight else 34)
  a.rect(12,18,8,10,4 if knight else 35);a.line(13,18,13,25,5 if knight else 36);a.line(18,18,18,25,3 if knight else 34)
  a.rect(6,18,5,3,4);a.rect(22,18,5,3,4);a.rect(6,18,5,1,5);a.rect(22,18,5,1,5)
  a.rect(5,27,4,5,3);a.rect(24,27,4,5,3);a.dots([(7,28),(25,28),(12,26),(20,26)],5)
 # Neck and blond layered hair or steel helmet.
 a.rect(13,12,6,5,19);a.rect(14,12,4,4,20)
 if merchant or npc:
  a.poly([(15,0),(22,3),(26,8),(27,15),(22,18),(9,18),(5,14),(6,8),(10,3)],1)
  a.poly([(15,1),(21,4),(24,8),(25,14),(21,16),(10,16),(7,13),(8,8),(11,4)],dark)
  a.poly([(15,2),(20,5),(22,8),(10,8),(12,4)],light)
  a.poly([(12,7),(21,7),(23,12),(21,15),(11,15),(9,11)],12)
  a.rect(12,8,9,6,20);a.rect(13,8,7,2,21)
  a.rect(13,10,1,2,1);a.rect(19,10,1,2,1);a.rect(16,13,2,1,19)
  a.poly([(9,15),(14,17),(16,20),(19,17),(23,15),(21,22),(16,24),(10,21)],light)
  a.line(8,18,20,29,13);a.rect(9,27,5,6,12);a.rect(9,27,5,4,14);a.rect(10,28,3,1,23)
 elif knight or boss:
  a.poly([(11,2),(21,2),(25,6),(25,12),(21,16),(10,16),(7,12),(7,7)],1)
  a.poly([(12,3),(20,3),(23,6),(23,12),(20,14),(11,14),(9,11),(9,7)],3)
  a.poly([(12,4),(16,3),(19,4),(18,13),(14,13),(13,10)],4)
  a.line(13,4,13,8,5);a.line(15,3,15,13,5)
  a.rect(9,8,14,3,1);a.rect(11,9,4,1,35 if boss else 23);a.rect(18,9,4,1,35 if boss else 23)
  a.rect(11,12,3,1,4);a.rect(19,12,3,1,4)
  if boss:
   a.poly([(8,7),(4,5),(3,0),(7,3),(11,4)],23);a.poly([(22,4),(26,3),(29,0),(28,6),(24,8)],23)
   a.rect(14,2,4,2,36);a.rect(15,2,2,1,25)
 else:
  a.poly([(12,1),(18,1),(21,3),(24,4),(25,8),(24,11),(21,14),(10,14),(7,11),(7,5),(10,3)],1)
  a.poly([(12,2),(18,2),(20,4),(23,4),(24,8),(22,11),(20,13),(10,12),(8,10),(9,5),(11,4)],15)
  a.poly([(12,2),(18,3),(18,5),(22,5),(22,8),(20,10),(16,9),(13,11),(9,9),(9,6),(12,5)],16)
  a.poly([(12,3),(17,3),(16,5),(20,5),(19,7),(14,7),(12,9),(10,8),(11,5)],17)
  a.dots([(13,2),(12,4),(18,4),(20,6),(10,6)],18)
  a.line(10,9,10,12,15);a.line(21,9,21,12,15)
  if not back:
   a.poly([(11,7),(21,7),(22,10),(20,14),(12,14),(10,11)],20)
   a.rect(12,8,8,3,21);a.rect(12,10,2,2,1);a.rect(18,10,2,2,1)
   a.rect(14,13,4,1,19);a.rect(15,11,1,1,19)
   a.poly([(10,5),(15,5),(13,9),(11,8),(9,9)],16)
  else:a.rect(12,14,8,3,7);a.rect(13,14,6,1,10)
 return a
sprites['heroBack']=person(back=True);sprites['hero']=person();sprites['npc']=person(npc=True);sprites['merchant']=person(merchant=True);sprites['knight']=person(knight=True);sprites['boss']=person(boss=True)
a=Art(32,24)
a.poly([(8,4),(12,1),(20,1),(25,5),(28,10),(30,16),(28,21),(23,23),(7,23),(2,20),(1,15),(4,9)],1)
a.poly([(9,4),(13,2),(20,2),(24,6),(27,11),(29,16),(27,20),(23,22),(7,22),(3,19),(2,15),(5,10)],26)
a.poly([(10,5),(15,3),(21,4),(24,8),(26,14),(25,19),(22,21),(7,21),(4,18),(4,14),(7,9)],27)
a.poly([(11,5),(16,4),(21,6),(23,11),(23,16),(19,19),(8,19),(5,16),(8,9)],28)
a.poly([(12,5),(17,5),(20,7),(20,11),(15,13),(9,11)],29)
a.rect(10,5,4,2,30);a.rect(8,8,2,3,30);a.rect(10,9,1,1,30)
a.rect(10,13,3,4,1);a.rect(20,12,3,4,1);a.rect(11,13,1,1,30);a.rect(21,12,1,1,30)
a.rect(15,18,3,1,26);a.rect(7,20,4,1,29);a.rect(21,20,3,1,29);a.dots([(25,17),(6,16),(17,6)],29)
sprites['slime']=a
a=Art(32,24)
a.poly([(1,2),(9,8),(12,7),(14,2),(16,6),(19,2),(21,8),(24,8),(31,2),(31,14),(26,12),(26,18),(22,15),(20,21),(16,23),(12,21),(10,15),(6,18),(6,12),(1,14)],1)
a.poly([(2,4),(10,9),(13,8),(14,5),(16,9),(18,5),(20,9),(23,10),(30,4),(30,12),(25,10),(25,16),(21,13),(19,19),(16,21),(13,19),(11,13),(7,16),(7,10),(2,12)],31)
a.poly([(3,6),(12,11),(8,14),(7,11),(3,10)],32);a.poly([(29,6),(21,11),(24,14),(25,11),(29,10)],32)
a.line(3,6,11,12,33);a.line(29,6,21,12,33);a.rect(14,10,5,8,32);a.rect(15,11,3,7,33)
a.rect(13,11,2,2,35);a.rect(19,11,2,2,35);a.rect(15,15,1,2,6);a.rect(18,15,1,2,6)
sprites['bat']=a
a=Art(32,24)
a.poly([(4,3),(9,0),(24,0),(29,4),(31,11),(31,21),(28,24),(3,24),(0,20),(0,9)],1)
a.poly([(4,4),(9,1),(23,1),(28,5),(29,10),(2,10)],43);a.poly([(5,4),(10,2),(22,2),(27,5),(28,9),(3,9)],44)
a.rect(2,11,28,10,43);a.rect(3,12,26,8,44)
a.line(5,4,25,4,45);a.line(4,7,27,7,13);a.line(4,14,28,14,45);a.line(3,17,28,17,13);a.line(5,20,26,20,45)
a.rect(5,2,3,20,3);a.rect(24,2,3,20,3);a.rect(5,3,1,18,5);a.rect(25,3,1,18,4)
a.rect(1,9,30,3,3);a.rect(2,9,28,1,5);a.rect(1,21,30,2,3);a.rect(2,21,28,1,4)
a.rect(13,9,6,9,22);a.rect(14,10,4,6,24);a.rect(15,12,2,3,1);a.rect(15,15,2,1,23)
a.dots([(6,5),(25,5),(6,18),(25,18),(3,10),(28,10)],24);a.dots([(11,5),(20,15),(9,18),(21,7)],13)
sprites['chest']=a
a=Art(32,48)
a.poly([(8,2),(24,2),(30,9),(30,47),(2,47),(2,9)],1)
a.poly([(9,3),(23,3),(28,10),(28,45),(4,45),(4,10)],37)
a.poly([(10,6),(22,6),(25,11),(25,44),(7,44),(7,11)],1)
a.poly([(11,8),(21,8),(23,12),(23,43),(9,43),(9,12)],43)
for x in [10,14,18,22]:a.rect(x,12,1,30,45);a.rect(x+1,15,1,25,13)
a.rect(5,12,2,32,38);a.rect(25,12,2,32,38)
a.rect(9,20,15,3,3);a.rect(9,34,15,3,3);a.rect(10,20,13,1,5);a.rect(10,34,13,1,4)
a.rect(20,25,3,6,22);a.rect(21,26,1,3,24)
a.rect(3,43,26,4,38);a.rect(4,43,24,1,39)
for y in [12,22,32]:a.rect(3,y,3,1,1);a.rect(26,y+3,3,1,1)
a.dots([(8,8),(24,8),(4,40),(26,39)],39)
sprites['door']=a
a=Art(32,40)
a.poly([(7,25),(25,25),(29,30),(29,36),(25,39),(6,39),(2,35),(3,30)],1)
a.rect(6,29,21,7,37);a.rect(5,28,20,2,39);a.rect(7,35,19,2,38)
a.poly([(15,1),(21,8),(23,16),(16,26),(8,16),(10,8)],1)
a.poly([(15,3),(20,9),(21,15),(16,23),(10,15),(12,9)],40)
a.poly([(15,4),(17,10),(17,18),(15,22),(12,15),(13,9)],41)
a.line(15,5,15,16,42);a.rect(16,9,2,4,42)
a.dots([(6,9),(25,6),(4,20),(27,22)],41);a.rect(4,8,3,1,42);a.rect(5,7,1,3,42)
sprites['heal']=a
used=Art(a.w,a.h)
used.p=[[{40:3,41:4,42:5}.get(v,v) for v in row] for row in a.p]
sprites['heal_used']=used
a=Art(32,48)
a.rect(10,22,12,18,1);a.rect(12,23,8,16,12);a.rect(13,24,3,13,44);a.line(15,27,15,37,45)
a.rect(8,21,16,4,3);a.rect(9,21,14,1,5);a.rect(10,24,12,1,4)
a.rect(7,32,18,3,3);a.rect(8,32,16,1,5);a.rect(7,32,2,6,3);a.rect(23,32,2,6,3)
a.poly([(11,21),(9,17),(10,12),(14,8),(14,2),(18,6),(20,12),(23,8),(24,16),(21,21)],46)
a.poly([(12,20),(11,16),(14,12),(15,6),(18,12),(20,14),(22,12),(22,18),(20,21)],47)
a.poly([(15,20),(13,17),(16,12),(16,9),(19,15),(19,20)],48)
a.rect(16,16,2,5,25);a.dots([(13,4),(22,4),(25,10),(9,7)],47)
sprites['torch']=a
# Individual 32px equipment icons also serve as held items and inheritance rewards.
for kind in ['sword','spear','hammer','claw','whip','shield','head','body','pants','boots','ring','necklace','potion','coin']:
 a=Art(32,48 if kind=='spear' else 32)
 if kind in ['sword','spear']:
  top=1;guard=36 if kind=='spear' else 23;bottom=46 if kind=='spear' else 31
  a.poly([(16,top),(20,top+7),(18,guard),(18,bottom),(13,bottom),(13,guard),(12,top+7)],1)
  if kind=='spear':a.rect(15,10,2,35,13);a.rect(15,10,1,35,24)
  else:a.rect(14,6,4,17,4);a.rect(14,5,2,17,6);a.rect(17,8,1,13,5)
  a.poly([(16,2),(18,7),(16,12 if kind=='spear' else 6),(14,7)],5)
  a.rect(10,guard,12,3,22);a.rect(11,guard,10,1,24)
  a.rect(14,guard+3,4,bottom-guard-3,13);a.rect(15,guard+3,1,bottom-guard-3,45);a.rect(13,bottom-2,6,2,23)
 elif kind=='hammer':
  a.rect(14,9,4,22,1);a.rect(15,10,2,19,13);a.rect(15,18,1,9,45)
  a.poly([(5,3),(24,3),(28,6),(28,15),(23,17),(4,15),(3,6)],1)
  a.rect(5,5,20,9,3);a.rect(6,5,18,3,5);a.rect(6,8,16,4,4);a.rect(11,5,3,9,23);a.rect(12,5,1,8,24)
 elif kind=='claw':
  a.poly([(7,16),(10,12),(22,12),(25,17),(23,26),(9,26)],1);a.rect(9,15,14,8,13);a.rect(10,16,12,2,14)
  for x in [9,15,21]:a.poly([(x,3),(x+3,1),(x+3,18),(x,20)],4);a.rect(x+1,4,1,14,6)
  a.rect(10,23,12,3,3);a.dots([(12,24),(20,24)],23)
 elif kind=='whip':
  a.rect(5,21,5,10,1);a.rect(6,22,3,8,13);a.rect(6,22,1,8,24)
  for c,shift in [(1,0),(13,1),(45,2)]:
   pts=[(7+shift,22),(8+shift,10),(12+shift,5),(23+shift,5),(27+shift,10),(26+shift,17),(21+shift,19),(16+shift,15)]
   for p,q in zip(pts,pts[1:]):a.line(*p,*q,c)
 elif kind=='shield':
  a.poly([(16,1),(28,6),(27,22),(23,27),(16,31),(8,27),(4,21),(3,6)],1)
  a.poly([(16,3),(26,7),(25,21),(21,26),(16,29),(9,25),(6,20),(5,7)],4)
  a.poly([(16,5),(23,8),(22,20),(19,24),(16,26),(11,23),(8,18),(8,9)],37)
  a.poly([(10,9),(15,7),(15,24),(11,21),(9,17)],38);a.rect(15,5,2,23,23);a.rect(15,5,1,21,24)
  a.rect(12,13,8,6,3);a.rect(14,14,4,4,24);a.dots([(7,9),(24,9),(10,23),(22,23)],6)
 elif kind=='head':
  a.poly([(10,4),(22,4),(27,10),(27,24),(22,27),(9,27),(5,24),(5,11)],1)
  a.poly([(11,6),(21,6),(25,11),(25,23),(21,25),(10,25),(7,22),(7,12)],3)
  a.rect(12,7,8,17,4);a.rect(14,6,3,19,5);a.rect(15,7,1,15,6)
  a.rect(7,15,18,4,1);a.rect(9,16,5,1,23);a.rect(18,16,5,1,23)
 elif kind=='body':
  a.poly([(11,3),(21,3),(29,8),(27,17),(24,16),(25,29),(7,29),(8,16),(4,17),(2,8)],1)
  a.poly([(11,5),(21,5),(27,9),(25,14),(22,12),(23,27),(9,27),(10,12),(6,14),(4,9)],3)
  a.rect(11,9,11,15,4);a.rect(12,10,3,10,5);a.rect(16,9,1,16,23);a.rect(7,25,18,3,13)
 elif kind=='pants':
  a.poly([(7,3),(25,3),(25,30),(17,30),(16,16),(15,30),(6,30)],1);a.rect(8,5,15,7,8)
  a.rect(8,12,6,16,9);a.rect(18,12,5,16,8);a.rect(9,13,1,12,10);a.rect(19,13,1,12,9)
  a.rect(8,5,16,3,13);a.rect(15,5,4,3,23)
 elif kind=='boots':
  for x in [3,18]:
   a.poly([(x,6),(x+8,6),(x+8,21),(x+11,25),(x+11,29),(x-1,29),(x-1,23)],1)
   a.rect(x+1,8,6,17,13);a.rect(x+2,9,2,13,14);a.rect(x,25,10,2,44);a.rect(x-1,28,12,1,3)
   for y in [12,16,20]:a.rect(x+3,y,3,1,24)
 elif kind in ['ring','necklace']:
  cx,cy=16,18 if kind=='ring' else 13
  for x0,y0,x1,y1 in [(8,cy-7,22,cy-7),(23,cy-6,25,cy+4),(22,cy+6,9,cy+6),(7,cy+4,7,cy-5)]:
   a.line(x0,y0,x1,y1,22);a.line(x0+1,y0+1,x1+1,y1+1,24)
  a.poly([(16,3),(22,8),(16,13),(10,8)],1);a.poly([(16,5),(20,8),(16,11),(12,8)],40);a.rect(15,5,2,3,42)
  if kind=='necklace':a.poly([(16,22),(21,26),(16,31),(11,26)],23);a.rect(15,24,3,4,41)
 elif kind=='potion':
  a.rect(12,1,9,6,1);a.rect(13,2,7,4,13);a.rect(14,2,2,3,45)
  a.poly([(11,7),(22,7),(22,12),(27,18),(27,27),(23,31),(9,31),(5,27),(5,18),(11,12)],1)
  a.poly([(13,8),(20,8),(20,13),(25,19),(25,26),(22,29),(10,29),(7,26),(7,19),(13,13)],38)
  a.rect(8,19,16,7,35);a.rect(10,26,12,2,34);a.rect(10,16,2,9,6);a.rect(12,14,2,3,6)
  a.rect(12,20,8,4,24);a.rect(15,19,2,6,24)
 else:
  a.poly([(12,3),(21,3),(27,9),(28,20),(21,28),(11,29),(4,22),(3,11)],22)
  a.poly([(12,5),(20,5),(25,10),(26,19),(20,26),(12,27),(6,21),(5,12)],24)
  a.rect(13,9,7,2,25);a.rect(11,11,3,10,23);a.rect(13,21,7,2,23);a.rect(18,16,3,6,23)
 sprites[kind]=a

# Distinct weapon variants retain their real grips so menus and battle use one asset.
import copy
variants = {
 'sword': ['longsword','rapier','sabre','falchion'],
 'spear': ['longspear','halberd','trident','lance'],
 'hammer': ['warhammer','sledgehammer','mace','morningstar'],
 'claw': ['knuckle','hookclaw','windclaw','katar'],
 'whip': ['leatherwhip','chainwhip','thornwhip','flexiblewhip']}
for family, names in variants.items():
 for index, name in enumerate(names):
  a=copy.deepcopy(sprites[family])
  if index:
   if family=='sword':
    a.rect(8,0,17,23,0)
    if index==1:
     a.poly([(16,1),(17,5),(17,23),(15,23),(15,5)],5);a.line(16,3,16,21,6)
     a.poly([(10,22),(20,22),(23,25),(21,28),(19,25),(10,25)],23)
    elif index==2:
     a.poly([(23,1),(23,7),(19,14),(18,23),(13,23),(15,11),(20,5)],1)
     a.poly([(22,3),(21,8),(17,15),(17,22),(15,22),(17,12)],5)
    else:
     a.poly([(16,1),(23,6),(22,15),(18,23),(12,23),(12,10)],1)
     a.poly([(16,3),(21,7),(20,15),(17,22),(14,22),(14,10)],4);a.line(16,5,16,20,6)
   elif family=='spear':
    a.rect(2,0,28,20,0)
    if index==1:
     a.poly([(16,0),(19,6),(18,18),(14,18),(14,6)],5)
     a.poly([(17,5),(26,7),(28,15),(24,18),(18,15)],4);a.line(23,8,26,15,6)
    elif index==2:
     for x in [6,15,24]:a.poly([(x,1),(x+2,5),(x+2,13),(x,15),(x-1,5)],5)
     a.rect(6,13,20,3,23);a.rect(15,12,2,11,13)
    else:
     a.poly([(16,0),(22,16),(17,23),(11,16)],1);a.poly([(16,2),(19,16),(16,21),(13,16)],6)
   elif family=='hammer':
    a.rect(0,0,32,18,0)
    if index==1:
     a.rect(1,2,30,16,1);a.rect(3,4,26,12,3);a.rect(4,4,24,3,5);a.rect(8,4,3,12,23);a.rect(23,4,3,12,23)
    elif index==2:
     a.poly([(12,2),(20,2),(24,8),(21,16),(11,16),(8,8)],1)
     a.rect(12,4,8,10,4);a.rect(15,2,2,14,6);a.rect(9,7,14,2,5)
    else:
     a.poly([(16,0),(19,4),(25,3),(24,8),(30,10),(24,13),(25,18),(19,16),(16,20),(13,16),(7,18),(8,13),(2,10),(8,7),(7,3),(13,4)],1)
     a.poly([(16,4),(22,7),(22,13),(16,16),(10,13),(10,7)],4);a.rect(14,7,4,6,23)
   elif family=='claw':
    a.rect(0,0,32,20,0)
    if index==1:
     for x in [6,14,22]:a.poly([(x,5),(x+5,1),(x+6,2),(x+2,8),(x+2,18),(x-1,20)],5)
    elif index==2:
     for x in [8,15,22]:a.poly([(x+5,1),(x+2,12),(x,20),(x-2,17),(x-1,8)],6)
    else:
     a.poly([(16,0),(23,14),(20,22),(12,22),(9,14)],1)
     a.poly([(16,2),(20,14),(17,22),(14,22),(12,14)],5);a.line(16,5,16,21,6)
    a.rect(10,19,12,4,13);a.rect(10,23,12,3,3)
   elif family=='whip':
    if index==1:
     for x,y in [(8,13),(10,7),(16,5),(23,6),(27,11),(24,17),(18,16)]:
      a.rect(x-1,y-1,4,3,4);a.rect(x,y,2,1,6)
    elif index==2:
     for x,y in [(8,12),(12,5),(22,5),(27,11),(22,18)]:a.poly([(x,y),(x+4,y-3),(x+2,y+2)],23)
    else:
     a.rect(11,2,20,19,0)
     a.poly([(8,12),(13,4),(21,2),(25,4),(23,6),(15,7),(11,14)],4)
     a.line(12,6,22,3,6)
  sprites['weapon_'+name]=a
for role in range(6):
 a=copy.deepcopy(sprites['ring']);a.rect(8,0,17,14,0)
 gems=[35,41,32,24,28,5]
 a.poly([(16,1),(22,5),(21,11),(11,11),(10,5)],1)
 a.poly([(16,3),(20,6),(19,9),(13,9),(12,6)],gems[role]);a.rect(14,4,2,2,6)
 # Each role has a different crown/socket silhouette.
 for j in range(role+1):a.rect(9+j*2,12+(j%2),1,2,23)
 sprites['ring_'+str(role)]=a
for index,role in enumerate(['none','map','chest','merchant','trap','quest']):
 a=copy.deepcopy(sprites['necklace']);a.rect(9,20,15,12,0)
 if index%3==0:a.poly([(16,21),(23,26),(16,31),(9,26)],23)
 elif index%3==1:a.rect(10,21,13,10,23);a.rect(12,23,9,6,38)
 else:a.poly([(12,21),(20,21),(23,26),(20,31),(12,31),(9,26)],23)
 a.rect(14,23,4,5,[5,28,24,35,41,32][index]);a.rect(15,23,1,2,6)
 sprites['necklace_'+role]=a
# Achievement seal and greater potion have their own authored silhouettes.
a=Art(32,32)
a.poly([(8,12),(13,8),(20,8),(25,12),(27,20),(23,28),(9,28),(5,20)],1)
a.poly([(9,13),(14,10),(19,10),(23,13),(25,20),(21,26),(11,26),(7,20)],23)
a.poly([(12,15),(20,15),(22,20),(19,24),(13,24),(10,20)],1)
a.poly([(16,0),(25,5),(23,13),(16,17),(9,13),(7,5)],22)
a.poly([(16,2),(23,6),(21,12),(16,15),(11,12),(9,6)],24)
a.rect(12,5,8,7,1);a.rect(14,6,4,7,41);a.rect(15,6,2,4,42)
a.rect(18,9,2,3,25);a.dots([(8,16),(24,16),(11,26),(21,26)],6)
sprites['ring_exit_seal']=a
a=Art(32,32)
a.rect(10,0,13,6,1);a.rect(11,1,11,4,23);a.rect(13,1,2,3,25)
a.poly([(10,6),(23,6),(23,11),(28,16),(28,27),(24,32),(8,32),(4,27),(4,16),(10,11)],1)
a.poly([(12,7),(21,7),(21,12),(26,17),(26,26),(23,30),(9,30),(6,26),(6,17),(12,12)],38)
a.rect(7,18,18,9,32);a.rect(10,27,12,2,31);a.rect(8,15,2,10,6)
a.poly([(16,13),(23,21),(16,28),(10,21)],23)
a.rect(13,19,7,3,25);a.rect(15,17,3,7,25);a.rect(14,14,2,3,6)
sprites['potion_greater']=a
# Optional challenge portal, rune pedestals, brazier and physical mechanisms.
a=Art(32,48)
a.poly([(6,47),(3,43),(3,12),(7,4),(13,0),(20,0),(27,6),(30,14),(30,44),(27,47)],1)
a.poly([(7,45),(5,42),(5,13),(9,6),(14,3),(19,3),(25,8),(28,14),(28,43),(26,45)],3)
a.poly([(9,43),(8,14),(12,8),(16,6),(21,9),(25,15),(25,43)],31)
a.poly([(11,42),(11,16),(14,11),(18,11),(22,17),(22,42)],32)
a.line(14,16,20,36,33);a.line(20,16,14,36,41);a.rect(13,26,9,3,42)
for x,y in [(7,14),(26,14),(7,27),(26,27),(7,39),(26,39)]:a.rect(x,y,2,3,41)
a.rect(3,44,27,3,4);a.rect(5,44,23,1,5)
sprites['challenge_portal']=a
for mark in range(1,4):
 a=Art(32,40)
 a.rect(5,33,23,6,1);a.rect(7,33,19,4,4);a.rect(10,20,13,14,3);a.rect(12,21,3,11,5)
 a.poly([(4,15),(8,10),(25,10),(29,15),(25,21),(8,21)],1);a.rect(7,14,19,5,4)
 if mark==1:
  a.rect(13,2,7,7,24);a.rect(15,0,3,11,25);a.rect(11,4,11,3,25)
 elif mark==2:
  a.poly([(19,0),(11,3),(10,8),(15,11),(22,8),(17,8),(14,5)],41)
 else:
  a.poly([(16,0),(18,4),(23,4),(19,7),(21,11),(16,9),(12,11),(13,7),(9,4),(14,4)],24)
 sprites['challenge_altar_'+str(mark)]=a
a=Art(32,40)
a.rect(4,34,25,5,3);a.rect(12,20,9,15,4);a.rect(14,22,2,11,5)
a.poly([(3,14),(29,14),(24,23),(8,23)],1);a.rect(6,15,20,5,23)
a.poly([(16,0),(19,7),(24,3),(24,12),(20,17),(10,17),(7,10),(11,5),(13,9)],46)
a.poly([(16,5),(21,11),(18,16),(12,15),(12,10)],48)
sprites['challenge_brazier']=a
for kind in ['blades','falling','wall']:
 a=Art(32,32)
 if kind=='blades':
  a.rect(1,25,30,5,3)
  for x in [2,9,16,23]:a.poly([(x,25),(x+3,4),(x+6,25)],5)
 elif kind=='falling':
  a.rect(5,0,2,22,4);a.rect(25,0,2,22,4);a.rect(2,18,29,9,13);a.rect(4,18,3,9,5);a.rect(25,18,3,9,5)
 else:
  a.rect(1,1,11,30,3);a.rect(21,1,10,30,3);a.rect(10,1,2,30,5);a.rect(21,1,2,30,5)
 sprites['challenge_'+kind]=a
# New silhouettes: eight-legged crawler, heavy stone body and floating flame.
a=Art(32,28)
for xsign in [-1,1]:
 for i in range(4):
  x=16+xsign*6;y=12+i*2
  a.line(x,y,16+xsign*13,4+i*5,1);a.line(16+xsign*13,4+i*5,16+xsign*15,8+i*6,4)
a.poly([(11,5),(21,5),(24,12),(21,21),(11,21),(8,12)],1);a.rect(11,8,10,10,13);a.rect(13,7,6,5,14)
a.rect(10,19,13,7,3);a.dots([(12,20),(15,20),(18,20),(21,20)],35);a.rect(12,25,2,3,6);a.rect(19,25,2,3,6)
sprites['spider']=a
a=Art(32,48)
a.poly([(8,3),(24,3),(27,14),(30,18),(31,36),(25,37),(25,47),(18,47),(16,35),(14,47),(6,47),(6,37),(1,36),(2,18),(6,14)],1)
a.rect(8,5,16,12,4);a.rect(10,6,12,4,5);a.rect(10,12,4,2,40);a.rect(19,12,4,2,40)
a.poly([(8,18),(24,18),(27,28),(22,37),(10,37),(5,28)],3);a.rect(10,20,11,12,4);a.rect(14,23,5,5,41)
a.rect(3,21,4,12,4);a.rect(26,21,4,12,4);a.rect(7,39,6,7,3);a.rect(19,39,6,7,3);a.line(10,22,13,25,1)
sprites['golem']=a
a=Art(32,36)
a.poly([(16,0),(22,8),(24,4),(28,16),(25,27),(18,35),(16,31),(10,35),(4,26),(5,16),(10,8)],1)
a.poly([(16,3),(20,12),(24,9),(25,20),(22,28),(17,32),(15,27),(10,31),(7,24),(8,16),(12,11)],40)
a.poly([(16,9),(19,17),(21,14),(23,22),(19,28),(13,27),(10,22),(14,16)],41)
a.rect(11,19,3,3,1);a.rect(19,19,3,3,1);a.rect(15,25,3,2,6)
sprites['wisp']=a
def depth_monster(band,family):
 # Each region has authored anatomy, with the original family retained for combat behavior.
 a=Art(32,sprites[family].h)
 dark,mid,light,glow=[[26,27,28,29],[37,38,40,41],[31,32,33,42],[34,35,46,47],[2,3,37,40]][band]
 if family=='slime':
  if band==1: # Low tidal body, fan fins and curling feelers.
   a.poly([(2,20),(5,13),(10,11),(13,5),(20,5),(24,11),(27,12),(30,20),(27,23),(5,23)],1)
   a.poly([(4,20),(7,14),(12,13),(15,7),(19,7),(22,14),(26,14),(28,20),(24,22),(7,22)],mid)
   a.poly([(5,16),(0,10),(1,20),(8,21)],light);a.poly([(25,16),(31,9),(31,20),(24,21)],light)
   a.rect(13,12,9,6,dark);a.dots([(14,13),(20,13)],glow);a.line(14,18,20,18,1)
  elif band==2: # Asymmetric crystal cluster.
   for x,y,w in [(2,12,8),(9,2,12),(19,8,11)]:
    a.poly([(x,y+6),(x+w//2,y),(x+w,y+6),(x+w-2,22),(x+2,23)],1)
    a.poly([(x+2,y+7),(x+w//2,y+2),(x+w-2,y+7),(x+w-3,21),(x+3,21)],mid)
    a.line(x+w//2,y+3,x+w//2,20,light)
   a.rect(12,15,3,2,glow);a.rect(19,15,3,2,glow)
  elif band==3: # Molten quadruped with a pointed tail.
   a.poly([(0,7),(7,11),(10,7),(24,8),(29,13),(30,20),(25,23),(20,18),(13,18),(10,23),(5,23),(6,16)],1)
   a.poly([(3,10),(8,13),(12,10),(23,10),(27,14),(27,19),(24,21),(21,16),(12,16),(8,21),(7,20),(9,15)],mid)
   a.poly([(10,10),(13,2),(17,10),(21,4),(24,11)],light)
   a.rect(23,12,4,3,glow);a.line(12,12,21,14,glow)
  else: # Open maw with a crown of tendrils.
   for x in [2,8,23,29]:
    a.poly([(x,1),(x+2,4),(16,13),(16,19),(x,8)],mid)
   a.poly([(5,9),(25,9),(30,16),(27,23),(4,23),(1,16)],1)
   a.poly([(6,10),(24,10),(28,16),(24,21),(7,21),(4,16)],light)
   a.poly([(10,11),(23,12),(25,17),(21,20),(10,19),(7,15)],1)
   for x in [10,15,20]:a.poly([(x,12),(x+3,12),(x+1,16)],glow)
   a.rect(14,18,5,2,glow)
 elif family in ['knight','boss']:
  boss=family=='boss'
  if band==1: # Deep diver with a spherical helmet and broad fins.
   a.poly([(9,5),(22,5),(27,11),(26,20),(29,25),(26,37),(24,37),(26,47),(17,47),(16,36),(14,47),(5,47),(8,35),(3,26),(6,20),(5,11)],1)
   a.poly([(10,7),(21,7),(24,12),(23,19),(9,20),(7,13)],mid)
   a.rect(9,12,14,6,1);a.rect(11,13,10,4,glow);a.line(16,13,16,16,dark)
   a.poly([(8,22),(23,21),(26,27),(23,35),(9,35),(6,28)],mid)
   a.rect(12,24,8,8,dark);a.rect(14,26,4,4,glow)
   for x in [7,20]:a.poly([(x,36),(x+5,36),(x+5,43),(x+9,46),(x-3,46),(x,42)],light)
   a.line(7,16,3,21,light);a.line(3,21,6,32,glow)
   if boss:
    a.poly([(1,19),(8,23),(9,31),(3,28)],light);a.poly([(23,23),(31,16),(29,29),(24,32)],light)
    a.poly([(12,5),(12,0),(16,3),(20,0),(21,5)],glow)
  elif band==2: # Narrow prismatic guardian with diamond limbs.
   a.poly([(16,0),(24,10),(21,17),(30,22),(25,29),(22,28),(24,35),(23,46),(18,47),(16,35),(13,47),(8,46),(9,35),(11,28),(5,29),(1,22),(10,17),(8,10)],1)
   a.poly([(16,2),(22,10),(19,17),(12,17),(10,10)],mid)
   a.line(16,3,16,14,glow);a.rect(12,11,9,2,1);a.dots([(13,11),(19,11)],glow)
   a.poly([(11,18),(21,18),(24,25),(20,34),(12,34),(8,25)],mid)
   a.poly([(16,19),(20,25),(16,31),(12,25)],glow)
   for x in [4,23]:a.poly([(x,16),(x+5,21),(x+2,28),(x-2,22)],light)
   for x in [9,19]:a.poly([(x+2,35),(x+4,40),(x+3,46),(x,45)],light)
   if boss:
    for x in [3,10,19,27]:a.poly([(x,2),(x+2,0),(x+3,12),(x,15)],glow)
    a.poly([(4,27),(8,22),(10,36),(3,40)],mid);a.poly([(24,22),(29,27),(29,40),(22,36)],light)
  elif band==3: # Horned furnace armor with massive angular shoulders.
   a.poly([(7,16),(5,7),(1,1),(8,4),(12,10),(20,10),(25,3),(31,1),(27,10),(25,17),(31,22),(28,31),(24,33),(25,45),(18,47),(16,36),(14,47),(6,45),(8,33),(3,31),(0,22)],1)
   a.poly([(9,17),(12,11),(20,11),(23,17),(20,22),(11,22)],mid)
   a.rect(11,16,3,3,glow);a.rect(19,16,3,3,glow);a.rect(14,21,5,2,glow)
   a.poly([(7,23),(12,25),(20,25),(25,22),(27,30),(22,36),(10,36),(5,30)],dark)
   a.poly([(15,24),(21,29),(18,35),(12,32),(11,28)],light);a.line(15,26,17,32,glow)
   for x in [6,22]:a.poly([(x,23),(x+3,16),(x+7,23),(x+4,28)],light)
   a.rect(8,37,5,8,mid);a.rect(19,37,5,8,mid)
   if boss:
    a.poly([(3,25),(0,17),(7,20),(10,29)],glow);a.poly([(22,27),(26,17),(31,15),(29,27)],glow)
    a.rect(13,2,6,8,light)
  else: # Floating hood, hollow mask and split spectral robes.
   a.poly([(16,0),(24,5),(27,14),(23,20),(30,25),(26,35),(30,46),(22,43),(18,48),(15,42),(10,47),(5,43),(1,46),(7,33),(2,25),(9,20),(5,14),(8,6)],1)
   a.poly([(16,2),(22,7),(24,14),(20,20),(11,19),(8,14),(10,8)],mid)
   a.poly([(12,9),(21,9),(22,15),(19,19),(13,18),(10,14)],glow)
   a.rect(12,12,3,2,1);a.rect(18,12,3,2,1);a.line(16,15,16,18,1)
   a.poly([(10,21),(21,21),(25,29),(21,34),(26,43),(19,39),(16,45),(12,38),(6,43),(10,32),(6,27)],dark)
   a.line(16,23,13,38,light);a.line(20,25,21,36,glow)
   if boss:
    for x in [0,26]:a.poly([(x,3),(x+5,9),(x+6,22),(x+1,19)],glow)
    a.poly([(13,0),(16,5),(19,0),(20,7),(12,7)],light)
 elif family=='bat':
  if band==1: # Manta ray, long tail and scalloped wings.
   a.poly([(0,2),(10,9),(15,5),(20,9),(31,2),(29,16),(22,14),(17,20),(16,24),(13,20),(8,14),(2,16)],1)
   a.poly([(2,5),(11,11),(15,7),(19,11),(29,5),(27,13),(21,12),(16,18),(14,18),(9,12),(4,13)],mid)
   a.line(15,13,16,22,glow);a.rect(12,9,2,2,glow);a.rect(18,9,2,2,glow)
  elif band==2: # Four diamond wings.
   for x,y in [(0,1),(19,1),(2,13),(19,13)]:
    a.poly([(x+6,y),(x+12,y+6),(x+6,y+11),(x,y+6)],1)
    a.poly([(x+6,y+2),(x+10,y+6),(x+6,y+9),(x+2,y+6)],light)
    a.line(x+6,y+2,x+6,y+9,glow)
   a.rect(13,7,6,14,dark);a.rect(14,9,4,4,glow)
  elif band==3: # Firebird with swept wings and three blazing tail feathers.
   a.poly([(0,0),(8,4),(14,10),(18,7),(24,3),(31,0),(28,13),(21,15),(20,20),(16,24),(12,20),(11,15),(3,14)],1)
   a.poly([(2,3),(9,7),(14,13),(18,10),(26,5),(29,3),(25,11),(19,13),(17,18),(14,18),(12,13),(5,12)],light)
   a.poly([(13,19),(10,23),(15,22),(17,24),(20,22),(19,18)],glow)
   a.rect(14,9,5,3,glow);a.poly([(18,10),(23,12),(18,13)],mid)
  else: # A central eye borne by four ragged wings.
   for side in [0,1]:
    pts=[(0,1),(10,5),(13,11),(8,14),(2,12),(4,8)]
    a.poly([(31-x if side else x,y) for x,y in pts],mid)
    pts=[(0,22),(7,14),(13,12),(12,19),(7,23),(8,18)]
    a.poly([(31-x if side else x,y) for x,y in pts],light)
   a.poly([(11,7),(21,7),(25,13),(21,20),(11,20),(7,13)],1)
   a.poly([(11,10),(21,10),(23,13),(20,17),(12,17),(9,13)],glow);a.rect(15,10,3,8,1)
 elif family=='spider':
  if band==1: # Armored crab with two pincers and six short legs.
   for side in [-1,1]:
    for i in range(3):a.line(16+side*7,17+i*2,16+side*14,20+i*3,light)
   a.poly([(8,11),(24,11),(27,18),(22,24),(10,24),(5,18)],1)
   a.poly([(9,13),(23,13),(24,18),(20,22),(11,22),(8,18)],mid)
   for x in [0,23]:
    a.poly([(x,3),(x+3,1),(x+4,6),(x+7,2),(x+8,10),(x+3,14),(x,10)],light)
   a.line(6,12,10,16,mid);a.line(25,12,22,16,mid);a.rect(11,11,3,3,glow);a.rect(19,11,3,3,glow)
  elif band==2: # Crystal scorpion with an arched segmented tail.
   for x,y in [(17,1),(23,4),(25,9)]:a.poly([(x,y),(x+4,y),(x+5,y+4),(x+1,y+5)],light)
   for side in [-1,1]:
    for i in range(3):a.line(16+side*6,15+i*3,16+side*14,12+i*6,mid)
   a.poly([(10,10),(22,10),(25,16),(21,25),(11,25),(7,17)],1)
   a.poly([(16,11),(22,16),(18,23),(11,20),(10,15)],light)
   a.line(16,12,16,22,glow);a.rect(11,22,3,2,glow);a.rect(18,22,3,2,glow)
  elif band==3: # Broad lava beetle with hooked mandibles.
   for side in [-1,1]:
    for i in range(3):
     a.line(16+side*8,9+i*5,16+side*14,6+i*8,1);a.line(16+side*14,6+i*8,16+side*15,10+i*7,light)
   a.poly([(10,2),(22,2),(26,12),(24,23),(20,26),(11,26),(7,21),(6,12)],1)
   a.poly([(11,4),(21,4),(24,12),(21,22),(12,23),(9,19),(8,12)],dark)
   a.line(16,4,16,22,glow);a.line(10,10,15,14,light);a.line(22,10,17,14,light)
   a.poly([(10,23),(6,27),(13,26),(15,22)],light);a.poly([(18,22),(20,26),(26,27),(22,23)],light)
  else: # Long jointed shadow legs around an exposed eye.
   for side in [-1,1]:
    for i in range(4):
     a.line(16+side*5,10+i*3,16+side*13,1+i*6,light);a.line(16+side*13,1+i*6,16+side*15,8+i*6,mid)
   a.poly([(12,6),(21,7),(25,15),(20,23),(11,22),(7,14)],1)
   a.poly([(12,9),(21,10),(23,14),(19,19),(12,18),(9,14)],glow);a.rect(15,10,3,9,1)
 elif family=='golem':
  if band==1: # Coral branches atop a wide reef body.
   a.poly([(5,17),(11,13),(21,13),(27,17),(31,26),(29,36),(24,36),(25,46),(18,47),(16,36),(14,47),(6,46),(8,36),(2,36),(0,25)],1)
   a.rect(9,15,14,15,mid);a.rect(5,25,22,10,dark);a.rect(10,37,4,9,mid);a.rect(19,37,4,9,mid)
   for x,y in [(5,3),(14,0),(23,4)]:
    a.rect(x,y,3,18-y,light);a.line(x,y+7,x-4,y+3,glow);a.line(x+2,y+9,x+6,y+4,glow)
   a.rect(10,19,4,3,glow);a.rect(19,19,4,3,glow);a.rect(14,28,5,4,glow)
  elif band==2: # Separate faceted blocks orbit a central prism.
   for x,y,w,h in [(10,1,12,13),(4,17,24,18),(1,19,6,15),(25,19,6,15),(8,38,6,9),(19,38,6,9)]:
    a.poly([(x+w//2,y),(x+w,y+3),(x+w-1,y+h),(x,y+h-2),(x,y+3)],1)
    a.poly([(x+w//2,y+2),(x+w-2,y+4),(x+w-2,y+h-2),(x+2,y+h-3),(x+2,y+4)],mid)
    a.line(x+w//2,y+2,x+w//2,y+h-3,light)
   a.rect(12,8,3,2,glow);a.rect(18,8,3,2,glow)
   a.poly([(16,20),(22,26),(16,32),(10,26)],glow)
  elif band==3: # Furnace torso, chimney and square iron fists.
   a.rect(10,0,5,10,1);a.rect(17,3,5,8,1);a.rect(11,1,3,6,light)
   a.poly([(9,9),(24,9),(27,17),(25,23),(29,25),(30,36),(24,37),(25,47),(18,47),(16,38),(13,47),(6,47),(7,37),(1,36),(2,25),(6,22),(5,17)],1)
   a.rect(8,11,16,11,mid);a.rect(10,15,4,3,glow);a.rect(19,15,4,3,glow)
   a.rect(6,24,21,13,dark);a.rect(11,25,12,10,light)
   for x in [13,17,21]:a.rect(x,26,1,8,1)
   a.rect(2,27,5,8,mid);a.rect(26,27,4,8,mid);a.rect(8,39,5,7,mid);a.rect(19,39,5,7,mid)
  else: # Obsidian monolith with four long hanging arms.
   a.poly([(16,0),(24,6),(23,15),(27,21),(23,35),(20,42),(16,47),(12,41),(9,34),(6,21),(10,15),(8,6)],1)
   a.poly([(16,2),(22,7),(20,15),(24,22),(20,34),(16,43),(12,33),(9,22),(12,15),(10,7)],mid)
   for side in [-1,1]:
    for i in range(2):
     a.poly([(16+side*7,17+i*8),(16+side*14,21+i*8),(16+side*15,38+i*5),(16+side*11,34+i*5)],light)
   a.poly([(16,10),(21,16),(16,22),(11,16)],glow);a.rect(15,12,2,8,1)
   a.line(16,25,16,36,glow)
 else: # Region-specific floating spirits.
  if band==1: # Jellyfish bell with five separated tentacles.
   a.poly([(7,2),(23,2),(29,12),(27,18),(4,18),(2,12)],1)
   a.poly([(8,4),(22,4),(26,12),(24,16),(6,16),(5,12)],mid)
   for x in [6,11,16,21,26]:
    a.line(x,17,x-2,25,light);a.line(x-2,25,x+1,34,glow)
   a.rect(11,10,3,3,glow);a.rect(19,10,3,3,glow)
  elif band==2: # Suspended diamond surrounded by small shards.
   a.poly([(16,0),(25,13),(20,29),(16,35),(11,28),(7,13)],1)
   a.poly([(16,3),(22,13),(18,28),(16,31),(13,26),(10,13)],mid)
   a.line(16,4,16,30,glow);a.line(10,13,22,13,light)
   for x,y in [(0,11),(27,6),(2,27),(26,25)]:a.poly([(x+2,y),(x+4,y+4),(x+2,y+7),(x,y+4)],light)
  elif band==3: # Hollow flaming wheel with a central ember.
   a.poly([(16,0),(21,8),(27,4),(26,14),(32,17),(25,23),(28,31),(20,29),(16,36),(10,29),(3,32),(5,23),(0,18),(7,13),(5,4),(12,8)],1)
   a.poly([(16,3),(20,11),(25,8),(23,16),(28,18),(22,22),(25,28),(19,26),(16,32),(12,26),(7,29),(9,21),(4,18),(10,15),(8,8),(13,11)],light)
   a.poly([(14,12),(20,13),(23,19),(18,25),(12,24),(9,18)],0)
   a.rect(14,16,5,6,glow)
  else: # Four-pronged star with a trailing void spiral.
   a.poly([(16,0),(20,10),(31,13),(23,19),(27,30),(17,26),(7,35),(9,23),(0,17),(10,12)],1)
   a.poly([(16,4),(18,13),(27,14),(20,18),(23,26),(16,23),(10,30),(12,21),(4,17),(12,15)],light)
   a.poly([(13,13),(20,13),(23,18),(19,23),(12,22),(9,18)],dark)
   a.rect(13,16,7,4,glow);a.rect(16,16,2,4,1)
 return a

families=['slime','knight','bat','spider','golem','wisp','boss']
for band in range(5):
 for family in families:
  a=copy.deepcopy(sprites[family]) if band==0 else depth_monster(band,family)
  sprites[f'monster_{band}_{family}']=a

# Additional NPC roles share the same scale and have distinct clothing and props.
for role in ['beggar','healer','herbalist']:
 a=copy.deepcopy(sprites['npc'])
 mapping = {'beggar': {31:12,32:13,33:14}, 'healer': {31:4,32:5,33:6}, 'herbalist': {31:26,32:27,33:28}}[role]
 a.p=[[mapping.get(v,v) for v in row] for row in a.p]
 if role=='beggar':
  a.rect(10,25,5,4,3);a.line(10,25,14,28,5)
  a.poly([(21,29),(31,29),(30,34),(23,35)],1);a.rect(22,29,8,2,4);a.rect(24,31,5,2,23)
  a.rect(9,34,3,3,12);a.rect(20,33,3,3,12)
 elif role=='healer':
  a.rect(13,22,7,2,35);a.rect(15,20,3,6,35)
  a.rect(28,12,2,33,13);a.rect(28,12,1,33,24)
  a.poly([(29,6),(32,11),(29,16),(26,11)],40);a.rect(28,9,2,4,42)
 else:
  a.poly([(23,25),(31,25),(31,37),(23,37)],12);a.rect(24,27,7,8,14)
  for x,y in [(23,19),(27,16),(30,21)]:
   a.line(x+1,y+2,27,29,26);a.poly([(x,y),(x+3,y-3),(x+3,y+2),(x,y+3)],28)
  a.rect(24,31,7,1,23);a.rect(24,34,7,1,23)
 sprites['npc_'+role]=a

# Upgrade parchment and the furnace/anvil landmark share the established pixel palette.
a=Art(32,40)
a.rect(6,5,21,29,12);a.rect(7,6,19,27,17);a.rect(10,9,13,23,24)
a.rect(4,4,23,5,14);a.rect(5,4,21,2,18);a.rect(5,31,23,5,14);a.rect(6,31,21,2,18)
a.poly([(16,11),(22,20),(18,20),(18,27),(14,27),(14,20),(10,20)],8)
a.line(16,13,20,19,41);a.rect(12,28,9,1,23)
sprites['scroll']=a
a=Art(40,48)
a.rect(3,2,22,36,2);a.rect(5,3,18,33,3)
for row in range(5):
 for col in range(3):a.rect(5+col*6+(row%2)*2,3+row*5,5,4,4)
a.rect(8,12,14,20,1);a.poly([(9,31),(10,22),(13,26),(15,15),(20,23),(21,31)],46)
a.poly([(11,31),(14,24),(15,28),(18,21),(20,31)],47);a.poly([(14,31),(16,26),(18,31)],48)
a.rect(4,33,24,5,4);a.rect(5,33,22,1,5)
a.rect(13,39,23,7,12);a.rect(16,35,17,6,3)
a.poly([(9,27),(37,27),(33,31),(28,31),(29,36),(18,36),(19,31),(12,31)],4)
a.rect(10,26,26,2,5);a.rect(25,14,3,11,14);a.rect(21,12,11,6,4);a.rect(21,12,11,2,5)
sprites['forge']=a

from item_expansion_art import add_item_art
add_item_art(sprites, Art)

header='''package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Generated by tools/build_pixel_sprites.py. Edit the authored geometry there. */
internal object FineSprites {
    internal class Art(val rows: List<String>) {
        val width = rows.first().length
        val height = rows.size
        val colors = rows.flatMap { it.toList() }.filter { it != '0' }.toSet().size
        val bitmap: ImageBitmap by lazy {
            val pixels = rows.flatMap { row -> row.map { palette[alphabet.indexOf(it)] } }.toIntArray()
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }
    private const val alphabet = "'''+CHARS+'''"
    private val palette = intArrayOf(
'''
# Keep generated JVM methods below the bytecode size limit as the atlas grows.
chunks=[list(sprites.items())[i:i+32] for i in range(0,len(sprites),32)]
text=header+''.join('        0x'+c+'.toInt(),\n' for c in COLORS)+'    )\n'
text+='    val all: Map<String, Art> = buildMap {\n'
for i in range(len(chunks)):text+=f'        putAll(atlas{i}())\n'
text+='    }\n'
for i,chunk in enumerate(chunks):
 text+=f'    private fun atlas{i}(): Map<String, Art> = mapOf(\n'
 for name,a in chunk:
  assert all(len(r)==a.w for r in a.rows())
  text+='        '+json.dumps(name)+' to Art(listOf(\n'+''.join('            '+json.dumps(row)+',\n' for row in a.rows())+'        )),\n'
 text+='    )\n'
text+='}\n'
p=Path('app/src/main/java/com/powerfulhimchan/mapexit/FineSprites.kt')
if '--check' in sys.argv:
 if not p.exists() or ''.join(p.read_text().split()) != ''.join(text.split()):
  raise SystemExit('FineSprites.kt differs from the authored atlas; regenerate it')
else:
 p.write_text(text)
print(f'Authored {len(sprites)} sprites; {sum(a.w*a.h for a in sprites.values())} source pixels')

from battle_motion_art import emit as emit_battle_motion
emit_battle_motion(sprites, COLORS, Art, check='--check' in sys.argv)

from obstacle_art import emit as emit_obstacle_art
emit_obstacle_art(check='--check' in sys.argv)

from preparation_art import emit as emit_preparation_art
emit_preparation_art(check='--check' in sys.argv)
