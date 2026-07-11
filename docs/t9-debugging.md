# T9 Debugging Notes

These notes capture the repeatable debugging workflow for physical-key T9
bugs, UI refresh timing, and candidate-surface frame analysis.

## Physical Key Injection

Plain ADB key events can be misleading when testing an input method:

```bash
adb shell input keyevent 11
```

On some devices this is treated like a generic injected key event and may write
`4` directly into the focused app, bypassing the IME path that real hardware
keyboard events use. This can hide bugs in `FcitxInputMethodService`,
`PhysicalT9KeyHandler`, and `PhysicalT9KeyFlow`.

Inject key events with an explicit keyboard source when testing physical T9
behavior:

```bash
adb shell input keyboard keyevent 11
```

Do not move `keyboard` after `keyevent`. On Android's `input` command the source
is a positional argument before the command; an option-style spelling may be
silently interpreted as generic text injection and reach the IME as
`KEYCODE_UNKNOWN`. `11` is `KEYCODE_4`; named key codes are usually clearer:

```bash
adb shell input keyboard keyevent KEYCODE_4
adb shell input keyboard keyevent KEYCODE_DEL
adb shell input keyboard keyevent KEYCODE_DPAD_CENTER
```

Before testing, confirm the debug IME is active. This project often has both
release and debug builds installed:

```bash
adb shell ime list -s
adb shell settings get secure default_input_method
adb shell ime set org.fcitx.fcitx5.android.debug/org.fcitx.fcitx5.android.input.FcitxInputMethodService
```

When there are duplicate wireless-debugging transports, pass `-s` explicitly:

```bash
adb -s 192.168.x.x:PORT shell input keyboard keyevent KEYCODE_4
```

## ADB Rime Data Imports

Files copied into the debug Rime directory with `adb push` are owned by
`shell`. On emulated external storage they may arrive as `0644`, which lets Rime
read the configuration but prevents the app's `ext_data_rw` group from updating
`user.yaml`. The failed write makes every process cold start repeat Rime
maintenance and can add several seconds before Chinese input is ready.

After an ADB import, make shell-owned files group-writable once:

```bash
RIME_DIR=/storage/emulated/0/Android/data/org.fcitx.fcitx5.android.debug/files/data/rime
adb shell "find \"$RIME_DIR\" -type f -user shell -exec chmod 660 {} +"
```

Allow one deployment to finish, restart the debug IME, and check logcat. An
unchanged configuration should not report `failed to save config to stream`,
and Rime-ready should follow Fcitx-ready without another maintenance pass. Use
the release package path without `.debug` when diagnosing the release IME.

## Release-like Rime Performance Fixture

Performance collection must not borrow the already-deployed formal or debug
Rime directory. The connected performance tasks install an isolated Rime
plugin, stage the clean sibling `rime-ice-t9-phone` checkout, and copy that
configuration into the isolated target after Android resets its package data.
Keep the sibling checkout clean and make sure it contains `t9`, `t9_stroke`,
and `t9_zhuyin` before collecting profiles.

The readiness proof is exact:

```text
Rime ready: schema=拼音九键
```

Generic Rime readiness or a `朙月拼音` schema means the plugin's built-in Luna
configuration was deployed and the result is invalid. The critical profile
journey must also observe `PINYIN -> STROKE -> ZHUYIN -> PINYIN`; a partly
working stock Stroke schema is not sufficient evidence.

Use the release-like tasks rather than timing the debug APK when comparing ART
compilation:

```bash
./gradlew :app:generateReleaseBaselineProfile \
  -PbuildABI=arm64-v8a \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile

./gradlew :performance:connectedBenchmarkReleaseAndroidTest \
  -PbuildABI=arm64-v8a \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
```

The Driver restores the previously selected IME after each run. It must never
clear, seed, or select the user's formal or debug package as a benchmark target.

## Responsiveness Trace

Turn on **Trace T9 responsiveness** in the app developer settings before
profiling a typing session. Then reproduce the lag and open **T9 responsiveness
report** from the same developer settings screen.

Useful trace names to compare:

- `PhysicalT9KeyHandler.keyDown`
- `PhysicalT9KeyHandler.keyUp`
- `CandidatesView.updateUi`
- `CandidatesView.updateUi.buildState`
- `CandidatesView.updateUi.renderCandidates`
- `CandidatesView.updateUi.renderPinyin`
- `CandidatesView.updateUi.renderVisibility`
- `T9EnglishDictionary.candidatesFor`
- `SmartEnglishPredictionDictionary.predictionsAfter`

The primary report measures physical-key decision, engine-source wait,
snapshot construction, render work, and the following frame callback as one
generation-owned transaction. The developer report exposes a partial window
immediately; logcat emits aggregate summaries every 20 completed inputs. Test
with a short, repeatable sequence first, such as one first-letter Chinese T9
case, one folded pinyin-row case, and `43556` for Smart English `hello`.

## Screen Recording and Frame Analysis

For UI flash, delayed pinyin-row reveal, or bubble-width regressions, record the
screen while reproducing the exact sequence:

```bash
adb shell screenrecord --time-limit 10 /sdcard/t9-debug.mp4
adb pull /sdcard/t9-debug.mp4 /tmp/t9-debug.mp4
```

Split the video into frames and inspect the first wrong frame:

```bash
mkdir -p /tmp/t9-frames
ffmpeg -i /tmp/t9-debug.mp4 -vf fps=60 /tmp/t9-frames/frame_%04d.png
```

What to look for:

- A first pinyin chip row that appears clipped before the full row renders.
- Candidate bubbles moving vertically between adjacent frames.
- Bottom candidate bubbles whose width changes because the pinyin row or
  pagination row is driving the surface unexpectedly.
- Smart English candidates showing raw digits or stale previews before the
  dictionary/page cache is ready.

Frame analysis is especially useful when user-visible lag is shorter than a
manual screenshot can capture. Pair the frame number with the responsiveness
trace report so the visual symptom and the measured hot path stay connected.
