# T9 Analysis - fcitx5-android-t9-phone

Scope: this fork adds phone-style T9 input to fcitx5-android, mainly for Chinese input through Rime. The latest goal is no longer "make each row refresh"; it is to make pinyin selection a reversible composition-state transition where the top pinyin display, pinyin-choice row, and Hanzi candidate row always describe the same composition.

## 1. Confirmed Product Target

1. Primary goal: Chinese T9 composition and candidate coherence.
2. English multi-tap and number mode should keep working, but they are secondary until Chinese T9 is stable.
3. Keep T9 logic in Kotlin for now. Do not move the feature into a new C++ Rime processor.
4. Use yuyansdk only as a behavioural reference for stable adapter-backed rows and touch parity. Do not port its full keyboard, symbol database, mode switcher, haptics, or visual style.
5. Touch and physical-key paths must call the same T9 action functions.

## 2. Clarified UX Contract

There are three visible T9 surfaces:

1. Top composition display: the resolved pinyin reading for the current composition.
2. Pinyin candidate row: selectable pinyin options for only the unresolved current suffix.
3. Hanzi candidate row: Rime Hanzi candidates for the same full composition.

Critical target flow:

```text
type 2496 -> select ai
```

Expected result:

- Nothing final is committed to the app text field yet.
- The composition state becomes resolved prefix `ai` plus unresolved suffix `96`.
- The top display should show a full reading such as `ai wo` when the current highlighted Hanzi candidate/comment implies `96 -> wo`.
- The pinyin candidate row should advance to options for `96`; it must not keep offering `ai`.
- The Hanzi candidate row should stay aligned with the full Rime composition.
- Selecting a Hanzi candidate commits Hanzi only, not raw digits or stray pinyin text.
- Delete/back should let the user undo the pinyin selection or back out of the suffix cleanly.

This means pinyin selection is not final text commit. It is an internal composition transition.

## 3. Current Implementation Snapshot

Current source was checked after several bug-fix rounds.

### Existing T9 Pieces

- Main service logic: `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`.
- Active candidate UI: `app/src/main/java/org/fcitx/fcitx5/android/input/CandidatesView.kt`.
- Active pinyin adapter: `app/src/main/java/org/fcitx/fcitx5/android/input/t9/T9PinyinChipAdapter.kt`.
- Tracker helper: `app/src/main/java/org/fcitx/fcitx5/android/input/t9/T9CompositionTracker.kt`.
- Lookup helper: `app/src/main/java/org/fcitx/fcitx5/android/input/t9/T9PinyinUtils.kt`.
- Stale-looking duplicate component: `app/src/main/java/org/fcitx/fcitx5/android/input/t9/PinyinSelectionBarComponent.kt`.

Important service methods already exist:

- `syncT9CompositionWithInputPanel`
- `getT9PinyinCandidates`
- `getT9PreeditDisplay`
- `selectT9Pinyin`
- `commitT9PinyinSelection`
- `commitHighlightedT9Pinyin`
- focus helpers for top/bottom candidate rows

Important UI facts:

- `CandidatesView.kt` owns the active pinyin row.
- The pinyin row is already a RecyclerView backed by `T9PinyinChipAdapter`.
- `PagedCandidatesUi.kt` owns the Hanzi candidate row.
- `CandidatesView.updateUi()` still mixes several rendering strategies in one pass.

## 4. Core Architectural Problem

The current code mixes at least three "truths":

1. `T9CompositionTracker`, which mainly stores unresolved raw T9 digits and apostrophes.
2. Fcitx/Rime input-panel preedit, which can be raw digits before pinyin selection but can become mixed text like `ai96` after pinyin selection.
3. Hanzi candidate comments, which can imply the full pinyin reading for the highlighted candidate.

The old tracker model works for plain raw digit composition, but it is not enough after selecting a pinyin prefix. For `2496 -> select ai`, the UI must remember:

- selected/resolved prefix: `ai`
- unresolved suffix digits: `96`
- Rime's authoritative composing state: likely mixed pinyin-plus-digits, not just raw digits
- candidate-comment reading for the whole composition, such as `ai wo`

Without an explicit model for resolved prefix plus unresolved suffix, the rows can disagree:

- top row may show only `ai` because key-count truncation now sees only the remaining two digits
- pinyin row may still offer options for stale `2496` or stale `24`
- Hanzi row may already be using Rime's newer mixed composition
- delete/focus-out can clear one state source while another source still renders old data

## 5. Required Model Shift

The next coding pass should introduce a single T9 composition/presentation model. It does not have to be a large architecture, but it must represent these concepts explicitly:

- `resolvedPinyinPrefix`: selected pinyin syllables that are still composing, not final text.
- `unresolvedDigits`: current raw T9 suffix that still needs pinyin choices.
- `rawOrMixedRimePreedit`: latest Rime preedit, for debugging and sync.
- `topReading`: what the top row should display for the full composition.
- `pinyinOptions`: choices for `unresolvedDigits` only.
- `candidateFocus`: top or bottom row.
- `pendingSelection`: optional short-lived state while `selectT9Pinyin` waits for the next fcitx update.

