#!/usr/bin/env python3
"""固定フレームの一致とデモ完走を検査してから4回の平均を出す。"""
import csv
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
for backend, variant in (('dxvk','control'), ('dxvk','profile'), ('wined3d','control'), ('wined3d','profile')):
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
for backend in ('dxvk','wined3d'):
    values = {}
    for i in range(1,5):
        data = list(csv.DictReader((out / f'{backend}-profile-{i}.csv').open()))
        byname = {x['name']: x for x in data}
        if set(byname) != {'lock','blit','unlock','target','copy','present'}:
            raise SystemExit('プロファイルに必要な API がありません')
        frames = int(byname['present']['calls'])
        if frames < 1000: raise SystemExit(f'{backend}: 計測対象フレームが不足: {frames}')
        print(f'{backend} {i}: sampled_frames={frames}, logical_bytes={byname["lock"]["logical_bytes"]}')
        for name, row in byname.items():
            count=int(row['calls']); freq=int(row['frequency']); ticks=int(row['ticks'])
            if freq <= 0 or (name in ('lock','blit','unlock','copy','present') and count < 1000):
                raise SystemExit(f'{backend} {name}: 呼び出し数またはタイマーが不正: {count}')
            per_frame=1000*ticks/freq/frames
            values.setdefault(name,[]).append(per_frame)
    for name, arr in values.items():
        print(f'{backend} {name}: CPU ms/対象フレーム {list(map(lambda x: round(x,4),arr))} 平均={statistics.mean(arr):.4f}')
print('SDL 呼び出し内の CPU 壁時計時間。GPU 実行時間・DMA 量・実転送帯域ではない。')
print('control/profile は同じ検査用 DLL、計時オフ／オンの違い。固定フレーム120の読み戻しを含む。x86_64 Linux ソフトウェア描画であり Android 実機ではない。')
