#!/usr/bin/env bash

set -euo pipefail

case_name="${1:-}"
serial="${ADB_SERIAL:-}"
delay_seconds="${T9_KEY_DELAY_SECONDS:-0.18}"
output_dir="${T9_SMOKE_OUTPUT_DIR:-/tmp/t9-text-smoke}"

if [[ -z "$case_name" || -z "$serial" ]]; then
    echo "Usage: ADB_SERIAL=host:port $0 CASE"
    echo "Cases: pinyin-nihao, pinyin-separator, pinyin-long, pinyin-folded, english-hello"
    exit 2
fi

case "$case_name" in
    pinyin-nihao) digits="64426" ;;
    pinyin-separator) digits="64416" ;;
    pinyin-long) digits="946649366674494233" ;;
    pinyin-folded) digits="434434" ;;
    english-hello) digits="43556" ;;
    *)
        echo "Unknown case: $case_name"
        exit 2
        ;;
esac

adb -s "$serial" get-state >/dev/null
mkdir -p "$output_dir"

# The harness never focuses an editor, clears text, commits a candidate, or presses Return.
# Keeping those destructive actions manual prevents a smoke run from sending a real message.
for ((index = 0; index < ${#digits}; index++)); do
    digit="${digits:index:1}"
    adb -s "$serial" shell input keyboard keyevent "KEYCODE_$digit"
    sleep "$delay_seconds"
done

sleep 0.5
artifact="$output_dir/${case_name}-$(date +%Y%m%d-%H%M%S).png"
adb -s "$serial" exec-out screencap -p > "$artifact"
echo "$artifact"