The model can live in `input/t9` as small data classes plus a builder, or start as service-owned state if that is less disruptive. What matters is that `CandidatesView` receives one snapshot and renders top row plus pinyin row from the same snapshot.

## 6. Current Defect List

| ID | Severity | Status | Location | Problem |
|---|---:|---|---|---|
| C1 | Critical | RESOLVED | Service plus `CandidatesView` | `T9CompositionModel` now explicitly represents `resolvedSegments` + `unresolvedDigits` + `pendingSelection` + `rawPreedit`. |
| C2 | Critical | RESOLVED | `selectT9Pinyin` | Selection records a `T9ResolvedSegment`, keeps the remaining suffix, and sets `pendingSelection` so the next Rime update can confirm/correct. |
| C3 | High | RESOLVED | `CandidatesView.updateUi` | A single `T9PresentationState` is built via `getT9PresentationState`; `updateUi` no longer switches strategies mid-render and `truncateCommentByKeyCount` is gone. |
| C4 | High | PARTIAL | `syncT9CompositionWithInputPanel` | All-digit and empty-preedit branches are handled. Mixed preedit is only reconstructed when `resolvedSegments` is already non-empty; the fallback branch just stashes `rawPreedit` with empty `unresolvedDigits`. Verify whether Rime can emit mixed preedit without a prior Kotlin-side selection and, if so, extend the parser. |
| C5 | High | OPEN | Delete/back/focus-out paths | Undo semantics after pinyin selection are not defined around resolved prefix plus unresolved suffix. |
| C6 | Medium | OPEN | `T9PinyinUtils.kt` | `t9KeyToPinyin` and `matchedPrefixLength` still use `take(6)`, silently ignoring later keys. |
| C7 | Medium | OPEN | `useT9KeyboardLayout` naming/gating | The stored setting is effectively "T9 mode enabled"; code should use that meaning consistently, but persisted-key migration is optional later. |
| C8 | Medium | OPEN | `PinyinSelectionBarComponent.kt` | Duplicate pinyin-row implementation appears stale and can mislead future changes. |
| C9 | Low | OPEN | `onKeyUp` | `t9ConsumedNavigationKeyUp = null` appears twice in the same branch. Harmless but should be cleaned during nearby edits. |
| C10 | Low | OPEN | English mode | English STAR and multi-tap display may still need cleanup, but they are not the main blocker for Chinese T9 coherence. |

### Additional change since last revision

- `handleFcitxEvent.CommitStringEvent` in `FcitxInputMethodService` now intercepts letter-only Rime commits in T9 Chinese mode and routes them through `updateComposingText` instead of `commitText`. This keeps raw pinyin letters inside the composing region so the app text field does not receive final text on pinyin chip/OK selection. Phase C delete/focus-out work must keep this branch in mind: clearing the model on focus-out must also clear the composing region, and delete should not rely on that letter run being a real commit.

## 7. Reference From yuyansdk

Use only these ideas:

- Candidate and pinyin rows backed by stable adapters.
- One row update path per render.
- Touch selection calls the same action as hardware selection.

Do not port:

- Full keyboard UI.
- Haptics or sound.
- Symbol database.
- Input-mode switcher.
- Cloud input or frequency reranking.

## 8. Recommended Implementation Order

Steps 1-4 landed; remaining work starts at step 5.

1. ~~Add a composition model that can represent resolved prefix plus unresolved suffix.~~ (done)
2. ~~Make `selectT9Pinyin` update/mark that model transactionally instead of losing the selected prefix.~~ (done)
3. ~~Build one presentation snapshot from model plus latest fcitx input-panel and paged-candidate data.~~ (done)
4. ~~Make `CandidatesView` render the top row and pinyin row from that single snapshot.~~ (done)
5. Define delete/back/focus-out transitions against the same model (plan Phase C). Must also clear the composing region written by the letter-commit intercept described in section 6.
6. Verify C4: confirm whether Rime ever emits mixed preedit without a prior Kotlin-side selection and, if so, extend `syncT9CompositionWithInputPanel` to rebuild `resolvedSegments` from the letter run.
7. Only then address lower-risk cleanup: six-digit truncation, preference naming, stale component removal, English cleanup, and controller extraction.

## 9. Verification Notes

- Use correct T9 examples. `ai` maps to `2 4`, not `2 3`.
- Key regression: `2496 -> select ai -> select Hanzi -> back/delete`.
- Success means all three rows stay mutually consistent, not merely that one row looks correct.
- Verification must cover both touch chip selection and physical focus/OK selection.
- After pinyin selection, the app text field must not receive final text until a Hanzi candidate is selected or Rime commits.
