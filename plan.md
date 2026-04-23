# Implementation Plan - T9 Composition Model First

Audience: another coding agent. Read `analyse.md` first. The current source already has partial fixes: RecyclerView pinyin row, chip adapter, top/bottom focus state, and `commitT9PinyinSelection`. Do not restart from older plans. The next work should make pinyin selection a coherent, reversible composition-state transition.

## Ground Rules

1. Work on `CandidatesView.kt` for the active pinyin/top/candidate UI. `PinyinSelectionBarComponent.kt` is not the active path unless a fresh source check proves otherwise.
2. Keep changes surgical. Do not do a broad service refactor while behaviour is still unstable.
3. Treat the current `use_t9_keyboard_layout` stored preference as semantic "T9 mode enabled" unless a migration is explicitly added later.
4. Touch chip selection and physical OK selection must call the same pinyin-selection action.
5. Do not add a new Rime processor or move T9 logic to C++.
6. Use correct T9 examples. `ai` is `2 4`, not `2 3`.
7. Every step needs manual on-device verification before moving to the next step.

## Confirmed Product Rules

- `2496 -> select ai` must not commit final text to the app field.
- After selecting `ai`, the composing state should be resolved prefix `ai` plus unresolved suffix `96`.
- The top row may show full reading such as `ai wo`, using the current highlighted Hanzi candidate/comment when needed.
- The pinyin row should show options for `96`, not continue offering `ai`.
- Hanzi candidate selection commits Hanzi only.
- Delete/back should let the user undo or back out cleanly without mixed stale rows.

## Phase 0 - Reconfirm Current Active Paths

### Step 0 - Source Check And Baseline Build

Purpose: avoid editing stale code and establish the current compile state.

Change:

- No behaviour change.
- Confirm these active paths:
  - `CandidatesView.updateUi`
  - `CandidatesView.updatePinyinBar`
  - `T9PinyinChipAdapter`
  - `FcitxInputMethodService.syncT9CompositionWithInputPanel`
  - `FcitxInputMethodService.selectT9Pinyin`
  - `FcitxInputMethodService.commitT9PinyinSelection`
  - `T9CompositionTracker`
- Confirm `PinyinSelectionBarComponent.kt` is still unused.
- Run the closest available compile/build check.

Files:

- No required edits.

Verify:

- Build passes or the exact existing failure is documented.
- Active UI and service paths are confirmed before Step 1 starts.

## Phase A - Composition Model

### Step 1 - Introduce A Small T9 Composition Model

Purpose: represent selected pinyin prefix and unresolved digit suffix explicitly.

Change:

- Add small data classes in `app/src/main/java/org/fcitx/fcitx5/android/input/t9/`, for example:
  - `T9ResolvedSegment(pinyin: String, sourceDigits: String)`
  - `T9CompositionModel(resolvedSegments: List<T9ResolvedSegment>, unresolvedDigits: String)`
  - `T9PresentationState(topReading: FormattedText?, pinyinOptions: List<String>, pinyinRowVisible: Boolean, focus: T9CandidateFocus)`
- Keep this small. Do not build a full controller yet.
- Add helpers to:
  - initialize/update unresolved digits from raw digit preedit
  - apply a selected pinyin to the prefix of `unresolvedDigits`
  - clear all model state
  - expose full reading prefix and current unresolved digits
- Keep `T9CompositionTracker` only if it still helps with raw digit entry; do not let it be the only UI truth after pinyin selection.

Files:

- New file such as `app/src/main/java/org/fcitx/fcitx5/android/input/t9/T9CompositionModel.kt`
- Possibly `T9CompositionTracker.kt` if it needs small helpers

Verify:

- Unit-test manually through logs if no test harness exists:
  - raw `2496` -> resolved `[]`, unresolved `2496`
  - select `ai` -> resolved `[ai/24]`, unresolved `96`
  - clear -> resolved `[]`, unresolved empty

### Step 2 - Make `selectT9Pinyin` Transactional Against The Model

Purpose: stop losing the selected pinyin prefix as soon as a chip is selected.

Change:

- Keep `commitT9PinyinSelection(pinyin)` as the shared touch/hardware entry point.
- In `selectT9Pinyin`, before sending keys to fcitx:
  - compute the matched prefix against `model.unresolvedDigits`
  - record the selected segment in the model as resolved composing state
  - retain the remaining suffix as `model.unresolvedDigits`
  - set an optional pending-selection marker so the next fcitx update can confirm or correct state
- Continue sending backspaces + selected pinyin + remaining digits to fcitx if that is still the Rime-compatible transaction.
- The visible final state should be overwritten by the next authoritative fcitx update, but the optimistic state must contain both selected prefix and remaining suffix.
- Do not write selected pinyin to the app text field as final committed text.

Files:

- `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`
- New model file from Step 1
- `app/src/main/java/org/fcitx/fcitx5/android/input/t9/T9PinyinUtils.kt`

Verify:

- Type `2496`, tap `ai`.
- App text field receives no final text.
- Logs/model show resolved `ai/24` and unresolved `96`.
- Pinyin row options are based on `96`.
- Physical OK on highlighted `ai` produces the same model state as touch.

### Step 3 - Parse Or Reconcile Mixed Rime Preedit

Purpose: keep Kotlin model and Rime state aligned after Rime receives selected pinyin plus remaining digits.

Change:

- Update `syncT9CompositionWithInputPanel` so it handles more than all-digit preedit.
- For all-digit preedit, model should become no resolved segments plus raw unresolved digits.
- For mixed preedit such as `ai96`, keep or reconstruct resolved prefix `ai` and unresolved suffix `96`.
- If reconstruction from preedit alone is ambiguous, use the pending-selection marker from Step 2 and clear that marker after the next matching fcitx update.
- If preedit is empty, clear model, tracker, focus, and pinyin adapter state.

