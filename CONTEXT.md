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

### Physical T9 Input Scheme

The mechanism-specific interpretation of the physical keypad inside one
top-level input mode. Pinyin, Stroke, Zhuyin, Smart English, simple English,
and number input do not share one literal key map: they share semantic actions
such as punctuation entry, candidate shortcuts, confirmation, return, and mode
switching, then reserve digit groups for their own composition data.

`*` is the common symbol-entry key for text schemes. `1` belongs to the active
scheme: Pinyin separator, Stroke horizontal stroke, Zhuyin `ㄅㄆㄇㄈ`, or
English case cycle. Number mode keeps literal numeric behavior. Key-flow
commands must describe these semantic actions rather than the historical key
that triggered them.

Stroke means the five-stroke mobile input method (horizontal, vertical,
left-falling, dot/right-falling, and bend), not Wubi 86. Its sixth input token
is the unknown-stroke wildcard. The internal token stays semantic and the
engine Adapter translates it to the backend-specific wildcard representation.

New Chinese mechanisms join the existing Physical T9 Key Flow and T9 Candidate
UI Snapshot Pipeline. They own compact composition sessions and cached engine
snapshots, while `FcitxInputMethodService` remains a platform Adapter. They must
not introduce direct dictionary work, Rime calls, or Android view measurement
into the synchronous physical-key decision path.

### Chinese T9 Output Script Default

The preferred Simplified or Traditional output selected independently for
Pinyin, Stroke, and Zhuyin. It is applied once when its scheme becomes active,
when Rime first becomes ready, or when that active preference changes. It is
not a permanent lock: a manual Rime-side toggle remains effective for the rest
of the current scheme visit.

`ChineseT9OutputScriptPolicy` owns each scheme's Rime option name and polarity.
`ChineseT9OutputScriptSession` owns generation-tagged requests across the
asynchronous Fcitx seam. The platform Adapter verifies that the request still
belongs to the active Rime scheme and applies one typed option assignment. It
must not infer state by parsing translated status-action labels, and it must not
read preferences or call Rime from the physical-key or candidate-frame path.

### Physical Input Router

The ordered physical-key routing Module above Physical T9 Key Flow. It owns
route priority and key-down/key-up pairing across number transient panels,
selection actions, password input, active selection mode, Physical T9 Key Flow,
mapped delete recovery, and ordinary forwarded keys.

Each route uses the same handled/consume-key-up Interface. Mapping to Android
input-mode keys is lazy so a password, selection, or T9 route that consumes the
original key does not allocate a replacement `KeyEvent`. Selection-mode release
runs before generic consumed-key pairing because its short-press action is
deferred until key up. `FcitxInputMethodService` supplies route Adapters and
platform effects, but it must not re-create the route order or maintain a
parallel consumed-key-up field.

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

### Smart English Persistence

The persistence Module shared by runtime prediction and dictionary-management
screens. Learned words and learned word pairs become visible through immutable
in-memory snapshots immediately, while one serialized background writer
coalesces newer generations and atomically replaces the persisted file. Input
and candidate lookup Interfaces must not read file metadata or perform writes.

### Physical T9 State Capture

The mode-aware snapshot Module between the platform Adapter and Physical T9 Key
Flow. It captures the active mode once, reads only that mode's state plus shared
selection/punctuation state, and supplies one internally consistent flow
snapshot. Number and simple-English input must not query Chinese candidate
presentation; Chinese input must not query Smart English sessions.

### T9 Punctuation Lifecycle

The T9 punctuation lifecycle covers pending punctuation state, Chinese versus
English punctuation sets, explicit symbol-key entry, candidate preview,
shortcut selection, commit, cancel, and UI refresh side effects.

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

`ChineseT9CompositionCoordinator` is the external Interface for this lifecycle.
It hides the composition session, lifecycle policy, presentation source, and
Rime bridge behind snapshot, presentation, pinyin-selection, candidate-reading,
resolved-prefix, backspace, and replay operations. `FcitxInputMethodService`
must not reconstruct the composition model or candidate-comment matching rules;
it only executes editor, Rime, focus, and UI effects requested around those
operations.

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

### T9 Candidate Surface Android Adapter

The Android adapter for the visible T9 candidate surface: top preedit bubble,
pinyin filter row, Hanzi/Smart English/punctuation candidate row, shortcut
candidate row, focus highlight, and immediate hide/clear operations.

