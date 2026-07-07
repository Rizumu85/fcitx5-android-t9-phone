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

Some Android builds also accept the option-style spelling:

```bash
adb shell input keyevent --source keyboard 11
```

Use whichever spelling the connected device accepts, but keep the source
explicit. `11` is `KEYCODE_4`; named key codes are usually clearer when the
command supports them:

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

The trace is aggregated, so test with a short, repeatable sequence first. For
example, use one first-letter Chinese T9 case, one folded pinyin-row case, and
one Smart English word such as `43556` for `hello`.

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
