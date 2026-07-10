# Plan

## Working Agreement

Rizum Guidelines are active for this project/thread until the user says
otherwise.

## Scheme-Aware `1` and `*` Migration

- [x] Record the agreed current-mode and future-scheme key roles in analysis
  and design before implementation.
- [x] Replace key-named English case commands with semantic case-cycle
  commands.
- [x] Move Pinyin and English punctuation entry from short `1` to short `*`
  while preserving long-press candidate shortcuts and number-mode behavior.
- [x] Add focused Physical T9 Key Flow, case lifecycle, and punctuation
  lifecycle regression tests.
- [x] Add an ADR for Pinyin, Stroke, Zhuyin, English, and number key contracts,
  including Stroke `6` as unknown stroke and the performance architecture.
- [x] Run focused unit tests and a Kotlin compile check.
- [x] Commit implementation and architecture documentation separately, then
  push both commits.

## Current Input-Method Follow-up

- [x] Diagnose hardware-key passthrough in game/emulator `TYPE_NULL` contexts.
- [x] Pass through hardware keys when no real text input session is active.
- [x] Add smart English T9 as an optional status-panel toggle.
- [x] Add local smart-English dictionary lookup and learned-word storage.
- [x] Keep simple English multi-tap available for unknown words and learning.
- [x] Refresh cached Fcitx input-method/status state after config reload.
- [x] Replace raw smart-English digit display with word/letter-group feedback.
- [x] Gate physical T9 long-press actions by held duration instead of first
  repeat event.
- [x] Run Kotlin compile and debug APK build checks.
- [x] Install and select the debug IME on the connected phone.
- [x] Re-check the physical OK/confirm key repeat path.
- [x] Replace smart-English letter-group fallback with prefix word candidates
  and a compact no-match state.
- [x] Run Kotlin compile and debug APK build checks for the follow-up.
- [x] Install and select the updated debug IME on the connected phone.
- [x] Inspect TT9 English dictionary source and license notes.
- [x] Generate an adapted English T9 dictionary asset for this app.
- [x] Replace the tiny Kotlin word list with asset-backed lookup.
- [x] Run Kotlin compile and debug APK build checks for the dictionary asset.
- [x] Install and select the updated debug IME with the larger dictionary.
- [x] Add smart-English numeric shortcut labels and local candidate paging.
- [x] Route smart-English long-press `1..9,0` to visible candidate selection.
- [x] Run Kotlin compile and debug APK build checks for smart-English paging.
- [x] Install and select the updated debug IME with smart-English paging.
- [x] Consume smart-English candidate navigation at page edges and route
  English punctuation candidates through the same navigation/commit path.
- [x] Track input sessions from `onStartInput()` so early physical
  Back/Backspace cannot leak as system/app back in normal text editors.
- [x] Audit physical key rules across Chinese, smart/simple English, and number
  modes; normalize number commits, pending punctuation `*`, and forward-delete
  cancellation.
- [x] Add a tested `PhysicalT9KeyPolicy` module for shared physical-key
  classification and route service/number-mode callers through it.
- [x] Move physical T9 decision/execution routing into tested
  `PhysicalT9KeyHandler` and leave `FcitxInputMethodService` as the host
  adapter instead of a duplicate key-state machine.
- [x] Document the exact Smart English learning flow in README, release notes,
  and Baidu release readmes.
- [x] Add a top-level Dictionary Management entry for the Smart English learned
  word list.
- [x] Allow adding, editing, and deleting Smart English learned words from
  settings.
- [x] Disable Smart English learning in password/no-personalized-learning
  editor contexts.

## User Retest Checklist

- [ ] Confirm Chinese T9 uses the same candidate bubble placement as Smart
  English after the layout-experiment revert.
- [ ] Confirm a short final Hanzi candidate page does not clip a populated
  pinyin row down to one or two chips; it should show four full chips plus a
  quiet ellipsis only for that edge case, while normal candidate rows keep all
  pinyin chips visible when the row has enough width.
- [ ] Confirm game/emulator physical-key mappings pass through without IME
  input.
- [ ] Confirm smart English T9 shows words or compact no-match state, not raw
  digits or long letter-group strings.
