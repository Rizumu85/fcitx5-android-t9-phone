# Implementation Plan - T9 Engine-Backed Pinyin Selection

Audience: another coding agent. Read `analysis.md` and `design.md` first. The current source already has the composition model, RecyclerView pinyin row, chip adapter, top/bottom focus state, delete-to-reopen behaviour, coded focus-out/touch-away clearing, six-digit truncation removal, stale component removal, clarified T9 input-mode naming/gates, and a native bridge that can read/replace the current Rime input through fcitx-rime. Do not restart from older plans. The active pivot is engine-backed pinyin selection: selected T9 digit spans are replaced inside Rime with `pinyin'` so unusual combinations and short choices such as `g`, `h`, or `l` do not depend on fragile client-side candidate-comment filtering.

## Progress Snapshot (kept in sync with source)

- Phase 0 (Step 0): baseline confirmed. Active paths listed below are present in source.
- Bridge foundation: CODED. `plugin/rime/.../rime_public.h` now exposes `getInput` + `replaceInput`; `RimeState` implements them via librime `get_input` / `set_input` / `set_caret_pos`; `native-lib.cpp`, `FcitxAPI.kt`, and `Fcitx.kt` expose matching app-side calls. T9 pinyin selection/delete now uses this bridge opportunistically.
- Engine-backed pinyin selection pivot: CODED, NEEDS DEVICE VERIFICATION. `T9ResolvedSegment` now tracks whether a selected pinyin segment has been written into Rime. `selectT9Pinyin` still updates the Kotlin model immediately, then queues `getRimeInput()` + `replaceRimeInput(...)` to replace the selected raw digit span with `pinyin'`. While the replacement is pending, client-side Hanzi filtering is suppressed; after success, engine-backed selections use Rime candidates as authoritative. If replacement fails, the pending marker clears and the previous client-filter fallback can recover.
- Engine-backed delete/reopen: CODED, NEEDS DEVICE VERIFICATION. Reopening an engine-backed segment replaces the matching `pinyin'` span in Rime input with the segment's original digits. If that reverse replacement fails, the service resets Rime, replays raw T9 digits, and marks remaining resolved segments as fallback-only.
- Phase A Step 1: DONE. `T9CompositionModel.kt` introduces `T9ResolvedSegment`, `T9PendingSelection`, `T9CompositionModel`, `T9PresentationState`. Deviation: `T9PresentationState` only carries `topReading` + `pinyinOptions` (with derived `pinyinRowVisible`); candidate focus is still rendered from service state via `updateT9FocusIndicator()` rather than a snapshot field.
- Phase A Step 2: SUPERSEDED BY ENGINE-BACKED PIVOT. The earlier Option B path updated only the Kotlin model and left Rime's raw-digit composition untouched because key replay leaked text such as `ai96`. The current path still updates the Kotlin model immediately, but then uses the bridge to replace selected digits inside Rime with `pinyin'`; the model-only/client-filter path remains only as fallback when replacement fails.
- Phase A Step 3: DONE (simplified). `syncT9CompositionWithInputPanel` no longer tries to parse Rime's preedit for `unresolvedDigits`. Rime's t9 schema has `isDisplayOriginalPreedit: false`, so its preedit is a display form (e.g. `a` for `2`, `ai'96` for `2496`) - parsing it was overwriting the tracker with the trailing digit run and breaking pinyin chips for fresh input. The tracker (built from user key events in `forwardKeyEvent`) is now the authoritative source for `unresolvedDigits`; sync only detects empty Rime preedit (to clear) and records `rawPreedit` as a debug fallback.
- Phase B Steps 4-5: DONE. `getT9PresentationState` builds one snapshot; `CandidatesView.updateUi` renders top row and pinyin row from it and `evaluateVisibility` takes the snapshot into account. `truncateCommentByKeyCount` was removed; comment-based truncation is no longer used for the top row. When `resolvedSegments` is non-empty the top reading prefers the model build (`resolved pinyin + first pinyin for unresolvedDigits`, e.g. `ai wo`) over the first Hanzi candidate's comment (which would otherwise show something like `bi wo`). When `resolvedSegments` is empty the comment path is preferred.
- Phase B addendum - Hanzi filtering: FALLBACK PATH. `CandidatesView.updateUi` can still filter T9 Hanzi candidates before rendering and owns the visible Hanzi cursor for fallback-filtered pages. Touch selection and physical OK translate the visible filtered candidate back to the original fcitx index before selecting, so filtered selections do not commit raw index 0. Engine-backed selections suppress this selected-pinyin filtering because Rime candidates are already narrowed. T9 Hanzi rows still ask fcitx for a wider bulk candidate slice and paginate locally by `AppPrefs.candidates.t9HanziCharacterBudget`. The first budget consistency pass is coded: before async bulk results arrive, locally budgeted chunks of the current Rime page can now be paged with Up/Down before falling through to fcitx page changes.
- First-load empty-preedit guard: DONE, NEEDS DEVICE VERIFICATION. `syncT9CompositionWithInputPanel` now only clears the optimistic T9 tracker/model on empty preedit after Rime has previously reported a non-empty preedit, preventing startup/stale empty events from dropping the first digit (`24` showing only `ghi` in the pinyin row).
- T9 preedit/delete display: CODED, NEEDS DEVICE VERIFICATION. The Chinese T9 top preedit row now prefers the local digit tracker when no pinyin segment is selected, and the tracker display greedily consumes the full digit sequence (`2496` -> `ai wo`, delete -> `ai w` -> `ai`) instead of showing only the first pinyin prefix. Physical and virtual delete refresh the row immediately after tracker updates while Rime candidate events continue to refresh pinyin/Hanzi candidates.
- T9 pinyin-row animation: CODED, NEEDS DEVICE VERIFICATION. The pinyin candidate row now animates in/out by revealing/collapsing row height from the Hanzi bubble over 180ms instead of scaling the whole row. The row clips to its own animated bounds so chips are revealed by the opening bubble itself rather than by a separate text animation. Before the first reveal it synchronizes its width to the measured Hanzi row and, if needed, waits one posted pass before animating so the row does not appear at width 0. While the async bulk candidate slice is still loading, unfiltered T9 also uses a synchronous local budgeted page from the current raw page so the first visible layout is closer to the later budgeted layout. The chips are bottom-anchored and only become visible near the end of the reveal so partially clipped content should not flash during first appearance. Hide keeps the old chips until the collapse finishes, then clears them; hard transient clears still skip animation.
- T9 pinyin-chip highlight animation: CODED, NEEDS DEVICE VERIFICATION. The active pinyin chip now gets its own 180ms highlight-alpha + scale transition, using overshoot on activation and accel/decel on deactivation. Recycled chip views snap to the new active state before animating so focus movement should feel like the Hanzi bubble instead of a hard background swap.
- T9 Hanzi-highlight animation: CODED, NEEDS DEVICE VERIFICATION. The active Hanzi candidate bubble now eases in/out with a 190ms highlight-alpha + scale transition when focus moves, using a stronger activation overshoot. Candidate text updates themselves remain instant. New visible Hanzi rows initialize focus directly on the first candidate, and recycled candidate views now snap to the new candidate's active/inactive state before animation so old 4th/5th-item highlight state does not flash. The floating Hanzi row also keeps a small horizontal overflow padding with clipping disabled so the first highlighted candidate's left rounded corners are not clipped.
- English T9 candidate budgeting: CODED, NEEDS DEVICE VERIFICATION. In English T9 mode, visible word count is now limited in `HorizontalCandidateComponent`, which is the primary rendering path for English suggestions. `T9CandidateBudget` treats each English word as 2 budget units, so a budget of `10` should yield about `5` English words in the horizontal strip. `CandidatesView` uses the same helper if that path is reused.
- Emoji T9 budget accounting: CODED, NEEDS DEVICE VERIFICATION. `T9CandidateBudget` treats emoji clusters, including common ZWJ sequences, keycaps, skin-tone variants, and flag pairs, as 2 budget units instead of raw code point count. Regular Hanzi/text continues to use displayed text code point count.
- Phase C Step 6: DONE (with design change). Any delete while `resolvedSegments` is non-empty first reopens the last resolved segment back into the unresolved suffix (prepends its `sourceDigits` to the current `unresolvedDigits`), no Rime keys replayed. The "only reopen when unresolved is empty" variant was replaced by the user-requested "selection is the most recent decision, so delete undoes it first". Wired in three places: `handleVirtualT9Backspace` (on-screen delete, returns `Boolean`), `onKeyDown` intercept for `KEYCODE_DEL`/mapped `KEYCODE_BACK` (consumes the key + UP via `t9ConsumedNavigationKeyUp`), and `CommonKeyActionListener.SymAction` (skips `sendKey` on consumed press). Each reopen path also calls `candidatesView?.refreshT9Ui()` because no fcitx event fires.
- Step removed: `handleFcitxEvent.CommitStringEvent` letter intercept is gone. The engine-backed pivot replaces Rime input directly and still does not push pinyin letters as key events, so the intercept remains dead code that could mask unrelated commit bugs.
- Phase C Step 7 (focus-out / touch-away coherent clear): CODED, NEEDS DEVICE VERIFICATION. `clearChineseT9CompositionFromEditorTap()` now treats `T9CompositionModel` as clear-worthy state, not just the raw tracker/composing span. Cursor updates with an empty composing span in T9 Chinese mode now clear the Kotlin model plus visible transient rows immediately before asking fcitx to reset, preventing stale candidate/pinyin rows from surviving a tap-away.
- Phase D Step 1 (remove six-digit truncation): CODED, NEEDS DEVICE VERIFICATION. `T9PinyinUtils.t9KeyToPinyin` and `matchedPrefixLength` now use the full filtered digit sequence instead of `take(6)`.
- Phase D Step 2 (remove stale component): DONE. Unused `app/src/main/java/org/fcitx/fcitx5/android/input/t9/PinyinSelectionBarComponent.kt` deleted; active pinyin row remains `CandidatesView` + `T9PinyinChipAdapter`.
- Phase D Step 3 (T9 naming/gating): DONE. Stored key `use_t9_keyboard_layout` remains unchanged for compatibility, but user-facing strings now say "T9 input mode" and local feature-gate variables in service/candidate/input-device code use `t9InputModeEnabled`. Actual layout-selection code still uses layout naming.
- Phase D remaining items: NOT STARTED.

