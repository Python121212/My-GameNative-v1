#!/usr/bin/env python3
"""公式 SDL2 DLL のエクスポートを real DLL に転送し、2関数だけ検査する。"""
import re
import subprocess
import sys
from pathlib import Path

dll, out = map(Path, sys.argv[1:])
text = subprocess.check_output(['x86_64-w64-mingw32-objdump', '-p', str(dll)], text=True)
marker = '[Ordinal/Name Pointer] Table'
if marker not in text:
    raise SystemExit('SDL2 export table not found')
section = text.split(marker, 1)[1].split('\n\n', 1)[0]
hooks = {'SDL_CreateRenderer', 'SDL_RenderPresent', 'SDL_LockTexture', 'SDL_LowerBlit', 'SDL_UnlockTexture', 'SDL_SetRenderTarget', 'SDL_RenderCopy', 'SDL_Quit'}
names = re.findall(r'^\s*\[\s*\d+\]\s+(SDL_[A-Za-z0-9_]+)\s*$', section, re.M)
if len(names) < 500 or len(names) != len(set(names)) or not hooks.issubset(names):
    raise SystemExit(f'Unexpected SDL2 export list: {len(names)}')
lines = ['LIBRARY SDL2', 'EXPORTS']
for name in names:
    lines.append('    ' + (name if name in hooks else f'{name}=SDL2_real.{name}'))
out.write_text('\n'.join(lines)+'\n')
print(f'SDL2 exports: {len(names)} (8 instrumented)')