- [ ] Confirm `435`, `4355`, and `43556` keep showing useful candidates such
  as `hello`/`help`.
- [ ] Try less common English words to confirm the larger adapted dictionary is
  active.
- [ ] Confirm smart English respects the T9 candidate budget setting, supports
  page up/down, and commits visible candidates by long-pressing `1..9,0`.
- [ ] Confirm smart-English first-page up does not move the editor cursor, and
  `*` punctuation candidates can move, commit, and page normally.
- [ ] Confirm hot/laggy physical-key typing no longer leaks digits or triggers
  long-press choices during short presses.
- [ ] Confirm immediately pressing physical Backspace after enabling typing
  deletes text instead of returning/backing out.
- [ ] Confirm number mode short digits still input digits, long digits still
  input operators, short `*` inserts a literal star, and long `*` opens the
  operator panel.
- [ ] Confirm DPAD center, Enter, and numpad Enter act consistently for
  candidate/confirmation contexts.
- [ ] Watch for the intermittent Rime/default-only recovery issue after
  redeploy or reload.

## Physical T9 Key Flow Architecture

- [x] Decide that Physical T9 Key Flow owns complete hardware-key behavior and
  leaves `FcitxInputMethodService` as the platform adapter.
- [x] Decide that the flow returns ordered domain commands from immutable
  physical-key state snapshots.
- [x] Decide that cross-key event state, such as long-press and deferred Smart
  English digits, belongs to the flow session.
- [x] Migrate Smart English `1` and `#` behavior into the command-based
  Physical T9 Key Flow slice, removing the replaced legacy branches.
- [x] Run focused tests, build/install debug, and provide a manual Smart
  English `1/#` test checklist before migrating the next slice.
- [x] Migrate the remaining Smart English physical-key behavior into the same
  command-based flow: `0`, `2..9`, long-press shortcuts, candidate navigation,
  candidate confirmation, and Backspace.
- [x] Move number-mode digit, `*`, and `#` special-key branches into the
  command-based flow and remove the replaced handler fallback.
- [x] Split number-mode Physical T9 rules behind an internal mode module while
  preserving one command-based flow interface for callers.
- [x] Split English and Smart English physical-key rules behind an internal
  mode module while preserving the same command interface.
- [x] Split Chinese physical-key rules behind an internal mode module while
  preserving the same command interface.
- [x] Extract Physical T9 Selection Mode so D-pad/OK selection commands for
  Chinese candidates, pending punctuation, and Smart English live behind one
  tested command-producing Module.
- [ ] Move remaining Chinese special-key branches into the command-based flow
  in small slices, removing each replaced handler fallback as it migrates.

## Chinese T9 Composition Lifecycle Architecture

- [x] Decide that Chinese T9 composition lifecycle owns session mutation,
  presentation-cache invalidation, and composition cleanup decisions while
  `FcitxInputMethodService` stays the Android/Fcitx adapter.
- [x] Move forwarded physical-key composition mutation into
  `ChineseT9CompositionLifecycle` so the service only performs the returned UI
  action.

## T9 Candidate UI Snapshot Pipeline Architecture

- [x] Decide that the T9 Candidate UI Snapshot Pipeline owns render-ready
  candidate UI snapshots while `CandidatesView` acts as the Android view
  adapter.
- [x] Decide that candidate paging, page caches, and UI focus state belong to
  the snapshot pipeline instead of `CandidatesView`.
- [x] Decide to migrate by candidate source, deleting replaced
  `CandidatesView` fallback after each slice.
- [x] Migrate Smart English and pending punctuation page/cache/selection/page
  offset state into the snapshot pipeline.
- [x] Migrate Chinese local-budget candidate paging, Hanzi cursor state, and
  pinyin row window/highlight state into the snapshot pipeline.
- [x] Narrow the T9 Candidate UI Snapshot Pipeline input seam by introducing a
  render-stable input snapshot while preserving the current visual algorithms.
- [x] Remove the misdiagnosed pinyin-chip strip layout seam after frame
  evidence showed the stale Hanzi candidate row was the true refresh bug.
- [x] Keep folded pinyin width stable when focus moves from Hanzi candidates
  to the pinyin filter row, so short second pages do not collapse the focused
  pinyin viewport.
