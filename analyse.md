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
- Removed stale duplicate component: `app/src/main/java/org/fcitx/fcitx5/android/input/t9/PinyinSelectionBarComponent.kt`.

Important service methods already exist:

- `syncT9CompositionWithInputPanel`
- `getT9PinyinCandidates`
- `getT9PreeditDisplay`
- `getT9PresentationState`
- `getT9ResolvedPinyinFilterPrefixes`
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
- When resolved pinyin exists, `CandidatesView.updateUi()` filters by selected-pinyin prefixes, can bulk-fetch a wider candidate slice, and stores visible-to-fcitx index mappings so touch/OK selection commits the shown Hanzi candidate.

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
| C5 | High | CODED_NEEDS_DEVICE_VERIFY | Delete/back/focus-out paths | Delete/back now reopens the latest resolved segment before forwarding normal delete. Focus-out/touch-away now clears model/tracker/UI transient state on editor tap or cursor-away paths, but still needs on-device verification. |
| C6 | Medium | CODED_NEEDS_DEVICE_VERIFY | `T9PinyinUtils.kt` | `t9KeyToPinyin` and `matchedPrefixLength` now use the full filtered digit sequence (no `take(6)`), but long-sequence behavior still needs device verification. |
| C7 | Medium | RESOLVED | `useT9KeyboardLayout` naming/gating | Stored key remains `use_t9_keyboard_layout` for compatibility, but visible strings and local feature-gate variables now use "T9 input mode" semantics where behaviour is broader than layout selection. |
| C8 | Medium | RESOLVED | `PinyinSelectionBarComponent.kt` | Stale duplicate pinyin-row implementation was removed; active path is `CandidatesView` + `T9PinyinChipAdapter`. |
| C9 | Low | RESOLVED | `onKeyUp` | The duplicate `t9ConsumedNavigationKeyUp = null` cleanup is no longer present in the current source. |
| C10 | Low | OPEN | English mode | English STAR and multi-tap display may still need cleanup, but they are not the main blocker for Chinese T9 coherence. |
| C11 | Medium | CODED_NEEDS_DEVICE_VERIFY | Hanzi row after pinyin selection | `CandidatesView` client-filters bulk Rime candidates by selected pinyin when a prefix is active, and also uses the same bulk slice for unfiltered T9 Hanzi rows. It now tries the longest resolved pinyin prefix first, then progressively shorter leading prefixes (`a b c` -> `a b c`, `a b`, `a`) instead of only `full -> first`, which better approximates the reference implementation's engine-side replacement flow for niche combinations. Prefix matching itself is also no longer only exact-string based: candidate comment syllables can match either the selected pinyin text or the same selected T9 digit sequence via `T9PinyinUtils.pinyinToT9Keys`, which borrows the reference path's key-level thinking without replacing raw digits inside Rime. T9 input mode keeps floating `CandidatesView` active while the on-screen T9 controls remain visible, and Chinese T9 candidate-list updates are suppressed from the Kawaii bar so rebuilt Hanzi candidates do not land in the wrong row. Both filtered and unfiltered T9 Hanzi rows are paged locally by `AppPrefs.candidates.t9HanziCharacterBudget` (default 12, min 4, max 24, text code points). Bulk-sourced visible selections temporarily switch fcitx to bulk candidate mode, call the existing `select(index)`, then restore the current input-device paging mode; this avoids the observed `AbstractMethodError` from dispatching the newer `selectFromAll` through the delegated runtime API. After committing a candidate whose comment exactly equals the selected prefix, the service prepares a local replay state that keeps T9 pinyin input/chips visible, clears stale Hanzi candidates, suppresses the intentional reset's empty-preedit clear, and replays the remaining raw T9 digits so leftover segments rebuild real Rime candidates; phrase candidates with longer comments are not split. Bulk results are posted back onto the view thread before refreshing adapters, fixing the `2496 -> ci` RecyclerView layout crash. |
| C12 | High | CODED_NEEDS_DEVICE_VERIFY | First-load T9 tracker sync | Empty/stale input-panel updates could clear the optimistic tracker before Rime reported non-empty preedit, so fresh `24` could show only `ghi` in the pinyin row. `syncT9CompositionWithInputPanel` now ignores empty preedit clears until a non-empty preedit has been observed. |
| C13 | Medium | CODED_NEEDS_DEVICE_VERIFY | T9 delete display | The top pinyin input row now prefers the local T9 tracker when no segment is selected, and `buildT9PreeditDisplay` greedily consumes the full digit string into pinyin chunks. Delete paths refresh `CandidatesView` immediately after tracker updates so the pinyin input display should shrink in lockstep with pinyin/Hanzi candidates. |
| C14 | Low | CODED_NEEDS_DEVICE_VERIFY | Pinyin row transition | `CandidatesView` now animates the T9 pinyin candidate row by revealing/collapsing the row height from the Hanzi bubble below over 180ms, instead of scaling the whole row. Before the first reveal it synchronizes the row width to the measured Hanzi row, waits one posted layout pass if needed, and uses a synchronous local character-budget page from the current raw candidate page while the async bulk slice is still loading, reducing first-appearance jumps caused by `5-candidate raw page -> budgeted page` swaps. The chips stay bottom-anchored, are bound immediately, and keep a small reveal-linked vertical offset, but no longer fade. The pinyin RecyclerView is pre-measured/pre-laid out at its final size before the first reveal starts. The pinyin list itself is now kept at a fixed full row height inside a non-clipping host, while only the host row height animates. That should better match the visual model of "bubble expands / Hanzi row moves down" and avoid first-frame blank/clipped chips caused by measuring the RecyclerView into the host's tiny animated height. Hide keeps the outgoing chips collapsing with the row, while hard transient clears still skip animation. |
| C14b | Low | CODED_NEEDS_DEVICE_VERIFY | Pinyin chip highlight transition | `T9PinyinChipAdapter` now animates the active pinyin chip itself with a 180ms highlight-alpha + scale transition, using overshoot on activation and accel/decel on deactivation. Recycled chips snap to their new active state before animating, matching the Hanzi bubble behavior more closely. |
| C15 | Low | CODED_NEEDS_DEVICE_VERIFY | Hanzi highlight transition | `LabeledCandidateItemUi` now animates the active Hanzi bubble itself with a 190ms highlight-alpha + scale transition when focus changes, using a stronger activation overshoot. Candidate text updates remain immediate. `CandidatesView` also seeds new visible Hanzi rows on the first candidate, and recycled candidate views now snap to a new candidate's highlight state before animating, so the highlight should no longer flash on an old 4th/5th index before settling. The floating candidate row now keeps a small horizontal overflow padding with clipping disabled so the first highlighted candidate's left rounded corners are not cut off by the row bounds. |
| C16 | Low | CODED_NEEDS_DEVICE_VERIFY | English T9 budget semantics | English T9 suggestions are primarily rendered by `HorizontalCandidateComponent`, so the visible word limit is now enforced there using `AppPrefs.candidates.t9HanziCharacterBudget / 2` (minimum 1). A budget of `10` should therefore show about `5` English words in the horizontal candidate strip. `CandidatesView` keeps the same fixed 2-unit-per-word helper for consistency if English T9 ever routes through the floating path. |

