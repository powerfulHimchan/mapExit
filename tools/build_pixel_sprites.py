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
text=header+''.join('        0x'+c+'.toInt(),\n' for c in COLORS)+'    )\n    val all: Map<String, Art> = mapOf(\n'
for name,a in sprites.items():
 assert all(len(r)==a.w for r in a.rows())
 text+='        '+json.dumps(name)+' to Art(listOf(\n'+''.join('            '+json.dumps(row)+',\n' for row in a.rows())+'        )),\n'
text+='    )\n}\n'
p=Path('app/src/main/java/com/powerfulhimchan/mapexit/FineSprites.kt')
if '--check' in sys.argv:
 if not p.exists() or ''.join(p.read_text().split()) != ''.join(text.split()):
  raise SystemExit('FineSprites.kt differs from the authored atlas; regenerate it')
else:
 p.write_text(text)
print(f'Authored {len(sprites)} sprites; {sum(a.w*a.h for a in sprites.values())} source pixels')
