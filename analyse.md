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

Current source was checked after several bug-fix rounds and the latest uncommitted T9 updates.

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
- `getT9PresentationState`
- `filterPagedByResolvedPinyin`
- `selectT9Pinyin`
- `commitT9PinyinSelection`
- `commitHighlightedT9Pinyin`
- `handleVirtualT9Backspace`
- focus helpers for top/bottom candidate rows

Important UI facts:

- `CandidatesView.kt` owns the active pinyin row.
- The pinyin row is already a RecyclerView backed by `T9PinyinChipAdapter`.
- `PagedCandidatesUi.kt` owns the Hanzi candidate row.
- `CandidatesView.updateUi()` now builds one T9 presentation state and renders from it.
- When resolved pinyin exists, `CandidatesView.updateUi()` uses `filterPagedByResolvedPinyin` and stores `t9ShownPaged` so filtered tap indices can be translated back to fcitx indices.

## 4. Core Architectural Problem

The current design deliberately keeps two state sources and one display fallback:

1. `T9CompositionTracker`, which stores raw T9 digits/apostrophes from user key events.
2. `T9CompositionModel`, which stores selected/resolved pinyin segments plus the unresolved digit suffix.
3. Fcitx/Rime input-panel preedit, which is now treated as display/debug fallback only because the t9 schema reports converted display text such as `a` or `ai'96`, not reliable raw keys.
4. Hanzi candidate comments, which can imply the full pinyin reading for the highlighted candidate when no selected prefix exists.

The old tracker-only model works for plain raw digit composition, but it is not enough after selecting a pinyin prefix. For `2496 -> select ai`, the Kotlin model must remember:

- selected/resolved prefix: `ai`
- unresolved suffix digits: `96`
- Rime's composing query still based on the full digit sequence
- candidate-comment reading for the whole composition, such as `ai wo`

Without an explicit model for resolved prefix plus unresolved suffix, the rows can disagree:

- top row may show only `ai` because key-count truncation now sees only the remaining two digits
- pinyin row may still offer options for stale `2496` or stale `24`
- Hanzi row may show readings for a different pinyin prefix unless filtered by the selected prefix
- delete/focus-out can clear one state source while another source still renders old data

## 5. Current Model Shape

The source now has the small composition/presentation model that earlier analysis requested:

- `resolvedSegments`: selected pinyin syllables that are still composing, not final text, each paired with source digits.
- `unresolvedDigits`: current raw T9 suffix that still needs pinyin choices.
- `rawPreedit`: latest Rime display preedit, used only as display/debug fallback.
- `topReading`: what the top row should display for the full composition.
- `pinyinOptions`: choices for `unresolvedDigits` only.
- `pendingSelection`: retained as a lightweight marker, not as a trigger for Rime replay.

Candidate focus remains service-owned state rendered by `CandidatesView.updateT9FocusIndicator()` rather than part of `T9PresentationState`. That is an intentional small-model deviation, not a blocker.

## 6. Current Defect List

| ID | Severity | Status | Location | Problem |
|---|---:|---|---|---|
| C1 | Critical | RESOLVED | Service plus `CandidatesView` | `T9CompositionModel` now explicitly represents `resolvedSegments` + `unresolvedDigits` + `pendingSelection` + `rawPreedit`. |
| C2 | Critical | RESOLVED | `selectT9Pinyin` | Selection records a `T9ResolvedSegment`, keeps the remaining suffix, and sets `pendingSelection`. It no longer replays backspaces/pinyin/digits into Rime. |
| C3 | High | RESOLVED | `CandidatesView.updateUi` | A single `T9PresentationState` is built via `getT9PresentationState`; `updateUi` no longer switches strategies mid-render and `truncateCommentByKeyCount` is gone. |
| C4 | High | RESOLVED | `syncT9CompositionWithInputPanel` | Sync no longer parses Rime display preedit for raw digits. The key-event tracker is authoritative; empty preedit clears state; non-empty preedit is stored as display/debug fallback. |
| C5 | High | PARTIAL | Delete/back/focus-out paths | Delete/back now reopens the latest resolved segment before forwarding normal delete. Focus-out/touch-away coherent clear is still open. |
| C6 | Medium | OPEN | `T9PinyinUtils.kt` | `t9KeyToPinyin` and `matchedPrefixLength` still use `take(6)`, silently ignoring later keys. |
| C7 | Medium | OPEN | `useT9KeyboardLayout` naming/gating | The stored setting is effectively "T9 mode enabled"; code should use that meaning consistently, but persisted-key migration is optional later. |
| C8 | Medium | OPEN | `PinyinSelectionBarComponent.kt` | Duplicate pinyin-row implementation appears stale and can mislead future changes. |
| C9 | Low | RESOLVED | `onKeyUp` | The duplicate `t9ConsumedNavigationKeyUp = null` cleanup is no longer present in the current source. |
| C10 | Low | OPEN | English mode | English STAR and multi-tap display may still need cleanup, but they are not the main blocker for Chinese T9 coherence. |
| C11 | Medium | RESOLVED_WITH_TRADEOFF | Hanzi row after pinyin selection | `filterPagedByResolvedPinyin` client-filters the current Rime page by resolved pinyin prefix and translates filtered tap indices back to original fcitx indices. If no candidates on the page match, it falls back to the unfiltered page rather than showing an empty row. |

### Additional change since last revision

- The earlier `handleFcitxEvent.CommitStringEvent` letter-only intercept has been removed. Under the current Option B design, pinyin selection never pushes letters into Rime, so the intercept is no longer needed and could hide unrelated commit bugs.
- `handleVirtualT9Backspace()` now returns `Boolean` so on-screen delete can skip sending a normal backspace when it was consumed by a model-only reopen.
- Physical DEL/BACK uses the same reopen transition and consumes the matching key-up through `t9ConsumedNavigationKeyUp`.

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

Steps 1-6 landed; remaining work starts at step 7.

1. ~~Add a composition model that can represent resolved prefix plus unresolved suffix.~~ (done)
2. ~~Make `selectT9Pinyin` update/mark that model transactionally instead of losing the selected prefix.~~ (done)
3. ~~Build one presentation snapshot from model plus latest fcitx input-panel and paged-candidate data.~~ (done)
4. ~~Make `CandidatesView` render the top row and pinyin row from that single snapshot.~~ (done)
5. ~~Define delete/back transitions against the same model.~~ (done)
6. Implement focus-out/touch-away coherent clear (plan Phase C Step 7).
7. Manually verify Option B on device, especially `2496 -> select ai -> delete`, touch chip parity, physical OK parity, and filtered Hanzi tap correctness.
8. Only then address lower-risk cleanup: six-digit truncation, preference naming, stale component removal, English cleanup, and controller extraction.

## 9. Verification Notes

- Use correct T9 examples. `ai` maps to `2 4`, not `2 3`.
- Key regression: `2496 -> select ai -> select Hanzi -> back/delete`.
- Success means all three rows stay mutually consistent, not merely that one row looks correct.
- Verification must cover both touch chip selection and physical focus/OK selection.
- After pinyin selection, the app text field must not receive final text until a Hanzi candidate is selected or Rime commits.
