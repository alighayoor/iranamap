#!/usr/bin/env python3
"""
Split Arash base recordings into per-phrase files using Anahita-Cut logic.
Input:  voices/arash/base/arash-batvh-v3-anahita-cut.mp3 (or raw)
Output: voices/arash/base/{phraseId}.mp3  (51 files)
        voices/anahita/base/{phraseId}.mp3 (from 11Labs single file after user generates)

Usage:
  python scripts/split_base_voices.py --input voices/arash/base/arash-batvh-v3.mp3 --voice arash
  python scripts/split_base_voices.py --input /tmp/anahita_base.mp3 --voice anahita
"""
import json, pathlib, argparse, subprocess, os
import librosa, soundfile as sf, numpy as np

parser = argparse.ArgumentParser()
parser.add_argument("--input", required=True)
parser.add_argument("--voice", required=True, choices=["arash","anahita"])
args = parser.parse_args()

base_ids = json.loads(pathlib.Path("docs/tts-lexicon/base_phrases.json").read_text(encoding="utf-8"))
ids = [x["id"] for x in base_ids]
print(f"Base phrases: {len(ids)} - {ids[:5]}")

y, sr = librosa.load(args.input, sr=22050, mono=True)
intervals = librosa.effects.split(y, top_db=38, frame_length=2048, hop_length=512)
print(f"Found {len(intervals)} intervals in {args.input} ({len(y)/sr:.1f}s)")

# Trim each interval
trimmed=[]
for s,e in intervals:
    seg=y[s:e]
    _, idx = librosa.effects.trim(seg, top_db=32, frame_length=512, hop_length=128)
    pad=int(0.005*sr)
    ts=max(s, s+idx[0]-pad); te=min(e, s+idx[1]+pad)
    trimmed.append((ts,te))

if len(trimmed) != len(ids):
    print(f"⚠️ Count mismatch: {len(trimmed)} segments vs {len(ids)} phrases. Will use first N.")
    # Save mapping for manual check
    trimmed = trimmed[:len(ids)]

out_dir = pathlib.Path(f"voices/{args.voice}/base")
out_dir.mkdir(parents=True, exist_ok=True)

for i, (s,e) in enumerate(trimmed):
    pid = ids[i]
    seg = y[s:e]
    wav_tmp = f"/tmp/{pid}.wav"
    sf.write(wav_tmp, seg, sr)
    mp3_out = out_dir / f"{pid}.mp3"
    cmd = ["ffmpeg","-y","-loglevel","error","-i",wav_tmp,"-codec:a","libmp3lame","-b:a","128k","-ac","1","-ar","44100", str(mp3_out)]
    subprocess.run(cmd, check=True)
    os.remove(wav_tmp)
    print(f"  {pid}.mp3  {len(seg)/sr:.2f}s")

print(f"Done -> {out_dir} ({len(list(out_dir.glob('*.mp3')))} files)")
