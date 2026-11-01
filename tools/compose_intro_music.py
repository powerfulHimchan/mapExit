#!/usr/bin/env python3
"""Original 10-second intro sketches. No recordings, soundfonts or borrowed melodies.

Requires numpy, scipy and ffmpeg. Usage: python tools/compose_intro_music.py OUTPUT
WAV masters are 44.1 kHz / stereo / 16 bit; MP3 previews use 192 kbps.
All sketches share D minor, 96 BPM, four bars, but distinct orchestration.
"""
import json
import subprocess
import sys
from pathlib import Path

import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
DURATION = 10.0
BEAT = 60 / 96
TAIL = 4.0
N = int((DURATION + TAIL) * SR)
RNG = np.random.default_rng(3505)


def frequency(note):
    return 440 * 2 ** ((note - 69) / 12)


def lowpass(x, hz):
    return signal.sosfilt(signal.butter(2, hz, fs=SR, output="sos"), x)


def envelope(t, duration, attack, release):
    return (1 - np.exp(-t / attack)) * np.minimum(1, np.maximum(0, (duration - t) / release)) ** 1.4


def voice(note, duration, kind):
    t = np.arange(int(duration * SR)) / SR
    f = frequency(note)
    if kind in ("strings", "choir"):
        y = np.zeros(len(t))
        for detune, phase in [(-0.0028, 0.8), (0, 0), (0.0026, 1.9)]:
            for k in range(1, 9):
                weight = np.exp(-k / (4 if kind == "strings" else 2.5)) / k
                y += weight * np.sin(2 * np.pi * f * k * (1 + detune) * t + phase)
        y = lowpass(y / 3, 1800 if kind == "strings" else 1200)
        return y * envelope(t, duration, 0.30, 0.60) * (0.93 + 0.07 * np.sin(2 * np.pi * 0.8 * t))
    if kind == "bell":
        y = sum(a * np.sin(2 * np.pi * f * ratio * t) * np.exp(-t / decay)
                for ratio, a, decay in [(1, 1, 1.4), (2.003, .30, .85), (2.756, .13, .45), (4.08, .06, .25)])
        return y * (1 - np.exp(-t / .003)) * envelope(t, duration, .002, .25)
    if kind == "piano":
        y = np.zeros(len(t))
        for k in range(1, 10):
            decay = 2.2 / (1 + k * .38)
            y += (1 / k ** 1.9) * np.sin(2 * np.pi * f * k * np.sqrt(1 + .00012 * k * k) * t) * np.exp(-t / decay)
        hammer = lowpass(RNG.normal(0, 1, len(t)), 1300) * np.exp(-t / .012) * .04
        return (y + hammer) * (1 - np.exp(-t / .004)) * envelope(t, duration, .003, .35)
    if kind == "pluck":
        y = sum(np.sin(2 * np.pi * f * k * t) / k ** 1.8 * np.exp(-t * (1 + k * .6)) for k in range(1, 8))
        return y * envelope(t, duration, .003, .20)
    if kind == "chip":
        # Band-limited pulse: no raw discontinuities or aliasing at high notes.
        y = sum(np.sin(2 * np.pi * f * k * t) / k ** 1.3 for k in (1, 3, 5, 7) if f*k < SR*.4)
        return y * np.exp(-t * 3.2) * envelope(t, duration, .005, .06)
    if kind == "bass":
        y = np.sin(2 * np.pi * f * t) + .14 * np.sin(2 * np.pi * f * 2 * t)
        return y * envelope(t, duration, .045, .25)
    raise ValueError(kind)


def add(mix, note, beat, length, kind, level=.2, pan=0):
    x = voice(note, length, kind) * level
    start = round(beat * BEAT * SR)
    end = min(start + len(x), N)
    gains = np.sqrt([(1 - pan) / 2, (1 + pan) / 2])
    mix[start:end] += x[:end-start, None] * gains


def drum(mix, beat, level=.3, deep=True):
    t = np.arange(int(1.3 * SR)) / SR
    phase = 2 * np.pi * (48 * t + 48 * .055 * (1 - np.exp(-t / .055)))
    x = np.sin(phase) * np.exp(-t * 5)
    noise = lowpass(RNG.normal(0, 1, len(t)), 850 if deep else 2300)
    x += noise * np.exp(-t * 22) * .20
    x *= (1 - np.exp(-t / .002)) * level
    start = round(beat * BEAT * SR)
    mix[start:start+len(x)] += x[:, None] * np.array([.72, .69])


def atmosphere(mix, level=.01):
    t = np.arange(N) / SR
    wind = lowpass(RNG.normal(0, 1, N), 620)
    wind = signal.sosfilt(signal.butter(2, 190, fs=SR, btype="highpass", output="sos"), wind)
    wind *= (.6 + .4 * np.sin(2 * np.pi * t / 10) ** 2) * level
    mix[:, 0] += wind
    mix[:, 1] += np.roll(wind, 170)


