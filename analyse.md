# T9 Analysis - fcitx5-android-t9-phone

Scope: this fork adds phone-style T9 input to fcitx5-android, mainly for Chinese input through Rime. This document records the current implementation snapshot and the latest clarified UX contract so the next coding pass can fix the state model instead of continuing one bug at a time.

## 1. Confirmed Product Target

1. Primary goal: Chinese T9 input. English multi-tap and number mode should keep working, but the main active UX work is Chinese T9 composition and candidate coherence.
2. Keep T9 state in Kotlin for now. The design may be simplified, but do not move T9 logic into a new C++ Rime processor.
3. Use yuyansdk only as a behavioural reference for reliable row updates and touch interactions. Do not port its full keyboard, symbol database, haptics, mode switcher, or visual style.
4. Touch is a first-class secondary input path. Physical T9 keys are primary, but every visible T9 action should also be doable by touch.
5. Touch and hardware paths must share the same action functions. For example, tapping a pinyin chip and pressing OK on a highlighted pinyin chip should both call the same `selectT9Pinyin` path.

## 2. Clarified UX Contract

There are three T9 surfaces that must stay coherent:

1. Top composition display: shows the user-facing pinyin reading for the current composition.
2. Pinyin candidate row: shows selectable pinyin choices only for the unresolved current segment.
3. Hanzi candidate row: shows Rime Hanzi candidates for the same composition state.

Latest clarified behaviour:

- After `2496 -> select ai`, the app text field should still receive no final text yet.
- The top composition display should show the full resolved reading, for example `ai wo` if the current highlighted Hanzi candidate implies that `96 -> wo`.
- The pinyin candidate row should no longer offer `ai`; it should advance to the unresolved suffix only.
- Selecting a Hanzi candidate should commit Hanzi only, not stray pinyin text or raw digits.
- Delete/back should let the user reopen or back out of a wrong pinyin selection cleanly.

This means a pinyin selection is a composition-state transition, not a final commit.

## 3. Current Implementation Snapshot

Current source was checked after several bug-fix rounds. The current problem is no longer just one missing event handler; it is a coherence issue between multiple state sources.

### T9 Routing And State

- Main routing lives in `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`.
- Important methods now include:
  - `handleT9SpecialKey`
  - `handleDigitKey`
  - `handleT9SpecialKeyUp`
  - `forwardKeyEvent`
  - `syncT9CompositionWithInputPanel`
  - `getT9PinyinCandidates`
  - `getT9PreeditDisplay`
  - `selectT9Pinyin`
  - `commitT9PinyinSelection`
  - focus-navigation helpers for top and bottom rows
- State now includes `currentT9Mode`, English case state, long-press flags, `t9CompositionTracker`, and top-versus-bottom candidate focus.
- `T9CompositionTracker.clear()` exists.
- `syncT9CompositionWithInputPanel(...)` exists, but it is still part of a mixed model rather than a complete shared presentation pipeline.

### Active Candidate UI

- `CandidatesView.kt` is the active T9 pinyin-row owner.
- The active pinyin row is already on `RecyclerView` with `T9PinyinChipAdapter.kt`.
- `PagedCandidatesUi.kt` owns the bottom Hanzi row.
- `PinyinSelectionBarComponent.kt` still appears stale and should not receive new work unless a fresh source check proves it is wired.

### Known Live Render Split

The code currently renders T9 from multiple sources:

- The pinyin row reads `service.getT9PinyinCandidates()`, which is based on the tracker's current segment.
- The top row in `CandidatesView.updateUi()` can use:
  - tracker-based T9 preedit
  - raw preedit fallback
  - current Hanzi candidate comment truncated by key count
- `selectT9Pinyin(...)` updates `t9CompositionTracker` optimistically before the asynchronous fcitx update comes back.
- `syncT9CompositionWithInputPanel(...)` only runs behind conditional gating in `CandidatesView.updateUi()`.

That is the architectural reason the three rows can disagree after pinyin selection, backspace, or focus changes.

## 4. Core Architectural Problem

The current implementation mixes at least three truths:

1. Local Kotlin tracker state in `t9CompositionTracker`
2. Raw fcitx input-panel preedit
3. Hanzi candidate comment-derived reading

Because each row can read from a different source in the same render cycle, several bad states become possible:

- top row shows one reading while pinyin row still offers chips for an older segment
- Hanzi row is correct for the new composition while top row is still based on stale tracker state
- pinyin selection updates the tracker immediately, but the visible final state depends on an async fcitx response that has not arrived yet
- clearing or focus-out can reset one surface while another surface still renders from old data

The next implementation should therefore build one shared presentation snapshot from fcitx event data and use that snapshot to render all three rows.

## 5. Current Defect List

| ID | Severity | Current location | Problem |
|---|---:|---|---|
| C1 | Critical | `FcitxInputMethodService.kt` plus `CandidatesView.kt` | No single shared T9 presentation state exists. Top row, pinyin row, and Hanzi row can read different truths in the same update pass. |
| C2 | High | `CandidatesView.kt` `updateUi` | T9 sync is conditionally gated, so some input-panel changes do not refresh or clear the tracker before the UI renders. |
| C3 | High | `FcitxInputMethodService.kt` `selectT9Pinyin` | Pinyin selection is partly optimistic local state and partly async fcitx state, so post-selection UI can show mixed old/new composition. |
| C4 | High | Delete / backspace / focus-out paths | Back-out behaviour after a pinyin selection is not yet defined as one coherent composition-state transition. |
| C5 | Medium | `T9PinyinUtils.kt` | `t9KeyToPinyin` still truncates input to six digits with `take(6)`, silently ignoring later keys. |
| C6 | Medium | `useT9KeyboardLayout` preference scope | The preference is serving as semantic T9 mode enabled, but naming and remaining gates may still be inconsistent. |
| C7 | Medium | `PinyinSelectionBarComponent.kt` | Duplicate pinyin-row implementation appears stale and can mislead future fixes. |
| C8 | Medium | English mode | English STAR and multi-tap display may still need cleanup, but they are no longer the main blocker for Chinese T9 coherence. |

## 6. Reference From yuyansdk

Use only the following ideas:

- Candidate and pinyin rows backed by stable adapter-based lists
- Clear row responsibility boundaries
- Touch selection calling the same commit function as hardware selection

Do not port:

- Full keyboard UI
- Haptics or sound
- Symbol database
- Input-mode switcher
- Cloud input or frequency reranking

## 7. Recommended Implementation Order

1. Build one T9 presentation snapshot from fcitx input-panel data plus paged candidates.
2. Make `CandidatesView` render top row and pinyin row from that snapshot in one pass.
3. Make `selectT9Pinyin(...)` transactional so authoritative UI settles on the next fcitx update, not on mixed local state.
4. Define coherent delete/back/focus-out behaviour for reopening earlier segment choices.
5. Only after that, revisit longer-tail items such as preference naming, long-input truncation, English STAR cleanup, and stale component removal.

## 8. Verification Notes

- Use correct T9 examples. For example, `ai` maps to digits `2 4`, not `2 3`.
- Every interaction step needs both a physical-key verification and a touch verification when a touch surface exists.
- The key regression scenario is `2496 -> select ai -> select Hanzi -> back/delete`.
- The success condition is not just “the correct Hanzi appears”; all three rows must stay mutually consistent through the whole flow.
