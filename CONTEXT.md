# Context

## Domain Terms

### Physical T9 Key Flow

The complete hardware-key decision flow for T9 input. It owns key-down/key-up
pairing, long-press gating, mode-specific key behavior, candidate navigation,
candidate confirmation, punctuation follow-up actions, return actions, and
short-press versus long-press outcomes for Chinese T9, simple English,
Smart English, and number mode.

`FcitxInputMethodService` should act as the platform adapter for this flow: it
executes Android/Fcitx side effects such as committing text, forwarding key
events, refreshing candidate UI, showing punctuation candidates, and handling
return keys, but it should not duplicate the user-facing key-flow rules.

The flow should return command lists rather than directly executing adapter
methods. This keeps multi-step key behavior, such as Smart English `1` and `#`
follow-up actions, testable as ordered outcomes before Android/Fcitx side
effects run.

`PhysicalT9CommandExecutor` owns the side-effect ordering for flow commands.
This keeps the flow's interface focused on user-facing decisions while keeping
fallback execution rules, such as "commit Smart English, then multi-tap, then
pending punctuation, otherwise return", local to one adapter-facing module.

Commands should stay at the domain level. For example, the flow may request
`CommitSmartEnglishCandidate` with spacing/prediction policy and then
`ShowEnglishPunctuationCandidates`, but it should not compute the committed
word text or reset Smart English internals itself. The session modules keep
their own locality; the key flow coordinates user-facing actions.

Each key event should be evaluated against an immutable physical-key state
snapshot supplied by the platform adapter. The flow should not keep querying
live adapter getters while it is deciding commands, because command execution
can change IME state before the key-flow decision is complete.

The flow session owns key-pairing state that exists between key-down and key-up
events, including digit long-press flags, pound long-press state, and deferred
Smart English digits. These are user-facing key-flow rules rather than Android
platform adapter details.

Mode-specific key rules may live behind internal Physical T9 Key Flow modules
as long as callers still cross the same command-based flow interface. This
keeps mode rules local without making `PhysicalT9KeyHandler` or
`FcitxInputMethodService` learn another user-facing key contract.

The refactor may be delivered in small slices, but it must not leave parallel
legacy fallback behavior behind. Each migrated key-flow branch should remove
the old branch it replaces, and the end state should have the command-based
flow as the single implementation.

The first migration slice should target Smart English physical-key behavior:
`1`, `#`, `0`, OK/select, directional candidate navigation, Backspace, and
long-press digit shortcuts. After each code slice, provide a concrete manual
test checklist and wait for user confirmation before migrating the next slice.

### Physical T9 Selection Mode

The physical-key selection subflow inside Physical T9 Key Flow. It owns how
D-pad, OK/select, Enter-as-select, and Smart English Space-as-select map onto
the visible candidate surface: Chinese pinyin row versus Hanzi row, pending
punctuation bottom row, and Smart English bottom row.

`PhysicalT9SelectionMode` returns flow commands, not Android side effects. It
keeps the product rule that visible T9 candidate rows consume physical focus
keys even when a transient row mismatch leaves no movement command, so editor
cursor movement cannot leak through the candidate UI.

### Smart English Lifecycle

The Smart English input lifecycle covers digit composition, candidate lookup,
candidate focus, committing selected words, spacing policy, continuous
next-word prediction, TT9-style pair-frequency reranking, case state, and
learned word/pair recording.

`SmartEnglishLifecycle` owns these transitions behind one Module so the
controller and physical-key flow do not need to know whether the visible
candidate came from active digits or from prediction context. Android-specific
work remains outside the lifecycle: the controller warms dictionaries and the
IME service commits text and refreshes candidate UI through adapter callbacks.

### T9 Punctuation Lifecycle

The T9 punctuation lifecycle covers pending punctuation state, Chinese versus
English punctuation sets, one-key deferred Chinese punctuation, candidate
preview, shortcut selection, commit, cancel, and UI refresh side effects.