### Additional change since last revision

- The earlier `handleFcitxEvent.CommitStringEvent` letter-only intercept has been removed. Under the current Option B design, pinyin selection never pushes letters into Rime, so the intercept is no longer needed and could hide unrelated commit bugs.
- `handleVirtualT9Backspace()` now returns `Boolean` so on-screen delete can skip sending a normal backspace when it was consumed by a model-only reopen.
- Physical DEL/BACK uses the same reopen transition and consumes the matching key-up through `t9ConsumedNavigationKeyUp`.
- Focus-out/touch-away handling now includes explicit model state in the clear condition and clears visible transient rows immediately when T9 Chinese cursor updates indicate composition has been abandoned.
- `T9PinyinUtils.t9KeyToPinyin` and `matchedPrefixLength` no longer truncate input with `take(6)`; both now evaluate the full 2-9 digit sequence.
- Removed unused `app/src/main/java/org/fcitx/fcitx5/android/input/t9/PinyinSelectionBarComponent.kt` to avoid duplicate/obsolete pinyin-row logic.
- Clarified the historical `use_t9_keyboard_layout` setting: no key migration, but user-facing strings now say "T9 input mode" and local feature gates use `t9InputModeEnabled`.
- Fixed the reported filtered-Hanzi regressions in code: no unfiltered fallback on empty filtered pages, no raw index-0 OK commit from the Hanzi row, first Right moves the visible filtered highlight, Down in Hanzi focus requests the next page, and a wider bulk-filtered result set can fill sparse `ci*` pages.
- Avoided using `FcitxAPI.selectFromAll` from the T9 Hanzi UI path after device logs showed an `AbstractMethodError` on the delegated runtime API. Bulk-filtered Hanzi selections now use the existing `setCandidatePagingMode(0)` + `select(index)` path and restore the current input-device paging mode afterward.
- Fixed the reported `2496 -> ci` crash: the bulk-filter coroutine no longer calls `refreshT9Ui()` directly from `Dispatchers.Default`; it posts the result to the view thread before touching RecyclerView-backed adapters.
- Added `AppPrefs.candidates.t9HanziCharacterBudget` for the T9 filtered Hanzi row. It defaults to 12 characters with 4-24 bounds and counts displayed Hanzi text length rather than number of candidates.
- T9 Hanzi results now keep a local bulk-sourced list and page locally by character budget for both filtered and unfiltered rows, so Down/Up can move between budgeted pages without flashing through Rime paging.
- Added partial selected-pinyin fallback: if the full selected reading has no Hanzi candidates, filtering can show candidates for the first selected segment and then consume that resolved prefix after a successful selection.
- Selected-prefix consumption now prepares a local replay state, resets fcitx, and replays the remaining raw T9 digits when the committed candidate's normalized comment exactly equals the selected prefix, so examples beyond `yo ci` (such as `2496 -> ci -> one Hanzi`, then remaining `96`) can rebuild candidate rows from the remaining composition without splitting phrase candidates like `ci wo`.
- T9 input mode now prefers the floating paged candidate path even with on-screen T9 controls visible, and `KawaiiBarComponent` ignores Chinese T9 candidate-list updates to avoid showing remaining-segment Hanzi candidates in the Kawaii bar.
- T9 preedit display now greedily renders full digit segments from the local tracker and refreshes immediately on delete, so `2496` should shrink visibly in the pinyin input row as deletes are pressed.
- Reworked the pinyin candidate row appearance/disappearance animation so the bubble opens/closes by row-height reveal from the Hanzi row, with the pinyin chips clipped by the row bounds instead of being separately scaled.
- The pinyin row now synchronizes its width to the measured Hanzi row before first reveal and defers that first animation by one posted pass if the width is still unknown, to avoid wrong/empty first appearances.
- The first-reveal pinyin content path no longer waits for the bubble to fully finish before showing chips. Instead, chips are bound immediately and ride the same reveal progress with a small vertical-offset ramp, so they should emerge with the bubble rather than appearing after a visible blank pause. The earlier chip alpha fade has been removed.
- While the async bulk candidate slice is still loading, unfiltered T9 now builds a synchronous local budgeted page from the current raw fcitx page so the first visible Hanzi row is closer to the later bulk-budgeted row.
- Added a matching active-chip highlight animation for the pinyin row so focus movement there no longer snaps abruptly.
- Replaced the Hanzi row content fade with a smaller active-bubble highlight transition, so navigation gets motion without making full candidate swaps feel mushy. New candidate rows now initialize the highlight directly on the first visible Hanzi candidate.
- English T9 candidate budgeting now counts words rather than letters in the horizontal candidate strip, using half of the configured character budget so a budget of `10` yields about `5` visible English words.
- Compared with `../yuyansdk-reference-codes`: their T9 path filters by replacing selected digits with pinyin directly inside Rime (`Rime.replaceKey(...)`), so engine candidates are already narrowed. This fork still keeps Rime on raw digits, but now approximates that behavior better by trying the longest matching resolved-prefix head before falling back to shorter heads and by letting candidate-comment syllables match selected segments through the same T9 key sequence.
- Added an empty-preedit guard so first-load stale input-panel events do not erase the first T9 digit before Rime has reported a real preedit.

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

