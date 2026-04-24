# T9 Analysis - Engine-Backed Pinyin Pivot

This file is the canonical `analysis.md` requested by `rizum-agent.md`.

## Current Goal

Chinese T9 pinyin selection should narrow Rime itself instead of relying on
client-side Hanzi filtering. The previous overlay/filter path worked for common
flows such as `2496 -> ai`, but it is fragile for sparse pinyin combinations and
short selections such as `g`, `h`, or `l`, where comment filtering can hide all
Hanzi candidates.

## Active Direction

- Keep the UI and T9 state machine in Kotlin.
- Use the existing fcitx-rime bridge, not a new Rime processor.
- On pinyin chip selection, replace the matched raw T9 digit span in Rime input
  with `pinyin'`, following the Yuyan reference behavior.
- Treat engine-backed Rime candidates as authoritative; only use client-side
  selected-pinyin filtering when the bridge replacement fails.
- Delete/reopen must reverse the replacement by restoring the selected segment's
  original T9 digits.

## Follow-Up Risk

T9 Hanzi candidate budgeting can still be inconsistent across raw pages, bulk
pages, fallback-filtered pages, and engine-backed pages. The first cleanup pass
lets locally budgeted chunks of the current Rime page page with Up/Down before
falling through to fcitx page changes. The second cleanup pass centralizes cost
calculation: English T9 words cost 2 budget units each, emoji clusters cost 2
budget units each, and regular Hanzi/text costs its code point count. Device
testing still needs to confirm the configured setting, for example `10`,
consistently follows those rules.
