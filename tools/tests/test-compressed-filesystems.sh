#!/usr/bin/env bash
# ゲームデータを使わない Linux 上の形式検査。Android のマウント可否ではない。
set -euo pipefail
base=$(mktemp -d)
trap 'rm -rf "$base"' EXIT
mkdir "$base/input"
python3 - "$base/input" <<'PY'
import pathlib, random, sys
p = pathlib.Path(sys.argv[1])
(p / 'repeat.bin').write_bytes((b'GameNative synthetic asset\0' * 200000))
r = random.Random(42)
(p / 'noise.bin').write_bytes(r.randbytes(1024 * 1024))
(p / 'empty').write_bytes(b'')
PY
mkfs.erofs -zlz4 "$base/test.erofs" "$base/input" > /dev/null
mksquashfs "$base/input" "$base/test.squashfs" -comp lz4 -processors 2 -noappend -no-progress > /dev/null
mkdir "$base/erofs"
fsck.erofs --extract="$base/erofs" "$base/test.erofs" > /dev/null
unsquashfs -no-progress -d "$base/squashfs" "$base/test.squashfs" > /dev/null
for f in repeat.bin noise.bin empty; do
  cmp "$base/input/$f" "$base/erofs/$f"
  cmp "$base/input/$f" "$base/squashfs/$f"
done
printf '合成データ: %s bytes; EROFS: %s bytes; SquashFS: %s bytes\n' \
  "$(du -sb "$base/input" | cut -f1)" "$(stat -c %s "$base/test.erofs")" "$(stat -c %s "$base/test.squashfs")"
echo '両形式の展開結果は元データと一致。Linux 上のみ・合成データであり、Android 実行や実ゲーム性能は未検証。'
