#!/usr/bin/env python3
"""FEXCore パックの PE 構造と識別情報のみを調べる。DLL は実行しない。"""
import hashlib
import os
from pathlib import Path
import struct
import sys
import tarfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def pe_machine(data):
    if len(data) < 64 or data[:2] != b'MZ':
        raise ValueError('MZ ヘッダーがありません')
    off = struct.unpack_from('<I', data, 0x3c)[0]
    if off + 24 > len(data) or data[off:off + 4] != b'PE\0\0':
        raise ValueError('PE ヘッダーが不正です')
    return struct.unpack_from('<H', data, off + 4)[0]


def inspect(filename):
    path = Path(filename)
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(block)
    print(f'パック: {path.name} sha256={digest.hexdigest()} size={path.stat().st_size}')
    # .wcp は xz 圧縮 tar、.tar は zstd を外部で解凍済みの同梱パック。
    with tarfile.open(path, mode='r:*') as tar:
        members = tar.getmembers()
        if len(members) > 50000:
            raise ValueError('エントリ数が多すぎます')
        targets = {'libwow64fex.dll': [], 'libarm64ecfex.dll': [], 'fexofflinecompiler64.exe': []}
        for entry in members:
            name = entry.name.replace('\\', '/').lstrip('./')
            key = name.rsplit('/', 1)[-1].lower()
            if key not in targets:
                continue
            if not entry.isfile() or entry.size < 64 or entry.size > 64 * 1024 * 1024:
                raise ValueError(f'通常ファイルではないかサイズ不正: {name}')
            with tar.extractfile(entry) as stream:
                data = stream.read()
            machine = pe_machine(data)
            targets[key].append(name)
            print(f'  {name}: size={len(data)} PE machine=0x{machine:04x} sha256={sha(data)}')
        for required in ('libwow64fex.dll', 'libarm64ecfex.dll'):
            if len(targets[required]) != 1:
                raise ValueError(f'{required} が1つではありません: {len(targets[required])}')
        print('  オフラインコンパイラー:', 'あり' if targets['fexofflinecompiler64.exe'] else 'なし')
        print('  注意: 静的検査。DLL 実行、キャッシュ読込、ゲーム動作は未検査。')


def main():
    if len(sys.argv) < 2:
        print('使い方: inspect-fex-packs.py <tar または wcp> ...', file=sys.stderr)
        return 2
    try:
        for name in sys.argv[1:]:
            inspect(name)
    except (OSError, ValueError, tarfile.TarError) as e:
        print('検査失敗:', e, file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
