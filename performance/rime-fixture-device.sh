#!/bin/sh
set -eu

mode=$1
adb=$2
apk_dir=$3
config_dir=$4
archive_dir=$5
schema_override=$6
package_name=org.fcitx.fcitx5.android.plugin.rime.performance
remote_config=/data/local/tmp/fcitx-t9-rime-config
remote_revision=/data/local/tmp/fcitx-t9-rime-config.revision
remote_archive=/data/local/tmp/fcitx-t9-rime-config.tar.gz
archive_format=2

if [ "$mode" = "install" ]; then
    if [ ! -d "$config_dir/.git" ]; then
        echo "The maintained Rime configuration is not a Git checkout: $config_dir" >&2
        exit 1
    fi
    dirty=$(git -C "$config_dir" status --porcelain --untracked-files=all)
    if [ -n "$dirty" ]; then
        echo "The maintained Rime configuration must be clean before profiling:" >&2
        printf '%s\n' "$dirty" >&2
        exit 1
    fi
    for required in default.yaml t9.schema.yaml t9_stroke.schema.yaml t9_zhuyin.schema.yaml; do
        if [ ! -f "$config_dir/$required" ]; then
            echo "Missing required Rime performance schema: $config_dir/$required" >&2
            exit 1
        fi
    done
    revision=$(git -C "$config_dir" rev-parse HEAD)
    override_hash=$(shasum -a 256 "$schema_override" | awk '{ print $1 }')
    fingerprint="$revision-$override_hash-$archive_format"
    mkdir -p "$archive_dir"
    archive="$archive_dir/rime-config-$revision-v$archive_format.tar.gz"
    if [ ! -f "$archive" ]; then
        archive_tmp="$archive.tmp"
        rm -f "$archive_tmp"
        (
            cd "$config_dir"
            COPYFILE_DISABLE=1 tar \
                --exclude='./.git' \
                --exclude='./.github' \
                --exclude='./scripts' \
                --exclude='./.gitignore' \
                --exclude='./README.md' \
                --exclude='./LICENSE' \
                --exclude='./THIRD_PARTY.md' \
                --exclude='./go.work' \
                -czf "$archive_tmp" .
        )
        mv "$archive_tmp" "$archive"
    fi
fi

devices=$($adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [ -z "$devices" ]; then
    echo "No connected Android device is available for the Rime performance fixture." >&2
    exit 1
fi

for serial in $devices; do
    if [ "$mode" = "install" ]; then
        abi=$($adb -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')
        apk=$(find "$apk_dir" -maxdepth 1 -type f -name "*-${abi}-performanceRelease.apk" -print -quit)
        if [ -z "$apk" ]; then
            echo "No Rime performance fixture APK for $serial ($abi) in $apk_dir." >&2
            exit 1
        fi
        "$adb" -s "$serial" install -r -t "$apk"

        device_fingerprint=$(
            "$adb" -s "$serial" shell cat "$remote_revision" 2>/dev/null | tr -d '\r' || true
        )
        staged=$(
            "$adb" -s "$serial" shell \
                "test -f '$remote_config/t9.schema.yaml' && \
                 test -f '$remote_config/t9_stroke.schema.yaml' && \
                 test -f '$remote_config/t9_zhuyin.schema.yaml' && echo ready" \
                2>/dev/null | tr -d '\r' || true
        )
        if [ "$device_fingerprint" != "$fingerprint" ] || [ "$staged" != "ready" ]; then
            remote_new="$remote_config.new"
            "$adb" -s "$serial" push "$archive" "$remote_archive" >/dev/null
            "$adb" -s "$serial" shell \
                "rm -rf '$remote_new' && mkdir -p '$remote_new' && \
                 tar -xzf '$remote_archive' -C '$remote_new'"
            "$adb" -s "$serial" push "$schema_override" \
                "$remote_new/default.custom.yaml" >/dev/null
            verified=$(
                "$adb" -s "$serial" shell \
                    "test -f '$remote_new/default.yaml' && \
                     test -f '$remote_new/default.custom.yaml' && \
                     test -f '$remote_new/t9.schema.yaml' && \
                     test -f '$remote_new/t9_stroke.schema.yaml' && \
                     test -f '$remote_new/t9_zhuyin.schema.yaml' && echo ready" \
                    | tr -d '\r'
            )
            if [ "$verified" != "ready" ]; then
                echo "Unable to stage the maintained Rime configuration on $serial." >&2
                exit 1
            fi
            "$adb" -s "$serial" shell \
                "rm -rf '$remote_config' && mv '$remote_new' '$remote_config' && \
                 printf '%s' '$fingerprint' > '$remote_revision' && \
                 rm -f '$remote_archive'"
        fi

    elif [ "$mode" = "uninstall" ]; then
        "$adb" -s "$serial" uninstall "$package_name" >/dev/null 2>&1 || true
    else
        echo "Unknown fixture operation: $mode" >&2
        exit 1
    fi
done
