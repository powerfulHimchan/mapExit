from pathlib import Path
import numpy as np
from scipy.signal import butter, sosfilt
from scipy.io import wavfile
import subprocess, json, zipfile, base64

ROOT=Path(__file__).parent
OUT=ROOT.parent/'app/src/main/res/raw'
SR=32000
LEN=24
RNG=np.random.default_rng(117)

def freq(m): return 440*2**((m-69)/12)
def env(t,d,a=.012,r=.1):
    return np.minimum(t/max(a,.001),1)*np.minimum(np.maximum(d-t,0)/max(r,.001),1)

def voice(m,d,kind):
    t=np.arange(int(d*SR))/SR
    f=freq(m)
    ph=2*np.pi*f*t
    if kind=='lead':
        p=ph+.018*np.sin(2*np.pi*5*t)
        x=sum(np.sin(h*p)*(.49**(h-1)) for h in range(1,7))
        x*=env(t,d,.014,.10)*(.72+.28*np.exp(-t*5))
    elif kind=='pluck':
        x=sum(np.sin(ph*h)*np.exp(-t*(2+h*1.2))*.53**(h-1) for h in range(1,6))
        x*=env(t,d,.005,.1)
    elif kind=='bell':
        x=(np.sin(ph)*np.exp(-t*1.8)+.28*np.sin(ph*2.006)*np.exp(-t*3.1)+.12*np.sin(ph*3.99)*np.exp(-t*5))
        x*=env(t,d,.005,.2)
    elif kind=='pad':
        x=(np.sin(ph)+.38*np.sin(ph*1.0017)+.24*np.sin(ph*.9985)+.22*np.sin(ph*2)+.075*np.sin(ph*3))
        x*=env(t,d,.48,.75)*(.94+.06*np.sin(2*np.pi*.3*t))
    elif kind=='strings':
        x=sum(np.sin(ph*h+.005*h*np.sin(t*29))*.42**(h-1) for h in range(1,7))
        x*=env(t,d,.16,.28)
    elif kind=='bass':
        x=(np.sin(ph)+.2*np.sin(ph*2)+.09*np.sin(ph*3))*env(t,d,.014,.13)*(.7+.3*np.exp(-t*8))
    elif kind=='kick':
        x=np.sin(2*np.pi*(46*t+4.8*(1-np.exp(-t*27))))*np.exp(-t*12)*env(t,d,.003,.08)
    elif kind=='tom':
        x=(np.sin(2*np.pi*(82*t+3*(1-np.exp(-t*14))))+.16*RNG.normal(size=len(t)))*np.exp(-t*9)*env(t,d,.004,.08)
    elif kind=='snare':
        n=RNG.normal(size=len(t)); n=sosfilt(butter(2,1100,fs=SR,btype='high',output='sos'),n)
        x=(.43*n+.24*np.sin(2*np.pi*180*t))*np.exp(-t*19)*env(t,d,.002,.04)
    else:
        n=RNG.normal(size=len(t)); n=sosfilt(butter(2,6500,fs=SR,btype='high',output='sos'),n)
        x=.35*n*np.exp(-t*44)*env(t,d,.002,.03)
    return x

THEMES=[
 dict(id='01_floors_01_10',title='첫 번째 계단',scene='1–10층',bpm=92,root=57,scale=[0,2,3,5,7,8,10],prog=[(0,[0,3,7,14]),(5,[0,3,7,10]),(8,[0,4,7,11]),(7,[0,4,7,10])],motif=[(0,4,1),(1.5,2,.5),(2,1,1),(3,0,.7)],mood='조심스러운 탐험',busy=0),
 dict(id='02_floors_11_20',title='물든 회랑',scene='11–20층',bpm=100,root=50,scale=[0,2,3,5,7,9,10],prog=[(0,[0,3,7,14]),(5,[0,4,7,14]),(3,[0,4,7,11]),(10,[0,4,7,14])],motif=[(0,2,.6),(.75,4,.5),(1.5,5,.7),(2.5,4,.5),(3.25,1,.5)],mood='신비로운 울림',busy=1),
 dict(id='03_floors_21_30',title='식지 않는 심장',scene='21–30층',bpm=112,root=52,scale=[0,2,3,5,7,8,10],prog=[(0,[0,3,7]),(8,[0,4,7]),(5,[0,3,7]),(7,[0,4,7])],motif=[(0,0,.5),(.75,2,.5),(1.5,4,.7),(2.5,3,.5),(3.25,2,.5)],mood='무거운 박동',busy=2),
 dict(id='04_floors_31_40',title='봉인된 기억',scene='31–40층',bpm=84,root=54,scale=[0,1,3,5,7,8,10],prog=[(0,[0,3,7,14]),(1,[0,4,7,11]),(8,[0,4,7,14]),(5,[0,3,7,10])],motif=[(0,4,1.2),(1.75,3,.7),(2.75,1,.6),(3.5,0,.4)],mood='차갑고 불길한 여운',busy=1),
 dict(id='05_floors_41_50',title='심연의 문턱',scene='41–50층',bpm=108,root=48,scale=[0,2,3,5,7,8,11],prog=[(0,[0,3,7]),(8,[0,4,7]),(5,[0,3,7]),(7,[0,4,7])],motif=[(0,4,.8),(1,3,.5),(1.75,2,.5),(2.5,1,.7),(3.5,0,.4)],mood='최심부의 압박감',busy=3),
 dict(id='06_battle',title='맞선 칼끝',scene='전투',bpm=144,root=50,scale=[0,2,3,5,7,8,11],prog=[(0,[0,3,7]),(8,[0,4,7]),(5,[0,3,7]),(7,[0,4,7])],motif=[(0,0,.4),(.5,2,.4),(1,4,.65),(2,3,.4),(2.5,2,.4),(3,1,.6)],mood='빠른 공방',busy=4),
 dict(id='07_npc',title='낯선 이의 등불',scene='NPC 조우',bpm=88,root=55,scale=[0,2,4,6,7,9,11],prog=[(0,[0,4,7,14]),(7,[0,4,7,14]),(9,[0,3,7,14]),(5,[0,4,7,11])],motif=[(0,2,.8),(1,4,.7),(2,5,.8),(3,4,.75)],mood='잠깐의 안도와 호기심',busy=0),
 dict(id='08_obstacle',title='한 걸음의 틈',scene='장애물',bpm=132,root=52,scale=[0,1,3,5,7,8,10],prog=[(0,[0,3,7]),(1,[0,4,7]),(0,[0,3,7]),(7,[0,3,6])],motif=[(0,0,.35),(.75,1,.35),(1.5,4,.35),(2.25,3,.35),(3,1,.5)],mood='규칙적인 긴박감',busy=3),
]

