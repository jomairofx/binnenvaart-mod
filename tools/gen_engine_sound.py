"""Maakt de motorgeluiden van een langzaam draaiende 6-cilinder scheepsdiesel (naadloze loops).

engine_idle.ogg  : stationair, zacht 'dug-dug-dug' met klepgetik
engine_load.ogg  : onder belasting: zwaardere uitlaatstoten, turbo-fluit, schroefwater

Beide loops duren precies 4,0 s en zijn opgebouwd uit hele motorcycli,
zodat ze in Minecraft zonder tik herhaald worden. Het toerental volgt in de mod via de pitch.
Gebruik: python3 gen_engine_sound.py <uitvoermap>
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, lfilter, iirpeak

SR = 44100
LEN = 4.0
N = int(SR * LEN)
RPM = 450.0                 # 2 omwentelingen = 1 cyclus = 0,2667 s -> 15 cycli in 4 s
CYL = 6
CYCLE = 2 * 60.0 / RPM
FIRE = CYCLE / CYL
rng = np.random.default_rng(7)


def circ(fn, x):
    """Filter periodiek toepassen: drie keer achter elkaar, middelste stuk nemen."""
    y = fn(np.concatenate([x, x, x]))
    return y[len(x):2 * len(x)]


def bp(lo, hi, order=2):
    b, a = butter(order, [lo / (SR / 2), hi / (SR / 2)], btype="band")
    return lambda x: lfilter(b, a, x)


def lp(f, order=2):
    b, a = butter(order, f / (SR / 2))
    return lambda x: lfilter(b, a, x)


def hp(f, order=2):
    b, a = butter(order, f / (SR / 2), btype="high")
    return lambda x: lfilter(b, a, x)


def peak(f, q):
    b, a = iirpeak(f / (SR / 2), q)
    return lambda x: lfilter(b, a, x)


t = np.arange(N) / SR
# vaste ongelijkheid per cilinder (echte motoren lopen nooit helemaal gelijk)
cyl_amp = np.array([1.0, 0.86, 0.95, 0.8, 1.05, 0.9])
cyl_jit = np.array([0.0, 0.0012, -0.0008, 0.0015, -0.0005, 0.0009])


def excitation(sharp, noise_amt):
    """Ontstekingspulsen: korte drukstoten, iets ruizig."""
    x = np.zeros(N)
    n_cycles = int(round(LEN / CYCLE))
    plen = int(SR * 0.06)
    tt = np.arange(plen) / SR
    for c in range(n_cycles):
        for k in range(CYL):
            t0 = c * CYCLE + k * FIRE + cyl_jit[k]
            i0 = int(round(t0 * SR)) % N
            a = cyl_amp[k] * (1 + 0.04 * rng.standard_normal())
            env = np.exp(-tt / sharp) * (1 - np.exp(-tt / 0.0012))
            pulse = env * (1 + noise_amt * rng.standard_normal(plen))
            idx = (i0 + np.arange(plen)) % N
            x[idx] += a * pulse
    return x


def clatter(level):
    """Klepgetik: hoge tikjes op de nokkenas, twee per cilinder per cyclus."""
    x = np.zeros(N)
    n_cycles = int(round(LEN / CYCLE))
    plen = int(SR * 0.008)
    tt = np.arange(plen) / SR
    for c in range(n_cycles):
        for k in range(CYL * 2):
            t0 = c * CYCLE + k * FIRE / 2 + 0.011
            i0 = int(round(t0 * SR)) % N
            env = np.exp(-tt / 0.0015)
            idx = (i0 + np.arange(plen)) % N
            x[idx] += level * (0.6 + 0.4 * rng.random()) * env * rng.standard_normal(plen)
    return circ(hp(2500), x)


def body(exc, resonances):
    out = np.zeros(N)
    for f, q, g in resonances:
        out += g * circ(peak(f, q), exc)
    return out


BANDS = [(20, 100), (100, 300), (300, 1000), (1000, 4000), (4000, 12000)]


def balance(x, target):
    """Verdeel de energie over de banden zoals bij opnames van echte scheepsdiesels
    (anders hoor je op laptop-speakers alleen een zacht gebrom)."""
    out = np.zeros(N)
    for (lo, hi), share in zip(BANDS, target):
        band = circ(bp(lo, hi, 3), x)
        e = np.std(band)
        if e > 0:
            out += band / e * np.sqrt(share)
    return out


def finish(x, peak_level=0.9):
    x = x - x.mean()
    x = np.tanh(1.6 * x / np.max(np.abs(x))) / np.tanh(1.6)
    return x / np.max(np.abs(x)) * peak_level


def idle():
    exc = excitation(sharp=0.012, noise_amt=0.6)
    low = circ(lp(160, 4), exc)
    x = 1.0 * low
    x += body(exc, [(48, 3, 1.2), (96, 4, 0.8), (165, 5, 0.45), (310, 6, 0.22), (620, 6, 0.08)])
    # dof rommelen van het blok (laagfrequente ruis, gemoduleerd op het ontstekingsritme)
    rumble = circ(lp(90, 4), rng.standard_normal(N))
    mod = 0.6 + 0.4 * np.cos(2 * np.pi * t / FIRE)
    x += 0.35 * rumble * mod / np.std(rumble) * np.std(low)
    bark = circ(bp(150, 1400), exc * rng.standard_normal(N))
    x += 0.45 * bark / np.std(bark) * np.std(low)
    x += clatter(0.05) * np.std(low) / 0.05 * 0.12
    x = balance(x, [0.42, 0.33, 0.17, 0.065, 0.015])
    return finish(x, 0.85)


def load():
    exc = excitation(sharp=0.02, noise_amt=1.0)
    low = circ(lp(220, 4), exc)
    x = 1.2 * low
    x += body(exc, [(44, 3, 1.4), (88, 3, 1.0), (140, 4, 0.7), (260, 5, 0.45), (520, 5, 0.25), (1100, 5, 0.08)])
    # uitlaatgeblaf: ruisstoot in het middengebied
    bark = circ(bp(250, 1600), exc * rng.standard_normal(N))
    x += 0.5 * bark / np.std(bark) * np.std(low)
    # turbo: zachte fluittoon met lichte zweving (periodiek binnen de loop)
    f_t = 3150 + 25 * np.sin(2 * np.pi * t / LEN)
    phase = 2 * np.pi * np.cumsum(f_t) / SR
    phase *= np.round(phase[-1] / (2 * np.pi)) * 2 * np.pi / phase[-1]
    x += 0.018 * np.sin(phase) * np.std(low) / 0.3
    # schroefwater / kolkend water achter het schip
    wash = circ(bp(120, 900), rng.standard_normal(N))
    x += 0.25 * wash / np.std(wash) * np.std(low)
    x += clatter(0.05) * np.std(low) / 0.05 * 0.08
    x = balance(x, [0.36, 0.32, 0.2, 0.1, 0.02])
    return finish(x, 0.9)


def write(name, x, outdir):
    with tempfile.TemporaryDirectory() as d:
        w = os.path.join(d, name + ".wav")
        wavfile.write(w, SR, (x * 32767).astype(np.int16))
        o = os.path.join(outdir, name + ".ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", w, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", o], check=True)
        print(o, os.path.getsize(o))


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    write("engine_idle", idle(), out)
    write("engine_load", load(), out)