- [x] Render focused folded pinyin as a whole-chip window inside the stable
  folded viewport, avoiding arbitrary left-edge clipping and layout-width
  churn when moving through hidden pinyin chips.
- [x] Deepen the T9 Pinyin Row Surface seam so folded/full/focused pinyin row
  width, hint, display window, and readiness are returned as one render-ready
  plan for `CandidatesView`.
- [x] Deepen the T9 Candidate Render Pass seam so pinyin render/sync/clear
  decisions and hidden-frame visibility decisions are planned outside the
  Android renderer.
- [x] Extract the T9 Pinyin Row Android Adapter so pinyin chip rendering,
  reveal timing, overflow hint placement, focus rendering, and row scrolling
  are local to the pinyin row instead of spread through `CandidatesView`.
- [x] Deepen the T9 Candidate Interaction Controller seam so pipeline-owned
  bottom-row move/page/commit side effects are dispatched outside
  `CandidatesView`, while non-owned Rime candidate rows keep their fallback.
- [x] Deepen T9 Candidate Source Sessions so Smart English, pending
  punctuation, Chinese local-budget/bulk source state, and owned shown-row
  original-index mapping live behind one internal Module instead of directly in
  the snapshot pipeline.

## Stroke And Zhuyin T9 Development

- [x] Record the accepted key maps, switching hierarchy, hot-path constraints,
  and shared candidate-snapshot contract in ADR-0001.
- [x] Add the `ChineseT9Scheme` Module and classify the cached Rime sub-mode
  without synchronous engine work in the physical-key path.
- [x] Make Physical T9 Key Flow interpret Chinese digits, `0`, and short `#`
  through the active scheme, deleting replaced Pinyin-only fallback rules.
- [x] Add a compact non-Pinyin composition/presentation session behind
  `ChineseT9CompositionCoordinator` while preserving the existing Pinyin
  resolved-segment implementation.
- [x] Add `t9_stroke` and `t9_zhuyin` schemas to the maintained Rime config,
  including bounded Stroke unknown-key support and Zhuyin comment formatting.
- [x] Expose the new schemas through the existing Dictionary Switch menu and
  keep the current scheme focused by the existing Rime action contract.
- [x] Add focused unit tests for scheme classification, key-flow decisions,
  raw-code presentation, and stale-session clearing.
- [x] Build and install a debug APK; verify scheme switching, exact and unknown
  Stroke lookup, Zhuyin `38 -> 好 / ㄏㄠ`, scheme-aware candidate freshness, and
  bounded deployment memory on the physical phone.
- [x] Decouple Pinyin bulk reset from local candidate paging and preserve raw
  scheme page/cache state across unchanged refreshes.
- [x] Add generation-aware candidate loading and invalidate stale interaction
  snapshots while a replacement frame is pending or hidden.
- [x] Make scheme transition and reconnect initialization clear composition,
  loading, source, focus, and presentation state through one operation.
- [x] Store Zhuyin short-`#` reading boundaries in the raw composition session
  so Backspace stays aligned with Rime.
- [x] Make raw-scheme `0` and universal OK fall back to the bottom candidate
  row when no Pinyin row exists.
- [x] Serialize candidate selection through the service Fcitx queue and reject
  obsolete composition/source tickets.
- [x] Re-run focused tests, install the debug APK, and measure the raw-scheme
  candidate path on the physical phone.
- [x] Device-test physical shortcuts, punctuation, confirmation, and deletion
  across Pinyin, Stroke, and Zhuyin; retain focused key-flow coverage for
  long-`#` because adb's synthetic long press does not honor the device delay.

## Chinese Scheme Commit And Quick Switching

- [x] Reproduce Pinyin short-`#` opening Rime punctuation instead of committing
  the visible reading, and audit the divergent Stroke/Zhuyin paths.
- [x] Replace all three composing short-`#` paths with one semantic literal-code
  commit command and delete the old Stroke no-op/Zhuyin delimiter behavior.
- [x] Add a top-level settings page that selects at least one Chinese T9 scheme
  for the quick cycle, defaulting to Pinyin only.