Files:

- `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`
- New model file from Step 1

Verify:

- `2496 -> select ai` survives the next input-panel update with resolved `ai` and unresolved `96`.
- Full delete or commit empties the model.
- Touch-away then return does not resurrect old model/preedit/candidate state.

## Phase B - One Presentation Snapshot

### Step 4 - Build One Snapshot Per Render

Purpose: stop `CandidatesView.updateUi()` from mixing tracker, raw preedit, and candidate-comment strategies independently.

Change:

- Add a builder method that takes the latest input-panel data and paged candidates and returns one `T9PresentationState`.
- The builder should decide:
  - `topReading`: resolved prefix plus unresolved suffix reading, using current candidate comment when useful
  - `pinyinOptions`: `T9PinyinUtils.t9KeyToPinyin(model.unresolvedDigits)`
  - `pinyinRowVisible`
  - current focus row
- Do not let `CandidatesView` call `getT9PreeditDisplay`, `getT9PinyinCandidates`, and candidate-comment truncation separately for the same render.

Files:

- `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/input/CandidatesView.kt`
- New model file from Step 1

Verify:

- One log line or debug dump can describe the full presentation state for a render.
- `24` produces top display and pinyin row from the same snapshot.
- `2496 -> select ai` produces top display and pinyin row from the same snapshot.

### Step 5 - Render CandidatesView From The Snapshot

Purpose: make the UI rows agree in every update pass.

Change:

- In `CandidatesView.updateUi`, obtain one snapshot after syncing model with input panel.
- Render:
  - top row from `snapshot.topReading`
  - pinyin row from `snapshot.pinyinOptions`
  - pinyin row visibility from `snapshot.pinyinRowVisible`
  - focus indicator from `snapshot.focus`
  - bottom Hanzi row from existing paged candidates
- Remove or bypass the old branch that independently chooses candidate-comment truncation, tracker preedit, and raw fallback.
- When `pinyinOptions` is empty, submit an empty list to the adapter before hiding the row.

Files:

- `app/src/main/java/org/fcitx/fcitx5/android/input/CandidatesView.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`

Verify:

- `24`: rows agree.
- `2496 -> select ai`: top row shows a full reading such as `ai wo`; pinyin row advances to `96` choices; Hanzi row stays aligned.
- Clearing composition clears top row, pinyin row, and Hanzi row together.
- No stale pinyin chips reappear after on-screen delete.

## Phase C - Backspace, Undo, And Focus-Out

### Step 6 - Define Delete And Undo Transitions

Purpose: make pinyin selection reversible and prevent mixed stale states.

Change:

- Define delete behaviour against the model:
  - If `unresolvedDigits` is non-empty, delete from the suffix first.
  - If suffix is empty and resolved segments exist, delete/reopen the last resolved segment as digits or send the matching Rime backspaces needed to undo it.
  - If everything is empty, fall through to normal delete.
- Ensure hardware DEL/BACK, on-screen delete, mapped BACK-to-DEL, and any virtual key path use the same helper.
- Remove small nearby duplicate cleanup such as duplicated `t9ConsumedNavigationKeyUp = null` if touched.

Files:

- `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`
- New model file from Step 1
- Possibly `app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/CommonKeyActionListener.kt`

Verify:

- `2496 -> select ai -> delete`: state backs out coherently with no mixed rows.
- Full delete clears model, top row, pinyin row, and Hanzi row.
- Touch delete and physical delete produce the same result.

### Step 7 - Make Focus-Out And Touch-Away Clear Coherently

Purpose: prevent default-looking candidate bars or stale Rime composition from surviving after the user taps away.

Change:

- Route editor tap/focus-out through the same clear helper used by full delete/commit.
- Clear model, tracker, pending selection, focus state, adapter list, and transient candidate view state together.
- Ensure Rime is focused out or reset consistently so the next typing session does not keep old composition.

Files:

- `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/input/CandidatesView.kt`

Verify:

- Type a T9 sequence, tap away, then return and type again.
- No previous composition remains in top row, pinyin row, Hanzi row, or any default candidate surface.

## Phase D - Cleanup After Core Flow Works

Do these only after Phases A-C pass manual verification:

1. Remove six-digit truncation from `T9PinyinUtils.t9KeyToPinyin` and `matchedPrefixLength`.
2. Quarantine or remove stale `PinyinSelectionBarComponent.kt`.
3. Recheck `use_t9_keyboard_layout` local naming and semantic gates.
4. Recheck English STAR and multi-tap display.
5. Recheck number-mode long press and Chinese-mode STAR punctuation toggle.
6. Consider extracting `T9InputController` only after behaviour is stable.

## Manual Verification Checklist

1. `24`
   Expected: top row and pinyin row agree on the same composition.
2. `2496 -> select ai`
   Expected: no final app text commit; top row shows full reading like `ai wo`; pinyin row advances to `96`; Hanzi row stays aligned.
3. Select a Hanzi candidate after that.
   Expected: only Hanzi is committed.
4. `2496 -> select ai -> delete/back`
   Expected: composition backs out coherently without stale or mixed rows.
5. Repeat with touch chip selection and physical UP/OK selection.
   Expected: both paths call the same pinyin-selection action and converge to the same snapshot.
6. Type, fully delete with the on-screen delete button, then type again.
   Expected: old pinyin candidates never reappear.
7. Type, tap away, return, type again.
   Expected: old Rime composition and default-looking candidate bars do not return.

## Intentionally Out Of Scope

- No new haptic or sound feedback layer.
- No visual redesign to match yuyansdk.
- No cloud input.
- No frequency-learning beyond Rime.
- No new C++ Rime processor.
- No swipe gestures for candidate rows.
