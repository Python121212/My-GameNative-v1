#!/usr/bin/env bash
# 最適化の前提確認。明示指定なしではプロセスを停止しない。
set -euo pipefail
cd "$(dirname "$0")/.."
usage() {
    cat <<'USAGE'
使い方:
  ./tools/prepare-optimization.sh
  ./tools/prepare-optimization.sh --stop-gradle
  ./tools/prepare-optimization.sh --reset-app <adb シリアル>

引数なしは状態確認のみ。--stop-gradle は現在のユーザーの Gradle デーモン
（他のプロジェクトで使用中のものも含む）を停止します。
--reset-app は指定端末の app.gamenative を強制停止してプロセスのメモリを解放します。
アプリデータ、ゲーム、システムや Gradle のキャッシュは削除しません。
USAGE
}
case "${1:-status}" in
    -h|--help)
        [[ $# == 1 ]] || { usage >&2; exit 2; }
        usage
        ;;
    status)
        [[ $# == 0 ]] || { usage >&2; exit 2; }
        echo '=== リポジトリ ==='
        git status --short --branch
        printf 'コミット: '; git rev-parse HEAD
        echo '=== ホストメモリ ==='
        if command -v free >/dev/null 2>&1; then free -h; else echo 'free コマンドがありません'; fi
        echo '=== ディスク ==='
        df -h .
        echo '=== ツール ==='
        for tool in java adb malioc glslangValidator; do
            if path=$(command -v "$tool"); then printf '%s: %s\n' "$tool" "$path"; else printf '%s: 未検出\n' "$tool"; fi
        done
        echo '状態確認のみ行いました。APK 作成、プロセス停止、データ削除は行っていません。'
        ;;
    --stop-gradle)
        [[ $# == 1 ]] || { usage >&2; exit 2; }
        if [[ -n "${JAVA_HOME:-}" ]]; then
            [[ -x "$JAVA_HOME/bin/java" ]] || { echo 'JAVA_HOME に Java がありません。' >&2; exit 1; }
        elif ! command -v java >/dev/null 2>&1; then
            echo 'Java が見つからないため Gradle を実行できません。' >&2
            exit 1
        fi
        echo '現在のユーザーの Gradle デーモンを停止します（他のプロジェクトを含む）。'
        ./gradlew --stop
        ;;
    --reset-app)
        [[ $# == 2 && -n "$2" && "$2" != -* ]] || { usage >&2; exit 2; }
        command -v adb >/dev/null 2>&1 || { echo 'adb が見つかりません。' >&2; exit 1; }
        serial=$2
        state=$(adb -s "$serial" get-state | tr -d '\r')
        [[ "$state" == device ]] || { echo '指定端末が使用可能な状態ではありません。' >&2; exit 1; }
        installed=$(adb -s "$serial" shell pm path app.gamenative | tr -d '\r')
        [[ "$installed" == package:* ]] || { echo '指定端末に app.gamenative が見つかりません。' >&2; exit 1; }
        echo '指定端末の app.gamenative を強制停止します。進行中のプレイは終了します。'
        adb -s "$serial" shell am force-stop app.gamenative
        echo '停止しました。アプリデータやキャッシュの消去は行っていません。'
        ;;
    *) usage >&2; exit 2 ;;
esac