def finish(mix, wet, out, stem):
    reverb = np.zeros_like(mix)
    damped = np.stack([lowpass(mix[:, c], 3600) for c in range(2)], axis=1)
    for seconds, gain in [(0.071,.28),(.113,.23),(.179,.20),(.283,.17),(.419,.14),(.631,.11),(.887,.085),(1.23,.06),(1.73,.04),(2.31,.025),(3.13,.015)]:
        d = int(seconds * SR)
        reverb[d:] += damped[:-d, ::-1] * gain
    y = mix * (1 - wet*.25) + reverb * wet
    count = int(DURATION * SR)
    # Fold the rendered decay back into the head, preserving the four-bar loop.
    y[:N-count] += y[count:]
    y = y[:count]
    y -= y.mean(axis=0)
    # Match perceptual levels approximately, with ample true-peak headroom.
    rms = np.sqrt(np.mean(y*y))
    y *= min(.115 / max(rms, 1e-8), .76 / max(np.max(np.abs(y)), 1e-8))
    ramp = np.linspace(0, 1, int(.012 * SR))
    y[:len(ramp)] *= ramp[:, None]
    y[-len(ramp):] *= ramp[::-1, None]
    pcm = np.rint(y * 32767).astype(np.int16)
    wav = out / (stem + '.wav')
    mp3 = out / (stem + '.mp3')
    wavfile.write(wav, SR, pcm)
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(wav),'-codec:a','libmp3lame','-b:a','192k','-metadata','title='+stem,str(mp3)],check=True)
    return {'file':mp3.name,'duration_seconds':len(y)/SR,'peak_dbfs':round(20*np.log10(np.max(np.abs(y))),2),'rms_dbfs':round(20*np.log10(np.sqrt(np.mean(y*y))),2),'stereo':True}


def main():
    out = Path(sys.argv[1]).resolve()
    out.mkdir(parents=True, exist_ok=True)
    names = ['01_문너머의_심연','02_황금빛_틈새','03_한층더_레트로','04_깊은곳의_맥박','05_돌아갈수있는_길']
    descriptions = [
        '어두운 현악 패드와 낮은 저음, 문 너머를 향하는 짧은 선율',
        '맑은 종소리와 합창풍 패드, 금빛 문틈의 신비로운 분위기',
        '절제된 8비트 선율과 베이스, 도트 던전에 맞춘 레트로 분위기',
        '낮은 북과 뜯는 현의 선율, 심연의 긴장감',
        '부드러운 피아노풍 음색과 공기감, 조용히 다시 도전하는 분위기',
    ]
    # Four bars: D minor(add9), B-flat major7, G minor9, A suspended -> A major.
    chords = [(38,50,57,65,76),(34,53,57,62,69),(43,50,58,65,69),(33,52,57,62,67)]
    motif = [(0,74,1.2),(2,77,.8),(3,76,.8),(4,74,1.5),(6.5,69,.8),(8,70,1.3),(10,74,.9),(11.5,72,.7),(12.5,69,.8),(14,73,1.2)]
    results = []
    for idx, name in enumerate(names):
        mix = np.zeros((N,2), dtype=np.float64)
        atmosphere(mix, .025 if idx in (0,3) else .012)
        if idx == 0:
            for bar,chord in enumerate(chords):
                for j,note in enumerate(chord[:4]):
                    add(mix,note,bar*4,3.7,'strings',.12 if j else .22,(j-1.5)*.30)
            for b,n,l in motif:
                add(mix,n-12,b,l+1,'pluck',.20,-.12)
            for b in (0,8): drum(mix,b,.14)
        elif idx == 1:
            for bar,chord in enumerate(chords):
                for j,note in enumerate(chord[1:]): add(mix,note,bar*4,3.9,'choir',.095,(j-1.5)*.36)
                add(mix,chord[0],bar*4,3.1,'bass',.18)
            for b,n,l in motif:
                add(mix,n+12,b,2.8,'bell',.14,(-.32 if int(b)%2 else .32))
                if b < 13: add(mix,n+12,b+.75,1.8,'bell',.035,-.4)
        elif idx == 2:
            for bar,chord in enumerate(chords):
                for step in range(8):
                    add(mix,chord[1+step%3],bar*4+step*.5,.26,'chip',.070,(-.30 if step%2 else .30))
                for step in (0,1.5,2,3): add(mix,chord[0]+12,bar*4+step,.36,'bass',.26)
                drum(mix,bar*4,.18,False)
                drum(mix,bar*4+2,.09,False)
            for b,n,l in motif: add(mix,n,b,l*.7,'chip',.21)
        elif idx == 3:
            for bar,chord in enumerate(chords):
                add(mix,chord[0],bar*4,3.2,'bass',.26)
                for j,note in enumerate(chord[1:4]): add(mix,note,bar*4,3.6,'strings',.07,(j-1)*.4)
                for b,level in [(0,.35),(1.5,.14),(2.75,.25)]: drum(mix,bar*4+b,level)
            for b,n,l in motif: add(mix,n-12,b,l+1,'pluck',.27,.13)
            for b,n in [(3.5,81),(7.5,77),(11.5,74),(15,73)]: add(mix,n,b,1.7,'bell',.055,-.3)
        else:
            for bar,chord in enumerate(chords):
                add(mix,chord[0]+12,bar*4,3.2,'piano',.23,-.15)
                for j,note in enumerate(chord[1:]): add(mix,note+12,bar*4+j*.58,2.6,'piano',.10,(j-1.5)*.25)
                for j,note in enumerate(chord[1:3]): add(mix,note,bar*4,3.8,'choir',.055,(j-.5)*.6)
            for b,n,l in motif: add(mix,n,b+.12,l+1.2,'piano',.24,.13)
        record=finish(mix,[.65,.9,.23,.6,.78][idx],out,name)
        record['description']=descriptions[idx]
        results.append(record)
    (out/'audio_checks.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(results,ensure_ascii=False,indent=2))


if __name__ == '__main__':
    main()
