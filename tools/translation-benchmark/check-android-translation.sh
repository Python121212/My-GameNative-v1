#!/usr/bin/env bash
# 端末の状態を読み取るだけ。インストール、停止、データ消去は行わない。
set -euo pipefail
if [[ $# != 1 || -z $1 || $1 == -* ]]; then
    echo '使い方: check-android-translation.sh <adb シリアル>' >&2
    exit 2
fi
command -v adb >/dev/null || { echo 'adb が見つかりません。' >&2; exit 1; }
serial=$1
read_device() { adb -s "$serial" "$@" | tr -d '\r'; }
state=$(read_device get-state)
[[ $state == device ]] || { echo '指定端末が使用可能ではありません。' >&2; exit 1; }
prop() { read_device shell getprop "$1"; }
abi=$(prop ro.product.cpu.abilist)
bridge=$(prop ro.dalvik.vm.native.bridge)
qemu=$(prop ro.kernel.qemu)
kernel=$(read_device shell uname -m)
release=$(prop ro.build.version.release)
printf 'Android: %s\n対応 ABI: %s\nカーネルのアーキテクチャ: %s\nNative Bridge: %s\nエミュレーター属性: %s\n' \
    "$release" "$abi" "$kernel" "${bridge:-未設定}" "${qemu:-未設定}"
if [[ $kernel == x86_64 || $kernel == i?86 ]]; then
    echo '判定: x86 系カーネル。ARM ライブラリの実行には追加の変換が必要です。'
    echo 'この環境の性能を ARM 実機の性能として扱わないでください。'
elif [[ $kernel == aarch64 && ,$abi, == *,arm64-v8a,* && ( -z $bridge || $bridge == 0 ) && $qemu != 1 ]]; then
    echo '判定: ARM64 ネイティブ候補。プロパティだけでは実際の実行経路を証明できません。'
    echo '機器の実体と、対象プロセス・ライブラリの読み込みを別途確認してください。'
else
    echo '判定: 不明。追加の変換がないとは確認できません。'
fi
printf '%s\n' '状態の読み取りのみを行いました。APK・ゲームデータ・アプリの設定は変更していません。'
