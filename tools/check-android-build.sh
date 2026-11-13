#!/usr/bin/env bash
# Inspect CLI prerequisites without invoking Gradle or building an APK.
set -u
cd "$(dirname "$0")/.."
missing=0

if [[ -n "${JAVA_HOME:-}" ]]; then
    java_cmd="$JAVA_HOME/bin/java"
else
    java_cmd="$(command -v java || true)"
fi
if [[ -n "$java_cmd" && -x "$java_cmd" ]]; then
    version=$("$java_cmd" -version 2>&1 | sed -n '1s/.*version "\([^"]*\)".*/\1/p')
    major=${version%%.*}
    if [[ "$major" =~ ^[0-9]+$ ]] && (( major >= 17 )); then
        echo "OK: Java $version ($java_cmd)"
    else
        echo "ERROR: Java 17 or later required (found ${version:-unknown})."
        missing=1
    fi
else
    echo "ERROR: JDK 17 not found. Set JAVA_HOME or add java to PATH."
    missing=1
fi

# Gradle prioritizes sdk.dir in local.properties over environment variables.
sdk=""
if [[ -f local.properties ]]; then
    sdk=$(sed -n 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*//p' local.properties | tail -n 1)
fi
sdk=${sdk:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}
if [[ -z "$sdk" ]]; then
    echo "ERROR: Android SDK not configured (ANDROID_HOME or sdk.dir)."
    missing=1
else
    echo "Android SDK: $sdk"
    for path in "platforms/android-36/android.jar" "ndk/27.3.13750724/source.properties"; do
        if [[ -f "$sdk/$path" ]]; then
            echo "OK: $path"
        else
            echo "ERROR: missing $path"
            missing=1
        fi
    done
    if compgen -G "$sdk/build-tools/*/aapt2" >/dev/null; then
        echo "OK: Android build tools found"
    else
        echo "ERROR: Android build tools not found."
        missing=1
    fi
fi
if [[ ! -f gradle/wrapper/gradle-wrapper.jar || ! -x ./gradlew ]]; then
    echo "ERROR: Gradle wrapper missing or not executable."
    missing=1
else
    echo "OK: Gradle wrapper present (distribution/dependencies not checked)."
fi
if (( missing )); then
    echo "Not ready. See docs/cli-build-ja.md."
    exit 1
fi
echo "Local prerequisites found. Network access and dependency resolution still need verification."
