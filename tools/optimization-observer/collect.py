#!/usr/bin/env python3
"""Android の負荷を読み取り専用で記録する。FPS は算出しない。"""
import argparse
import datetime
import json
import math
import os
from pathlib import Path
import re
import subprocess
import sys
import time

PACKAGE = re.compile(r'^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$')


def invoke(serial, *args):
    start = time.monotonic()
    try:
        result = subprocess.run(['adb', '-s', serial, *args], capture_output=True,
                                text=True, errors='replace', timeout=15, check=False)
        return {'returncode': result.returncode, 'stdout': result.stdout,
                'stderr': result.stderr, 'elapsed_seconds': round(time.monotonic() - start, 3)}
    except (OSError, subprocess.TimeoutExpired) as exc:
        return {'returncode': None, 'stdout': '', 'stderr': str(exc).replace(serial, '[serial]'),
                'elapsed_seconds': round(time.monotonic() - start, 3)}


def live_pids(result):
    text = result['stdout'].strip()
    if result['returncode'] != 0 or not re.fullmatch(r'\d+(?:\s+\d+)*', text):
        return []
    return sorted(int(x) for x in text.split())


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial', required=True, help='対象 adb シリアル。記録には明示的に保存しない')
    p.add_argument('--output', required=True, help='新しく作成する保存先。既存のパスは上書きしない')
    p.add_argument('--package', default='app.gamenative')
    p.add_argument('--samples', type=int, default=4)
    p.add_argument('--interval', type=float, default=10, help='サンプル開始間隔の最小値（秒）')
    args = p.parse_args(argv)
    if (not args.serial or args.serial.startswith('-') or not PACKAGE.fullmatch(args.package)
            or not 1 <= args.samples <= 120 or not math.isfinite(args.interval)
            or not 1 <= args.interval <= 300):
        p.error('シリアル、パッケージ、回数 (1〜120)、間隔 (1〜300秒) を確認してください')
    state = invoke(args.serial, 'get-state')
    if state['returncode'] != 0 or state['stdout'].strip() != 'device':
        print('指定端末が使用できません。adb と接続状態を確認してください。', file=sys.stderr)
        return 1
    initial = invoke(args.serial, 'shell', 'pidof', args.package)
    if not live_pids(initial):
        print('対象プロセスを確認できません。アプリを手動で起動してください。', file=sys.stderr)
        return 1
    output = Path(args.output)
    try:
        output.mkdir(mode=0o700, parents=False, exist_ok=False)
    except OSError as exc:
        print(f'保存先を新規作成できません: {exc}', file=sys.stderr)
        return 1
    print('読み取りのみを開始します。プロセスの停止・アプリの起動・設定変更は行いません。', flush=True)
    checks = {
        'pid': ('pidof', args.package),
        'cpuinfo': ('dumpsys', 'cpuinfo'),
        'meminfo': ('dumpsys', 'meminfo', args.package),
        'thermalservice': ('dumpsys', 'thermalservice'),
        'battery': ('dumpsys', 'battery'),
        'gfxinfo': ('dumpsys', 'gfxinfo', args.package),
    }
    report = {'version': 1, 'package': args.package, 'initial_pids': live_pids(initial),
              'interval_min_seconds': args.interval, 'requested_samples': args.samples,
              'note': 'gfxinfo はアプリ UI の累積情報であり、ゲーム FPS ではない。', 'samples': []}
    report_path = output / 'observations.json'
    incomplete = False
    for i in range(args.samples):
        started = time.monotonic()
        sample = {'index': i + 1, 'utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                  'elapsed_since_start_seconds': round(started - begin, 3) if i else 0,
                  'results': {}}
        if i == 0:
            begin = started
        for name, command in checks.items():
            result = invoke(args.serial, 'shell', *command)
            if name == 'pid':
                sample['pids'] = live_pids(result)
            # adb のエラーにもシリアルが含まれ得るため、ログから除去する。
            for key in ('stdout', 'stderr'):
                result[key] = result[key].replace(args.serial, '[serial]')
            sample['results'][name] = result
        end_pid = invoke(args.serial, 'shell', 'pidof', args.package)
        sample['end_pids'] = live_pids(end_pid)
        for key in ('stdout', 'stderr'):
            end_pid[key] = end_pid[key].replace(args.serial, '[serial]')
        sample['results']['pid_end'] = end_pid
        report['samples'].append(sample)
        if sample['pids'] != report['initial_pids'] or sample['end_pids'] != report['initial_pids']:
            incomplete = True
            sample['warning'] = '対象 PID の消失・変更を検出。連続した測定として扱わない。'
        if any(value['returncode'] != 0 for value in sample['results'].values()):
            incomplete = True
        report['complete'] = not incomplete and i + 1 == args.samples
        # データは個人情報を含み得るため、他ユーザーから読めないようにする。
        fd = os.open(report_path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, 'w', encoding='utf-8') as f:
            json.dump(report, f, ensure_ascii=False, indent=2)
        print(f'サンプル {i + 1}/{args.samples} を保存しました。', flush=True)
        if sample['pids'] != report['initial_pids'] or sample['end_pids'] != report['initial_pids']:
            print('対象プロセスが消失または変更されたため中断します。', file=sys.stderr)
            break
        if i + 1 < args.samples:
            time.sleep(max(0, args.interval - (time.monotonic() - started)))
    print(f'結果: {report_path}。共有前に内容を確認してください。')
    if incomplete:
        print('取得失敗またはプロセス変更があります。結果の詳細を確認してください。', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
