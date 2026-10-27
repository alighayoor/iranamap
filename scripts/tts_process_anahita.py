#!/usr/bin/env python3
"""
Anahita-Cut Standard v1.0 - استاندارد برش سکوت آناهیتا و آرش
هر قسمت: سکوت تا مرز عدم آسیب به سیگنال اصلی پاک می‌شود.

Usage:
    python scripts/tts_process_anahita.py input.mp3 output.mp3
    python scripts/tts_process_anahita.py --batch voices/arash/input/ voices/arash/output/

ویژگی: برای هر تکه گفتار، trim با پد 5ms + نگاشت وقفه تطبیقی
"""
import sys
import pathlib
import argparse
import librosa
import numpy as np
import soundfile as sf
import subprocess
import os

def process_file(input_path, output_path, verbose=True):
    y, sr = librosa.load(input_path, sr=22050, mono=True)
    intervals = librosa.effects.split(y, top_db=38, frame_length=2048, hop_length=512)
    trimmed = []
    for s,e in intervals:
        seg = y[s:e]
        _, idx = librosa.effects.trim(seg, top_db=32, frame_length=512, hop_length=128)
        pad = int(0.005 * sr)
        ts = max(s, s+idx[0]-pad)
        te = min(e, s+idx[1]+pad)
        trimmed.append((ts,te))
    # گپ‌ها
    gaps = [(trimmed[i+1][0]-trimmed[i][1])/sr*1000 for i in range(len(trimmed)-1)]
    def target(g):
        if g < 250: return 120
        elif g < 450: return 180
        elif g < 650: return 250
        else: return 320
    new_gaps = [target(g) for g in gaps]
    # بازسازی
    out_parts=[]
    for i,(s,e) in enumerate(trimmed):
        out_parts.append(y[s:e])
        if i < len(trimmed)-1:
            out_parts.append(np.zeros(int(new_gaps[i]/1000*sr), dtype=np.float32))
    y_out = np.concatenate(out_parts)
    tmp_wav = "/tmp/anahita_tmp.wav"
    sf.write(tmp_wav, y_out, sr)
    cmd = ["ffmpeg","-y","-loglevel","error","-i",tmp_wav,"-codec:a","libmp3lame","-b:a","128k","-ac","1","-ar","44100", output_path]
    subprocess.run(cmd, check=True)
    os.remove(tmp_wav)
    if verbose:
        print(f"✓ {os.path.basename(input_path)}: {len(y)/sr:.1f}s -> {len(y_out)/sr:.1f}s | pauses {len(gaps)} avg {np.mean(gaps):.0f}ms -> {np.mean(new_gaps):.0f}ms | saved {os.path.getsize(output_path)/1024:.0f}KB")
    return output_path

def main():
    p = argparse.ArgumentParser(description="Anahita-Cut: برش استاندارد سکوت")
    p.add_argument("input", help="فایل ورودی یا پوشه")
    p.add_argument("output", help="فایل/پوشه خروجی")
    p.add_argument("--batch", action="store_true", help="حالت دسته‌ای برای پوشه")
    args = p.parse_args()
    in_p = pathlib.Path(args.input)
    out_p = pathlib.Path(args.output)
    if args.batch or in_p.is_dir():
        out_p.mkdir(parents=True, exist_ok=True)
        for f in in_p.glob("*.mp3"):
            process_file(str(f), str(out_p / f.name))
        print(f"\nتمام شد: {len(list(in_p.glob('*.mp3')))} فایل")
    else:
        out_p.parent.mkdir(parents=True, exist_ok=True)
        process_file(str(in_p), str(out_p))

if __name__ == "__main__":
    main()
