# Implementation Plan - T9 Cohesive State First

Audience: another coding agent. Read `analyse.md` first. The current source already contains many partial T9 fixes, so do not continue bug-by-bug from older assumptions. The next work should start from a shared-state design for the three T9 surfaces.

## Ground Rules

1. Work on the active pinyin row in `CandidatesView.kt` unless a fresh source check proves `PinyinSelectionBarComponent.kt` is registered.
2. Keep changes surgical. Do not reformat nearby code or do a broad refactor while behaviour is still moving.
3. Treat the current `use_t9_keyboard_layout` preference as the semantic "T9 mode enabled" flag unless a proper migration is added later.
4. Touch and hardware paths must call the same underlying action functions.
5. Do not add a new Rime processor or move T9 logic to C++.
6. Use correct T9 examples in tests. `ai` is `2 4`, not `2 3`.
7. Every step must define manual on-device verification before coding.
8. Prefer one shared T9 presentation model over more ad-hoc conditionals in the UI.

## Confirmed Product Rules

These rules came from the latest planning pass and should control the next implementation:

- After `2496 -> select ai`, the app text field should still receive no final text yet.
- The top composition display should show the full resolved reading, for example `ai wo` if the current highlighted Hanzi candidate implies that `96 -> wo`.
- The pinyin candidate row should no longer offer `ai`; it should advance to the unresolved suffix only.
- Selecting Hanzi should commit Hanzi only, not stray pinyin text or raw digits.
- Back / delete should let the user back out of a wrong pinyin choice cleanly.

## Phase 0 - Reconfirm Active Paths

### Step 0 - Source Check And Build

Purpose: avoid fixing stale or unused code.

Change:

- No behaviour change.
- Reconfirm the active path for:
  - `CandidatesView.updateUi`
  - `CandidatesView.updatePinyinBar`
  - `FcitxInputMethodService.syncT9CompositionWithInputPanel`
  - `FcitxInputMethodService.selectT9Pinyin`
  - `T9CompositionTracker`
- Reconfirm whether `PinyinSelectionBarComponent.kt` is still unused.
- Build the app or run the closest compile check before starting architecture changes.

Files:

- No required edits.

Verify:

- Build passes or the exact existing build failure is documented.
- The active pinyin row and active top-row display path are confirmed before Step 1 starts.

## Phase A - Cohesive T9 Composition State

### Step 1 - Introduce One T9 Presentation Snapshot

Purpose: give the top composition display, pinyin row, and Hanzi row one shared source of truth.

Change:

- Add one Kotlin-side presentation snapshot near the T9 service logic, for example `T9PresentationState`.
- Build it from the latest fcitx input-panel data plus paged candidates.
- The snapshot should contain at least:
  - normalized raw T9 composition digits
  - resolved reading for the top row
  - unresolved current-segment digits
  - pinyin options for that unresolved segment
  - whether the pinyin row should be visible
  - current focus row
- Keep `T9CompositionTracker` as a helper for segment semantics only, not the sole long-term UI truth.

Files:

- `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`
- Possibly `app/src/main/java/org/fcitx/fcitx5/android/input/t9/T9CompositionTracker.kt`
- Possibly `app/src/main/java/org/fcitx/fcitx5/android/input/t9/T9PinyinUtils.kt`

Verify:

- With a single composition, all three rows can be described from one snapshot for the same fcitx event.
- There is no longer a need to mix tracker-only state with comment-only state inside `CandidatesView`.

### Step 2 - Make CandidatesView Render From That Snapshot

Purpose: stop the top row and pinyin row from reading different truths during one update pass.

Change:

- In `CandidatesView.updateUi`, always refresh the T9 state before computing the top row or pinyin row.
- Remove the current conditional sync behaviour that only runs in some input-panel states.
- Make one render pass use one snapshot:
  - top row shows resolved reading
  - pinyin row shows only unresolved current-segment options
  - bottom row continues to show paged Hanzi candidates
- Do not let the top row switch between unrelated strategies mid-render.

Files:

- `app/src/main/java/org/fcitx/fcitx5/android/input/CandidatesView.kt`
- Possibly `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`