def render(th,style):
    mix=np.zeros((int((LEN+4)*SR),2),np.float64)
    beat=60/th['bpm']; root=th['root']; busy=th['busy']; scale=th['scale']
    def add(m,start,d,kind,amp,pan=0):
        if start>=LEN: return
        x=voice(m,d,kind)*amp
        j=max(0,int(start*SR)); k=min(len(mix),j+len(x)); x=x[:k-j]
        mix[j:k,0]+=x*np.sqrt((1-pan)/2)
        mix[j:k,1]+=x*np.sqrt((1+pan)/2)
    def degree(n): return scale[n%7]+12*(n//7)
    bars=int(np.ceil(LEN/(beat*4)))
    for bar in range(bars):
        at=bar*4*beat
        offset,intervals=th['prog'][bar%4]
        chord=root+offset
        for n,iv in enumerate(intervals):
            add(chord+iv,at,4.5*beat,'pad',.047 if style=='A' else .075,(n-1.5)*.35)
        bassbeats=[0,2] if busy<2 else [0,1.5,2,3]
        for q in bassbeats:
            add(chord-12,at+q*beat,beat*(1.5 if busy<2 else .66),'bass',.16 if style=='A' else .12,-.03)
        # Distinct motif per area, with a response in alternate bars.
        for pos,deg,dur in th['motif']:
            if style=='B' and bar%2==1 and pos<1: continue
            note=root+12+degree(deg + (1 if bar%4==2 else 0))
            if bar%4==3 and pos>2: note=root+12+degree(max(0,deg-1))
            kind=('pluck' if th['id'].startswith('07') else 'lead') if style=='A' else 'bell'
            add(note,at+pos*beat,(dur+.18)*beat,kind,.095 if style=='A' else .064,.10 if style=='A' else -.24)
            if style=='A' and busy>=3 and bar>=2:
                add(note-12,at+pos*beat,dur*beat,'strings',.027,-.2)
        # Broken chords retain an unobtrusive pulse during exploration.
        steps=8 if style=='A' else (8 if busy>=3 else 4)
        for step in range(steps):
            iv=intervals[[0,2,1,2,0,2,3 if len(intervals)>3 else 1,2][step%8]]
            add(chord+12+iv,at+step*4*beat/steps,.5*beat+0.2,'pluck' if style=='A' else 'bell',.030 if style=='A' else .022,(-.42 if step%2 else .42))
        # Low drums grow denser with depth; NPC has no combat percussion.
        if not th['id'].startswith('07'):
            for q in ([0,2] if busy<3 else [0,1.5,2,3]):
                add(36,at+q*beat,.48,'kick' if style=='A' else 'tom',.16 if busy>=2 else .09)
            if style=='A' and busy>=1:
                for q in [1,3]: add(36,at+q*beat,.24,'snare',.10 if busy>=3 else .045,.12)
            if busy>=2:
                for q in np.arange(0,4,.5): add(36,at+q*beat,.10,'hat',.10 if style=='A' else .035,(-.3 if int(q*2)%2 else .3))
            if style=='B' and busy>=3:
                for q in [1.5,3.5]: add(root+7,at+q*beat,.46,'strings',.047,-.16)
    count=int(LEN*SR)
    dry=mix[:count].copy()
    tail=mix[count:];dry[:len(tail)]+=tail
    mix=dry.copy()
    for delay,gain in [(0.113,.12),(0.227,.16),(0.367,.12),(0.521,.09),(0.733,.055)]:
        mix+=np.roll(dry,int(delay*SR),axis=0)[:,::-1]*gain*1.35
    mix*=.82/max(float(np.max(np.abs(mix))),.01)
    wav=ROOT/('bgm_'+th['id']+'.wav')
    wavfile.write(wav,SR,(mix*32767).astype(np.int16))
    mp3=OUT/('bgm_'+th['id']+'.ogg')
    subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-af','loudnorm=I=-20:TP=-2:LRA=7','-ar','44100','-c:a','libvorbis','-q:a','5',str(mp3)],check=True)
    wav.unlink()
    return mp3


if __name__=='__main__':
    OUT.mkdir(parents=True,exist_ok=True)
    for theme in THEMES:
        LEN=16*4*60/theme['bpm']
        print(render(theme,'B'))
