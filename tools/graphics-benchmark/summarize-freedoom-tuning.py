#!/usr/bin/env python3
"""固定フレームの一致とデモ完走を検査してから4回の平均を出す。"""
import hashlib
import re
import statistics
import struct
import sys
from pathlib import Path
from PIL import Image

out = Path(sys.argv[1])
ref = None
reference_tics = None
rows = {}
for backend, variant in (('dxvk','baseline'), ('dxvk','candidate'), ('wined3d','baseline'), ('wined3d','candidate')):
    name = f'{backend}-{variant}'
    rows[name] = []
    for i in range(1, 5):
        raw = (out / f'{name}-{i}.raw').read_bytes()
        if len(raw) < 8:
            raise SystemExit(f'{backend} {i}: フレームが不正')
        w, h = struct.unpack('<II', raw[:8]); pixels = raw[8:]
        if not (1 <= w <= 4096 and 1 <= h <= 4096 and len(pixels) == w*h*4):
            raise SystemExit(f'{backend} {i}: サイズが不正')
        image = Image.frombytes('RGBA', (w,h), pixels, 'raw', 'BGRA')
        image.save(out / f'{name}-{i}.png')
        rgb = image.convert('RGB').tobytes()
        if len(set(zip(rgb[0::3], rgb[1::3], rgb[2::3]))) < 32:
            raise SystemExit(f'{backend} {i}: 画面が単調すぎる')
        if ref is None:
            ref = (w,h,rgb)
        if (w,h) != ref[:2]:
            raise SystemExit(f'{backend} {i}: 画面サイズが一致しません')
        # ドライバーの丸め誤差だけを許容。固定シーンの大きな相違は失敗。
        diffs = [abs(a-b) for a,b in zip(rgb,ref[2])]
        mean = sum(diffs)/len(diffs)
        large = sum(d > 8 for d in diffs)/len(diffs)
        if mean > 1.0 or large > 0.005:
            raise SystemExit(f'{backend} {i}: 固定フレーム不一致 mean={mean:.4f} large={large:.4%}')
        log=(out / f'{name}-{i}.txt').read_text(errors='replace')
        m=re.search(r'timed (\d+) gametics in (\d+) realtics \(([\d.]+) fps\)',log)
        if not m or int(m[2]) <= 0:
            raise SystemExit(f'{backend} {i}: デモ結果なし')
        tics=int(m[1]); fps=float(m[3])
        if reference_tics is None: reference_tics=tics
        if tics != reference_tics: raise SystemExit('デモ長が異なるため比較不可')
        rows[name].append(fps)
        print(f'{name} {i}: FPS={fps:.3f} gametics={tics} realtics={m[2]} 固定フレーム差平均={mean:.4f} 大差比率={large:.4%} sha256={hashlib.sha256(rgb).hexdigest()}')
for backend, values in rows.items():
    print(f'{backend}: FPS={values} 平均={statistics.mean(values):.3f} 範囲={min(values):.3f}–{max(values):.3f}')
print('baseline は既定設定。candidate は DXVK maxFrameLatency=1、WineD3D csmt=0。固定フレーム120を比較。SDL2 診断プロキシの読み戻しを含む。x86_64 Linux ソフトウェア描画であり Android 実機ではない。')