`T9PunctuationLifecycle` owns punctuation state transitions and returns
ordered effects. `T9PunctuationCoordinator` is the adapter that executes those
effects against Android/Fcitx callbacks such as clearing transient input UI,
refreshing candidates, cancelling punctuation timeout, and committing text.
This keeps punctuation behavior testable without depending on the IME service
or physical-key flow.

### Chinese T9 Composition Lifecycle

The lifecycle of Chinese T9 composition covers the Kotlin-side digit/session
model, presentation-cache invalidation, editor-tap cleanup, hidden-composition
cleanup, virtual/physical backspace session updates, and forwarded key effects
that decide whether candidate UI should refresh after Rime or disappear
immediately.

`ChineseT9CompositionLifecycle` owns these decisions. `FcitxInputMethodService`
remains the Android/Fcitx adapter: it forwards keys to Rime, finishes composing
text, refreshes views, and focuses Fcitx in or out, but it should ask the
lifecycle whether a composition state transition occurred and which UI action
that transition requires.

### Chinese T9 Presentation Source

The Chinese T9 presentation source turns a stable Chinese presentation
snapshot key into the top reading row and pinyin filter row shown by the T9
candidate surface. It owns candidate-comment preview, separator-aware preview,
resolved pinyin display, pending punctuation preview, digit-to-pinyin fallback
display, and pinyin option generation.

`ChineseT9PresentationSource` should keep these presentation rules out of
`FcitxInputMethodService`. The service remains the Android/Fcitx adapter: it
formats `FormattedText`, supplies Rime/input-panel snapshots, and asks
`ChineseT9CompositionLifecycle` to cache source output.

### T9 Candidate UI Snapshot Pipeline

The complete T9 candidate UI refresh flow. It should turn the current IME
state, candidate data, composition preview, pinyin filter row, focus state,
layout preference, and visibility rule into a stable snapshot that can be
compared before Android views are touched.

`CandidatesView` should act as the Android view adapter for this pipeline: it
may measure, render, scroll, and set view visibility, but it should not own the
rules for which T9 surface is shown, which candidate page is active, whether a
top reading row is reserved, or whether the UI should remain visible.

The snapshot pipeline should preserve the current candidate bubble visual
design while improving refresh locality. The goal is not a new visual style;
the goal is fewer broad refreshes, fewer transient wrong rows, and fewer layout
visibility changes during normal typing.

The pipeline snapshot should be render-ready. It owns the final bottom
candidate page, original candidate indices, shortcut-label visibility, top
reading content, pinyin filter row, candidate focus, visibility decision, and
anchor preference. `CandidatesView` should not rebuild or reinterpret those
domain decisions after the snapshot is produced; it should only diff and render
the snapshot into Android views.

Candidate paging, page caches, and UI focus state belong to the snapshot
pipeline. `CandidatesView` may forward user intents such as page up, page down,
or select shown index, but it should not own the pager/cache rules for Smart
English, pending punctuation, local Chinese budget pages, bulk Chinese
selection, or pinyin row focus.

`T9CandidateSourceSessions` owns source-specific candidate state for Smart
English, pending punctuation, and Chinese candidate sources. The snapshot
pipeline may choose which source is visible, but it should not also carry the
individual source pagers, shortcut original-index mapping, bulk-filter state,
or currently shown owned-row session. Keeping those sessions behind one
internal Module prevents interaction fixes from spreading across the snapshot
builder, Android view adapter, and per-source candidate implementations.

The migration should be sliced by candidate source, but each completed slice
must remove the replaced `CandidatesView` fallback. Start with Smart English
and pending punctuation, then move Chinese local-budget and pinyin-row state,
then bulk Chinese selection. This keeps each user-facing UI surface testable
without leaving parallel render rules behind.

The first implementation slice should fully move Smart English and pending
punctuation page/cache/selection/page-offset state into the snapshot pipeline.
`CandidatesView` should stop owning `T9SmartEnglishPageCache`, pending
punctuation pager decisions, Smart English shown flags, pending punctuation
shown flags, and original-index mapping for those two sources once the slice is
complete.

The second implementation slice should move Chinese local-budget candidate
paging, Hanzi cursor state, and pinyin row window/highlight state into the
snapshot pipeline. `CandidatesView` may still render pinyin chips and request
Android scrolling, but it should not own the pinyin window model or Chinese
local-budget pager state once the slice is complete.