`T9CandidateSurfaceAndroidAdapter` owns rendering into Android row widgets.
`CandidatesView` remains the floating window adapter: it owns cursor anchoring,
window measurement, insets, touch receiver placement, and width-budget helpers.
This keeps visual row rendering local without reopening the delicate floating
window positioning rules.

### T9 Candidate Surface Geometry

The geometry module for the T9 candidate surface. It owns the shared sizing
rules for candidate width budget, shortcut candidate layout, pinyin chip
measurement, folded pinyin row width, pinyin focus viewport, pagination width,
and visual tail padding.

`CandidatesView` supplies Android measurements and preference-derived pixel
values, then consumes one geometry result. It should not independently
recombine pinyin widths, candidate widths, shortcut row tail policy, and focus
state. This keeps the bubble-width design adjustable at one seam instead of
spreading small ratio fixes through the floating window adapter.

The geometry Module also owns the latest measured shortcut-toolbar width.
`T9CandidateSurfaceAndroidAdapter` reports real Android measurement after a row
render; later surface plans consume that observation without reading the view
back from `CandidatesView`. The first plan still uses the previous stable
observation, preserving the current real-measurement visual design rather than
replacing it with text-width estimation.

### Floating Candidate Window Controller

The floating window controller owns candidate-surface anchoring outside the
keyboard: cursor anchor snapshots, parent size, max window width, delayed show
until layout is measured, delayed show until content is ready, above-cursor
preference, navigation-bar bottom inset, translation, and the touch receiver
popup.

`CandidatesView` remains the Android view that hosts the candidate rows, but it
should ask `FloatingCandidateWindowController` when to show, hide, position, or
dismiss the floating surface. This keeps candidate row rendering independent
from the timing rules that prevent first-frame flicker and misplaced touch
receivers.

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

### T9 Candidate Refresh Generation

One candidate refresh request from scheduling through snapshot/render/frame
completion. It owns a monotonic generation ID and the matching responsiveness
trace ID. Cancelled or replaced callbacks cannot consume a newer request, and
render code must not look up a different active input generation midway through
the frame.

### Chinese T9 Engine Operation

The serialized operation Module for Chinese Fcitx work. It submits physical
key forwarding, pinyin mirroring, candidate selection, bulk candidate reads,
and composition replay through the same queue. Ticket-sensitive operations are
accepted on the owner thread before execution and revalidated before their UI
effects are published.

`T9CandidateUiStateBuilder` and `T9CandidateSourceSessions` are internal
Implementation details of the snapshot pipeline. Callers submit one stable
`T9CandidateUiInputSnapshot`; the pipeline builds the render snapshot and
retains the matching shown-source interaction state atomically. This prevents
the Android view adapter from maintaining a second candidate page, original
index mapping, or local-budget flag beside the rendered snapshot.

The source migration is complete: Smart English, pending punctuation, Chinese
local-budget, bulk Chinese, and engine-backed Chinese rows all use the same
shown-source session and interaction Interface. New candidate sources must join
that Interface rather than adding view-owned fallback state.

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

All visible T9 bottom rows, including engine-backed Chinese rows, are owned by
the snapshot pipeline. `CandidatesView` must not keep a parallel fallback page
or original-index mapping. `T9CandidateInteractionController` maps the
pipeline's interaction results to host side effects such as Smart English index
selection, punctuation preview, engine page offset, refresh, and Hanzi
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

### Input Dependency Identity

The unique type used by the input surface's dynamic dependency scope. Input
components and input windows are final runtime classes, so their concrete
`KClass` is their identity. They must not rediscover that identity by walking
generic supertypes during first input-surface construction.

`ConcreteUniqueComponent` owns this identity rule for static input components;
the `InputWindow` bases apply the same rule to dynamic windows. `DynamicScope`
continues to own dependency arrival and window lifecycle. This keeps picker and
keyboard replacement dynamic while removing Kotlin generic reflection from the
cold input-view path.

### Data Installation Fingerprint

The completed proof that native app/plugin data can be reused without parsing
or rebuilding its complete file hierarchy. It combines the build-emitted main
descriptor identity, an exact digest of the installed merged-descriptor file,
canonical plugin package identities, and the loaded plugin metadata required by
native startup.

`DataInstallationState` owns this proof. The fast path may restore plugins only
when every input matches; any missing state, old format, changed package,
changed build identity, or descriptor-content mismatch enters the full Data
Installation Module. The fingerprint does not replace the descriptor as the
source of truth during a full install.