Steps 1-7 and Phase D steps 1-3 are coded; remaining work starts with device verification before further cleanup.

1. ~~Add a composition model that can represent resolved prefix plus unresolved suffix.~~ (done)
2. ~~Make `selectT9Pinyin` update/mark that model transactionally instead of losing the selected prefix.~~ (done)
3. ~~Build one presentation snapshot from model plus latest fcitx input-panel and paged-candidate data.~~ (done)
4. ~~Make `CandidatesView` render the top row and pinyin row from that single snapshot.~~ (done)
5. ~~Define delete/back transitions against the same model.~~ (done)
6. ~~Implement focus-out/touch-away coherent clear.~~ (coded, needs device verification)
7. ~~Remove six-digit truncation from `T9PinyinUtils` lookup paths.~~ (coded, needs device verification for long sequences)
8. ~~Remove stale duplicate `PinyinSelectionBarComponent.kt`.~~ (done)
9. ~~Clarify `use_t9_keyboard_layout` naming and semantic gates.~~ (done, persisted key unchanged)
10. Manually verify Option B on device, especially `2496 -> select ai -> delete`, touch-away/return, touch chip parity, physical OK parity, filtered Hanzi tap correctness, and 7+ digit sequence behavior.
11. Only then address remaining lower-risk cleanup: English cleanup and controller extraction.