### T9 Shortcut Tail Policy

The visual tail of the T9 shortcut candidate row is the space between the final
visible candidate and the bubble edge. The whole bubble may grow or shrink with
candidate content, but this tail should not vary because a short final word hit
the minimum shortcut-chip width, a long final word used its natural text width,
or the focused shortcut chip applied its visual scale.

`T9ShortcutTailPolicy` owns this rule as a small Module. The Android toolbar
adapter may measure real pooled views, but it should ask the policy which final
candidate can edge-align and how much width is needed for focus overflow. This
keeps the tail rule at one seam instead of scattering it through TextView
padding, candidate-width estimation, and pinyin-row surface alignment.

### T9 Pinyin Row Surface

The render-ready visual surface for the Chinese T9 pinyin filter row. It owns
the folded versus full row decision, stable folded viewport width, ellipsis
placement, focused whole-chip window, displayed pinyin items, highlighted chip,
and readiness signal for the Android view reveal.

`T9PinyinRowSurfacePlanner` is the external seam for these decisions. It may use
smaller internal planners for width, overflow, and chip-window calculations, but
callers should consume one surface plan instead of independently combining
candidate row width, pinyin overflow state, focus state, and chip rendering.
This keeps the short-Hanzi-page edge case local: when a page has very few Hanzi
candidates but many pinyin chips, the focused pinyin row keeps the same folded
viewport and renders whole chips without layout-width churn.

### T9 Pinyin Row Android Adapter

The Android rendering adapter for the Chinese T9 pinyin filter row. It turns a
`T9PinyinRowSurfacePlanner` plan into chip-list contents, row width, overflow
hint placement, first-frame reveal timing, highlight state, and scroll
position.

`CandidatesView` supplies Android measurements and candidate-surface callbacks,
but it should not own pinyin-row reveal state, rendered chip state, or focused
chip scrolling. Keeping those Android details behind one Adapter preserves the
current bubble visuals while making future pinyin-row bugs local to the row
instead of split between the snapshot pipeline and the view host.

### T9 Candidate Render Pass

The render-time decision for which Android candidate UI regions should mutate
for one `T9CandidateRenderState`. It owns the pinyin-row action (`render`,
`sync layout`, `clear`, or `none`), the hidden-frame early exit, and the
visibility request derived after pinyin readiness is known.

`T9CandidateUiRenderer` should execute this pass against Android views, not
re-derive pinyin reveal rules inline. This keeps the frame lifecycle local:
candidate content changes may ask the pinyin row to realign, hidden frames skip
child rendering, and pending first-frame pinyin reveal retries until content is
ready without broad UI refresh churn.

### T9 Candidate Interaction Controller

The command-side interaction surface for the currently rendered bottom
candidate row when that row is owned by the T9 Candidate UI Snapshot Pipeline.
It owns move, page, shortcut commit, and highlighted commit dispatch for Smart
English, pending punctuation, and Chinese bulk-selection rows.

`CandidatesView` may keep the legacy fallback for non-owned Rime candidate
rows, but it should not duplicate source-specific side effects for rows already
owned by the snapshot pipeline. `T9CandidateInteractionController` maps the
pipeline's interaction results to host side effects such as Smart English index
selection, punctuation preview, page application, refresh, and bulk Hanzi
selection.

### Chinese T9 Candidate Frame Gate

Chinese T9 candidate rendering must be source-fresh at the frame level. A frame
must not combine a new composition preview with a stale Rime candidate page, and
it must not briefly show Rime's short current page when the bulk-budgeted page
has already been requested but has not returned yet.

`ChineseT9CandidateFreshness` owns the question "does this engine candidate page
match the current T9 digit sequence?" `ChineseT9CandidateFrameGate` owns the
decision to defer the whole frame while engine or bulk candidates are not ready.
This keeps transitions such as `ge` -> `gel` atomic: the user may see the
previous complete candidate frame for a moment, but should not see a partial
`gel HDL` row one frame before the final `gel HDL Hardware ...` row.