Verify:

- `24` shows a consistent top row and pinyin row.
- `2496 -> select ai` no longer produces a top row, pinyin row, and Hanzi row that disagree with each other.
- Clearing composition by commit, cancel, or leaving the field clears all three rows coherently.

### Step 3 - Make Pinyin Selection Transactional

Purpose: make `selectT9Pinyin(...)` a shared action without letting optimistic local state become the final UI truth.

Change:

- Keep `selectT9Pinyin(pinyin)` as the single shared action for touch and hardware.
- It may still send backspaces and letters to fcitx, but the final visible row state should be finalized by the next authoritative fcitx update.
- If an optimistic local update is still needed for responsiveness, limit it to a temporary pending state that is explicitly overwritten on the next input-panel update.
- Preserve unresolved suffix semantics:
  - selecting `ai` from `2496` consumes only the matched prefix
  - the suffix remains available for the next pinyin row
  - the top row may still show the full resolved reading, such as `ai wo`

Files:

- `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/input/CandidatesView.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/input/t9/T9CompositionTracker.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/input/t9/T9PinyinUtils.kt`

Verify:

- `2496 -> select ai` does not insert `ai` into the app text field.
- The top row shows full resolved reading.
- The pinyin row advances past `ai`.
- The Hanzi row stays aligned with the same composition.

### Step 4 - Define Coherent Backspace And Undo Behaviour

Purpose: stop pinyin selection from becoming a one-way trap that leaves mixed state.

Change:

- Explicitly define backspace/delete behaviour after a pinyin selection.
- Backspace should step back through unresolved suffix first and reopen earlier segment choice cleanly when needed.
- Ensure touch delete, hardware delete, BACK-as-delete, and focus-out teardown converge on the same composition-state helpers.
- Do not let any path clear one row while leaving another row stale.

Files:

- `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/input/CandidatesView.kt`
- Possibly `app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/CommonKeyActionListener.kt`

Verify:

- After `2496 -> select ai`, one delete/back action produces a coherent prior composition state.
- Full delete clears top row, pinyin row, and Hanzi row together.
- Touch-away and return do not resurrect stale composition or stale candidate surfaces.

### Step 5 - Keep One Active Pinyin Surface

Purpose: prevent future fixes landing in dead code while the cohesive-state work is in progress.

Change:

- Treat `CandidatesView.kt` as the active pinyin-surface owner.
- Leave `PinyinSelectionBarComponent.kt` out of the implementation path unless a fresh source check proves it is wired.
- Remove it or clearly quarantine it only after the cohesive-state flow is stable.

Files:

- `app/src/main/java/org/fcitx/fcitx5/android/input/t9/PinyinSelectionBarComponent.kt`

Verify:

- Build passes.
- The active pinyin row still appears through `CandidatesView`.

## Phase B - Follow-Up Work After State Is Stable

Do these only after Phase A passes manual verification:

1. Revisit `use_t9_keyboard_layout` naming and preference semantics if needed.
2. Recheck whether English STAR state and multi-tap display still need cleanup.
3. Recheck number-mode long press, long-input pinyin truncation, and Chinese-mode STAR punctuation toggle.
4. Consider extracting a `T9InputController` only after behaviour is stable.

## Manual Verification Checklist

1. `24`
   Expected: top row and pinyin row agree on the same reading.
2. `2496 -> select ai`
   Expected: top row shows full reading like `ai wo`, pinyin row advances past `ai`, Hanzi row stays aligned, app text field still has no final commit.
3. After that, select a Hanzi candidate.
   Expected: only Hanzi is committed.
4. After `2496 -> select ai`, press delete/back once.
   Expected: composition backs out coherently without mixed rows.
5. Repeat the same flow with touch and physical navigation.
   Expected: both paths call the same pinyin-selection action and converge to the same rendered state.

## Intentionally Out Of Scope

- No new haptic or sound feedback layer.
- No visual redesign to match yuyansdk.
- No cloud input.
- No frequency-learning beyond Rime.
- No new C++ Rime processor.
- No swipe gestures for candidate rows.