## Ground Rules

1. Work on `CandidatesView.kt` for the active pinyin/top/candidate UI. `PinyinSelectionBarComponent.kt` is not the active path unless a fresh source check proves otherwise.
2. Keep changes surgical. Do not do a broad service refactor while behaviour is still unstable.
3. Treat the current `use_t9_keyboard_layout` stored preference as semantic "T9 mode enabled" unless a migration is explicitly added later.
4. Touch chip selection and physical OK selection must call the same pinyin-selection action.
5. Do not add a new Rime processor for this pivot; use the existing fcitx-rime bridge unless a later plan explicitly supersedes this constraint.
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
- Confirm `PinyinSelectionBarComponent.kt` is absent/removed and the active pinyin row is `CandidatesView` + `T9PinyinChipAdapter`.
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
- Do **not** replay backspace/pinyin/digit key events into fcitx/Rime. That replay path leaked mixed text into the app field and could crash.
- Use `getRimeInput()` / `replaceRimeInput(...)` to replace the selected digit span with `pinyin'` inside Rime. Mark the segment engine-backed only after that replacement succeeds.
- Suppress selected-pinyin client filtering while replacement is pending and after all resolved segments are engine-backed. If replacement fails, clear the pending marker and let the fallback client filter recover.
- Refresh `CandidatesView` after selection because the model changes immediately and bridge completion is asynchronous.

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
- For engine-backed segments, reopen by replacing the matching `pinyin'` span in Rime input with the segment's source digits. If reverse replacement fails, reset and replay raw digits as fallback.
- Ensure hardware DEL/BACK, on-screen delete, mapped BACK-to-DEL, and any virtual key path use the same helper.
- Consume the matching key-up after physical DEL/BACK is handled as a T9 reopen.

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

