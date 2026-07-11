# Context

## Product Boundary

This repository adapts Fcitx5 for Android to physical T9-key smart/feature
phones. The primary experience is physical-key input with a compact touch UI
for candidates, settings, symbols, password entry, and secondary actions.

Supported T9 mechanisms:

- Chinese: Pinyin, mobile Stroke (`笔画九键`, not Wubi 86), and Zhuyin.
- English: multi-tap and predictive Smart English with next-word prediction.
- Number mode: digits, common operators, and simple expression results.
- Shared physical selection mode for cursor selection and edit actions.

Rime supplies Chinese engine candidates. `rime-ice-t9-phone` is the maintained
configuration companion and must provide `t9`, `t9_stroke`, and `t9_zhuyin`.

## Stable Key Contract

The complete scheme-aware contract is recorded in
`docs/adr/0001-scheme-aware-physical-t9-key-contract.md`. Its main invariants
are:

- Long `#` cycles top-level Chinese, English, and number modes.
- Idle long `*` cycles enabled Chinese schemes; short `*` opens punctuation in
  text modes.
- Short `#` is Return while idle and commits the visible raw Chinese code while
  composing.
- `1` belongs to the active scheme: Pinyin separator, Stroke horizontal token,
  Zhuyin group, or English case cycle.
- Long `1..9,0` selects visible shortcut candidates when a candidate owns that
  shortcut; otherwise it keeps the mode-specific literal/operator behavior.
- OK/center confirms candidates. `0` is not a universal confirm key because
  Zhuyin uses it for `ㄧㄨㄩ`.
- Number mode keeps literal arithmetic behavior and never opens text
  punctuation.

Key rules live in the Physical T9 flow. The Android service must not duplicate
them as fallback branches.

## Physical Input Pipeline

`PhysicalInputRouter` owns route priority across transient number panels,
selection actions, password handling, Physical T9, mapped delete recovery, and
ordinary forwarded keys. Routes share one handled/consume-key-up result so a
key cannot be interpreted twice.

`PhysicalT9KeyHandler` captures one immutable, mode-aware state snapshot and
delegates to `PhysicalT9KeyFlow`. Internal Chinese, English, number, and
selection modules return ordered domain commands. `PhysicalT9CommandExecutor`
executes those commands through `PhysicalT9KeyHostAdapter`.

`FcitxInputMethodService` is the platform adapter. It may commit editor text,
forward keys to Fcitx, switch modes, and request UI updates, but it does not own
user-facing key decisions. The reducer performs no file access, dictionary
scan, Rime call, coroutine wait, or Android view measurement.

Long-press and key-pairing state belongs to `PhysicalT9KeyFlowSession`. Device
events are classified by held duration and repeat state; new rules must not
reintroduce service-side repeat fallbacks.

Idle long `0` may be configured for voice in Pinyin/Stroke and English, but only
when no composition, punctuation, multi-tap character, or shortcut candidate
owns the key. Zhuyin and number mode keep their scheme-specific `0` behavior.

## Chinese Composition And Rime

`ChineseT9CompositionCoordinator` is the service-facing interface for Pinyin,
Stroke, and Zhuyin sessions. It owns raw digits, resolved readings, presentation
keys, backspace/replay behavior, and literal-code commit text.

`ChineseT9CompositionLifecycle` decides session mutation and immediate UI
cleanup. `ChineseT9PresentationSource` builds the stable top preview and
reading-filter content. Scheme-specific resolvers and the Rime bridge stay
behind the coordinator.

`ChineseT9EngineOperation` serializes Fcitx submission, candidate reads,
selection, and replay. Operations carry composition/source tickets and reject
stale work before publishing UI effects. `ChineseT9CandidateFreshness` and
`ChineseT9CandidateFrameGate` prevent a new preview from being displayed with
an old candidate page.

Each Chinese scheme has an independent Simplified/Traditional default.
`ChineseT9OutputScriptPolicy` hides Rime option polarity, while
`ChineseT9OutputScriptSession` rejects stale asynchronous assignments. The
default is applied once on scheme entry or preference change and does not lock
out temporary Rime-side changes.

## Smart English

`SmartEnglishLifecycle` owns digit composition, candidate focus, commit spacing,
continuous prediction, case state, pair-frequency reranking, and learned
word/pair recording.

Dictionaries are warmed outside the first-key path. Candidate and prediction
lookups use immutable revisioned snapshots and sequence/page caches.
`SmartEnglishPersistence` publishes learned data in memory immediately and
coalesces atomic writes on one background writer. Password and
no-personalized-learning editors never add learned words.

Dictionary management screens use the same persistence interface as runtime
prediction, so edits are visible without polling files.