- [x] Make idle short `#` cycle the configured Chinese schemes, while retaining
  Return for a one-scheme configuration and long `#` for top-level modes.
- [x] Show the active Chinese scheme in the T9 space-bar label and rename the
  Pinyin schema from `中文九键` to `拼音九键`.
- [x] Add focused key-flow, preview validation, and scheme-cycle regression
  tests, and verify the settings invariant on-device.
- [x] Build/install debug and run physical-device Pinyin, Stroke, and Zhuyin QA
  with exact code commit, candidate selection, deletion, punctuation, and
  scheme switching.

## Chinese Scheme Follow-up And Candidate Quality

- [x] Diagnose Stroke blank slots on-device and identify the committed U+18800
  Tangut component plus the missing custom-font fallback.
- [x] Record the replacement key contract: idle short `#` Return, idle long `*`
  configured-scheme cycle, and Pinyin idle `1` literal input.
- [x] Migrate the replacement key contract through semantic flow commands and
  delete the old idle-short-`#` cycle branch.
- [x] Add `T9ZhuyinResolver`, bounded legal-sequence validation, and a ticketed
  non-interactive no-match state.
- [x] Add `T9StrokeCodec` and a reproducible curated Stroke dictionary generator
  that rejects non-Han components before Rime compilation.
- [x] Add system fallback for unsupported custom-font glyph runs and make text
  measurement use the same fallback plan.
- [x] Give Chinese T9 Schemes a dedicated dial-pad settings icon.
- [x] Run focused tests and a debug build, install the APK and Rime data, then
  device-test all confirmed key, Stroke, Zhuyin, focus, and no-match cases.

## Stroke Glyph Eligibility Follow-up

- [x] Reproduce blank Stroke slots across single-key and two-key combinations
  and capture their exact Rime text/code points on-device.
- [x] Filter unsupported Stroke glyphs at the candidate-source boundary while
  preserving original Rime selection indices.
- [x] Demote supplementary Han in the generated mobile Stroke dictionary so
  portable BMP candidates fill early pages.
- [x] Add source-mapping and generator regression tests, remove diagnostic
  logging, rebuild/deploy, and repeat the device sweep.

## Zhuyin Candidate Presentation Follow-up

- [x] Sweep all 100 two-key codes on-device and compare local validity with
  settled Rime candidate availability.
- [x] Remove speculative local reading paths and the parallel Zhuyin filter row;
  make the focused Rime candidate own the top preview.
- [x] Normalize apostrophe-separated candidate comments and fix the Rime schema
  transforms that left Latin fragments in phrase readings.
- [x] Add focused resolver, presentation, snapshot, and source-planner tests.
- [x] Run the full unit suite, rebuild/install debug, and repeat final device QA.

## Default Zhuyin Reading Filter

- [x] Add a bounded, cached legal-reading option API separate from hot-path
  validity checks.
- [x] Publish one stable legal-reading snapshot for each valid raw code without
  requiring a hidden discovery gesture.
- [x] Route selected Zhuyin readings through the shared cross-page candidate
  filter while keeping the default candidate-driven preview.
- [x] Keep the row visible while Up/Down moves focus between readings and Hanzi.
- [x] Preserve selected-reading cross-page filtering and raw-code lifecycle resets.
- [x] Add key-flow, resolver, coordinator, presentation, and filtering tests.
- [x] Build/install debug and verify `38`, `2038`, selection, Backspace, and
  invalid `33` with the unified Pinyin-like interaction on the physical phone.

## Viewport-aware Folded Reading Preview

- [x] Reproduce and lock down the `68` fixed-four-chip regression.
- [x] Compute the folded preview count from measured chip widths and the stable
  row viewport while preserving four chips as the minimum.
- [x] Position the ellipsis from the exact displayed prefix.
- [x] Run focused row-layout tests, build/install debug, and verify `68` on the
  physical phone without changing the short-Hanzi folded interaction.

## Pinyin Short-`1` Separator

- [x] Replace the idle short-`1` digit with a literal apostrophe.
- [x] Preserve composing separator insertion and long-press shortcut/digit rules.
- [x] Update the key contract and focused flow tests.
- [x] Build/install debug for physical-phone verification.