- Route editor tap/focus-out through clear helpers that clear the model, tracker, pending selection, focus state, adapter list, and transient candidate/input view state together.
- Include `T9CompositionModel` in the "is there anything to clear?" check so fully resolved selections are not missed when the raw tracker is empty.
- When a cursor update reports an empty composing span in T9 Chinese mode, clear the Kotlin/UI transient state immediately before resetting fcitx.
- Ensure Rime is focused out or reset consistently so the next typing session does not keep old composition.

Files:

- `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/input/CandidatesView.kt`

Verify:

- Type a T9 sequence, tap away, then return and type again.
- No previous composition remains in top row, pinyin row, Hanzi row, or any default candidate surface.
- Static check: `git diff --check` passes.
- Build check: not run in this environment because no Java runtime is installed.

## Phase D - Cleanup After Core Flow Works

Prefer to do these after Phases A-C pass manual verification. Steps 1-3 are already coded as low-risk cleanup.

1. ~~Remove six-digit truncation from `T9PinyinUtils.t9KeyToPinyin` and `matchedPrefixLength`.~~ (coded, needs device verification for long sequences)
2. ~~Quarantine or remove stale `PinyinSelectionBarComponent.kt`.~~ (done: removed unused file)
3. ~~Recheck `use_t9_keyboard_layout` local naming and semantic gates.~~ (done: no migration; strings and local feature-gate names clarified)
4. Audit T9 Hanzi candidate budgeting consistency across raw pages, bulk pages, fallback-filtered pages, and engine-backed pages; current-page local paging plus shared English/emoji/Hanzi cost rules are coded, but the configured value (for example `10`) still needs device verification.
5. Recheck English STAR and multi-tap display.
6. Recheck number-mode long press and Chinese-mode STAR punctuation toggle.
7. Consider extracting `T9InputController` only after behaviour is stable.

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
8. Type a long sequence (7+ digits), select pinyin, then continue/delete.
   Expected: pinyin options and matched prefix length are computed from the full sequence (no silent 6-digit cutoff).