## 9. Verification Notes

- Use correct T9 examples. `ai` maps to `2 4`, not `2 3`.
- Key regression: `2496 -> select ai -> select Hanzi -> back/delete`.
- Success means all three rows stay mutually consistent, not merely that one row looks correct.
- Verification must cover both touch chip selection and physical focus/OK selection.
- After pinyin selection, the app text field must not receive final text until a Hanzi candidate is selected or Rime commits.
- Long (7+ digit) sequences must preserve full-length pinyin lookup and matched-prefix behavior.
- Regression cases from device testing: `2496 -> ai -> yo -> OK on 哎哟` must commit `哎哟`; `2496 -> ai -> Right` must move the visible Hanzi highlight immediately and should not flash on an old 4th/5th candidate first; Down in Hanzi focus must request the next page when the visible source is pageable; filtered and unfiltered T9 Hanzi rows should both respect the T9 Hanzi character budget; `2496 -> ci` must not show unrelated unfiltered candidates, should not crash when a shown Hanzi candidate is selected, and should rebuild candidates for remaining `96` in `CandidatesView` rather than Kawaii bar after committing one `ci` Hanzi; impossible pairs like `yo ci` should fall back to `yo` candidates first; fresh start `24` must include `ai` in the pinyin row; repeated delete from raw `2496` must visibly shrink the pinyin input row on every press; pinyin candidate row show/hide should animate without popping; Hanzi highlight motion should feel eased without delaying text swaps; English T9 budget `10` should show about `5` visible words in the horizontal candidate strip.
- Local static check: `git diff --check` passes.
- Build check could not run in this environment because no Java runtime is installed.
