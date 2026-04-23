# Implementation Plan - T9 Composition Model First

Audience: another coding agent. Read `analyse.md` first. The current source already has the composition model, RecyclerView pinyin row, chip adapter, top/bottom focus state, model-only pinyin selection, client-side Hanzi filtering, and delete-to-reopen behaviour. Do not restart from older plans. The next work should finish focus-out/touch-away clearing and manual verification for the coherent T9 composition flow.

## Progress Snapshot (kept in sync with source)

- Phase 0 (Step 0): baseline confirmed. Active paths listed below are present in source.
- Phase A Step 1: DONE. `T9CompositionModel.kt` introduces `T9ResolvedSegment`, `T9PendingSelection`, `T9CompositionModel`, `T9PresentationState`. Deviation: `T9PresentationState` only carries `topReading` + `pinyinOptions` (with derived `pinyinRowVisible`); candidate focus is still rendered from service state via `updateT9FocusIndicator()` rather than a snapshot field.
- Phase A Step 2: DONE (re-implemented as "Option B", see below). `selectT9Pinyin` updates the Kotlin model only - `resolvedSegments` += new `T9ResolvedSegment`, suffix stored in `unresolvedDigits`, `pendingSelection` marker recorded. **No Rime key replay.** An earlier design (backspace + pinyin letters + remaining digits into Rime) leaked text like `ai96` into the app field and sometimes crashed, because the t9 schema did not reliably consume the mixed letter+digit sequence. Current design leaves Rime's raw-digit composition untouched; the "selected pinyin" view is a Kotlin-side overlay. `commitT9PinyinSelection` now also calls `candidatesView?.refreshT9Ui()` because no fcitx event fires and the UI would otherwise not redraw.
- Phase A Step 3: DONE (simplified). `syncT9CompositionWithInputPanel` no longer tries to parse Rime's preedit for `unresolvedDigits`. Rime's t9 schema has `isDisplayOriginalPreedit: false`, so its preedit is a display form (e.g. `a` for `2`, `ai'96` for `2496`) - parsing it was overwriting the tracker with the trailing digit run and breaking pinyin chips for fresh input. The tracker (built from user key events in `forwardKeyEvent`) is now the authoritative source for `unresolvedDigits`; sync only detects empty Rime preedit (to clear) and records `rawPreedit` as a debug fallback.
- Phase B Steps 4-5: DONE. `getT9PresentationState` builds one snapshot; `CandidatesView.updateUi` renders top row and pinyin row from it and `evaluateVisibility` takes the snapshot into account. `truncateCommentByKeyCount` was removed; comment-based truncation is no longer used for the top row. Under Option B, when `resolvedSegments` is non-empty the top reading prefers the model build (`resolved pinyin + first pinyin for unresolvedDigits`, e.g. `ai wo`) over the first Hanzi candidate's comment (which would otherwise show something like `bi wo`). When `resolvedSegments` is empty the comment path is preferred.
- Phase B addendum - Hanzi filtering: DONE. `FcitxInputMethodService.filterPagedByResolvedPinyin` narrows the candidate page (client-side) to entries whose comment starts with the resolved-pinyin prefix. `CandidatesView.updateUi` calls it before rendering, and the candidate click handler translates the filtered index back to the original fcitx index via `t9ShownPaged` so taps still select the intended character. Trade-off: this is a client-side trim of the current Rime page, so if Rime's page contains few `ai*` entries the row can look sparse; in that case the filter falls back to the unfiltered page rather than showing an empty row. A deeper fix (making Rime itself narrow to `ai*`) would require pushing letters back into Rime, which reintroduces the text-leak that drove Option B, so it is deferred.
- Phase C Step 6: DONE (with design change). Any delete while `resolvedSegments` is non-empty first reopens the last resolved segment back into the unresolved suffix (prepends its `sourceDigits` to the current `unresolvedDigits`), no Rime keys replayed. The "only reopen when unresolved is empty" variant was replaced by the user-requested "selection is the most recent decision, so delete undoes it first". Wired in three places: `handleVirtualT9Backspace` (on-screen delete, returns `Boolean`), `onKeyDown` intercept for `KEYCODE_DEL`/mapped `KEYCODE_BACK` (consumes the key + UP via `t9ConsumedNavigationKeyUp`), and `CommonKeyActionListener.SymAction` (skips `sendKey` on consumed press). Each reopen path also calls `candidatesView?.refreshT9Ui()` because no fcitx event fires.
- Step removed: `handleFcitxEvent.CommitStringEvent` letter intercept is gone. Under Option B we never push letters into Rime, so Rime cannot emit a letter commit; the intercept became dead code that could mask unrelated bugs.
- Phase C Step 7 (focus-out / touch-away coherent clear): NOT STARTED.
- Phase D: NOT STARTED.

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
  - `T9PresentationState(topReading: FormattedText?, pinyinOptions: List<String>)` with `pinyinRowVisible` as a derived property. Candidate focus currently stays on the service and is read by `CandidatesView.updateT9FocusIndicator()`; fold it into the snapshot only if a later step actually needs it.
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
- In `selectT9Pinyin`:
  - compute the matched prefix against `model.unresolvedDigits`
  - record the selected segment in the model as resolved composing state
  - retain the remaining suffix as `model.unresolvedDigits`
  - set an optional pending-selection marker for debugging/state consistency