9. `2496 -> select ai -> select yo -> choose 哎哟 with OK`.
   Expected: `哎哟` commits, not the unfiltered first candidate such as `比我`.
10. `2496 -> select ai`, keep focus in Hanzi row, press Right.
   Expected: the visible Hanzi highlight moves on the first Right press.
11. With focus in Hanzi row, press Down.
   Expected: the next Hanzi candidate page is requested.
12. `2496 -> select ci` when the current page has no `ci*` matches.
   Expected: unrelated candidates are not shown; engine-backed Rime narrowing should avoid empty rows caused by client-only filtering; Down/Up should page locally and selecting a shown Hanzi candidate should commit it without crashing.
13. Fresh app/IME start, type `24`.
   Expected: pinyin row includes `ai`; it should not lose the first digit and show only `ghi`.
14. Select an impossible pinyin pair such as `yo ci`.
   Expected: if no full `yo ci` Hanzi candidates exist, show `yo` Hanzi candidates first; after selecting one, continue with the remaining `ci` segment.
15. `2496 -> select ci -> choose one-character ci Hanzi`.
   Expected: committed `ci` Hanzi stays in the app, stale Hanzi candidates clear during replay, then the remaining `96` rebuilds normal pinyin/Hanzi candidates in `CandidatesView`, not in the Kawaii bar.
16. `2496`, press delete repeatedly without selecting pinyin.
   Expected: the top pinyin input row updates immediately with each delete (`ai wo` -> `ai w` -> `ai` -> ...), while pinyin and Hanzi candidates continue updating from Rime.
17. Type until the pinyin candidate row appears, then delete until it disappears.
   Expected: the pinyin candidate row grows out from the Hanzi candidate bubble and collapses back into it without popping; on the first reveal the chip row should already have its full final row height while only the host bubble expands, so the first chips should not be blank/clipped because they were measured into a tiny strip. The chips should no longer fade; only the host reveal and chip offset motion remain.
18. Move pinyin focus left/right while the pinyin row is active.
   Expected: the active pinyin chip gets the same kind of eased highlight motion as the Hanzi bubble instead of snapping.
19. Change Hanzi candidates by typing, filtering, or paging.
   Expected: candidate text changes remain immediate; moving the highlight left/right gives the active bubble a small eased highlight transition.
20. English T9 candidate row with default budget `10`.
   Expected: about `5` English word candidates are shown in the horizontal candidate strip, because each English word costs 2 budget units.
21. T9 Hanzi candidate budget setting `10`.
   Expected: raw, bulk, fallback-filtered, and engine-backed Hanzi rows use the same cost rules: English word 2, emoji cluster 2, regular Hanzi/text by code point count.

## Intentionally Out Of Scope

- No new haptic or sound feedback layer.
- No visual redesign to match yuyansdk.
- No cloud input.
- No frequency-learning beyond Rime.
- No new C++ Rime processor.
- No swipe gestures for candidate rows.