## Punctuation

`T9PunctuationLifecycle` owns Chinese/English punctuation sets, preview,
shortcut selection, commit, cancel, and set toggling.
`T9PunctuationCoordinator` applies its ordered effects. Candidate selection
followed by punctuation is chained after successful commit so a delayed Rime
event cannot overwrite the punctuation row.

## Candidate Presentation

`T9CandidateUiSnapshotPipeline` is the domain boundary for candidate UI. It
combines stable input state with `T9CandidateSourceSessions` and publishes one
render-ready snapshot containing:

- top preview and optional reading-filter row;
- bottom candidates, page state, and original source indices;
- candidate focus and shortcut labels;
- visibility, anchoring, and source interaction identity.

Smart English, punctuation, local Chinese pages, bulk filtered Chinese pages,
and engine-backed Chinese pages all use the same shown-source interaction
contract. Paging and original indices are produced together; Android rendering
must not reconstruct them.

`T9CandidateRefreshGeneration` owns scheduling through the first complete frame.
Replaced callbacks cannot consume a newer trace or render request.
`T9CandidateUiRenderer` chooses the minimal region changes for one snapshot.
`T9CandidateSurfaceAndroidAdapter` renders the top, reading, candidate, and
shortcut rows; `CandidatesView` remains the floating Android window host.

Geometry is centralized:

- `T9CandidateSurfaceGeometry` owns measured candidate and reading-row sizing.
- `T9ShortcutTailPolicy` keeps the final candidate-to-bubble spacing stable.
- `T9PinyinRowSurfacePlanner` owns folded/full viewport and whole-chip windows.
- `T9PinyinRowAndroidAdapter` owns row reveal, focus, and scroll publication.
- `FloatingCandidateWindowController` owns cursor anchoring, insets, delayed
  show, and touch-receiver placement.

The accepted bubble visuals are product behavior. New candidate mechanisms join
before the snapshot seam rather than creating another renderer or view-owned
pager.

## Voice And Toolbar

The idle KawaiiBar is always expanded. Voice occupies the former leading toggle
slot; temporary clipboard or inline-suggestion content changes that slot to a
Back action so the toolbar remains reachable. Voice, undo, redo, text editing,
clipboard, and hide-keyboard actions are independently configurable. Quick
Settings is fixed and cannot be removed.

Voice selection is an enabled IME target with an optional subtype. Explicit
preferences support standalone voice IMEs without subtype metadata; automatic
selection prefers declared voice subtypes and conservative voice-service
identity hints. Target resolution happens when invoked, and unavailable targets
show feedback instead of failing silently. The full contract is recorded in
`docs/adr/0002-voice-input-and-toolbar-contract.md`.

## Startup And Performance

`T9ResponsivenessTrace` measures one physical input through decision, effect,
engine wait, snapshot, render, and first complete frame. It is enabled only by
the developer preference and emits aggregate input summaries every 20 samples.

`StartupPerformanceTrace` owns semantic process-start stages. Input components
use concrete runtime classes for dependency identity, avoiding generic
reflection during first view construction. Hidden selection/number panels and
the clipboard Room database are initialized on first use rather than every IME
start.

Quick Settings renders from `FcitxCachedState.statusAreaActions` before attach;
opening the window never waits for a new serialized engine query.

`DataInstallationState` and `DataInstallationStateCodec` provide the bounded,
atomic native-data fast path. Any version, descriptor, plugin, checksum, or
corruption mismatch falls back to a complete installation; there is no partial
compatibility path.

The `performance` Module owns isolated release-derived Baseline Profile and
Macrobenchmark targets. It stages a clean sibling `rime-ice-t9-phone` checkout,
requires the real `t9` schema, uses paced physical keyboard events, and restores
the user's previous IME. It must never read or clear formal/debug app data.
Generated Baseline and Startup Profiles live under
`app/src/release/generated/baselineProfiles/`.

Repeatable commands and device-debugging rules live in `docs/t9-debugging.md`.
Release packaging lives in `docs/release-runbook.md`.

## Extension Rules

- Add a new input mechanism through composition/engine snapshots and the shared
  candidate-source interface, not through `CandidatesView` branches.
- Keep one owner for each decision. Delete replaced logic instead of retaining
  compatibility fallbacks.
- Cache immutable inputs at dictionary, engine, paging, and geometry seams; do
  not cache Android view state as domain state.
- Preserve generation/ticket checks across every asynchronous boundary.
- Add decision comments only where a product constraint or rejected alternative
  would otherwise be easy to undo.
- External schemas, dictionaries, and algorithms require a license audit before
  data or source is adapted into the project.