- Do **not** send backspaces, pinyin letters, or remaining digits back into fcitx/Rime. That replay path leaked mixed text into the app field and could crash.
- Keep Rime composing the original digit sequence; use the Kotlin model as the overlay for selected-pinyin UI state.
- Refresh `CandidatesView` after model-only selection because no fcitx event is guaranteed.

Files:

- `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`
- New model file from Step 1
- `app/src/main/java/org/fcitx/fcitx5/android/input/t9/T9PinyinUtils.kt`

Verify:

- Type `2496`, tap `ai`.
- App text field receives no final text.
- Logs/model show resolved `ai/24` and unresolved `96`.
- Pinyin row options are based on `96`.
- Rime receives no replayed pinyin letters for the selection.
- Physical OK on highlighted `ai` produces the same model state as touch.

### Step 3 - Sync Without Parsing Rime Display Preedit

Purpose: keep Kotlin model and Rime state aligned without misreading Rime's display preedit as raw user input.

Change:

- Treat `T9CompositionTracker` (updated from user key events in `forwardKeyEvent`) as the authoritative source for raw digits.
- Do not rebuild `unresolvedDigits` from `data.preedit`; the t9 schema exposes a display form such as `a` or `ai'96`, not the original key sequence.
- If preedit is empty, clear model/tracker state.
- If preedit is non-empty, record it as `rawPreedit` only for display/debug fallback.

Files:

- `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`
- New model file from Step 1

Verify:

- Plain digit entry such as `2496` keeps unresolved digits as `2496` even if Rime display preedit is `ai'96`.
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
  - If resolved segments exist, delete first reopens the last selected segment as source digits at the front of `unresolvedDigits`.
  - If no resolved segments exist, fall through to normal digit-suffix deletion/backspace behaviour.
  - If everything is empty, fall through to normal delete.
- Do not replay keys into Rime while reopening; under Option B, Rime is already composing the full digit sequence.
- Ensure hardware DEL/BACK, on-screen delete, mapped BACK-to-DEL, and any virtual key path use the same helper.
- Consume the matching key-up after physical DEL/BACK is handled as a model-only reopen.

Files:

- `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`
- New model file from Step 1
- Possibly `app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/CommonKeyActionListener.kt`

Verify:

- `2496 -> select ai -> delete`: state backs out coherently with no mixed rows.
- Touch delete and physical delete first restore `2496`/fresh pinyin options before later deletes shrink the digits.
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
