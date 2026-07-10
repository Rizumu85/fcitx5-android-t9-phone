# Design

## Scheme-Aware Physical Key Design

Physical keys map to domain commands through the active input scheme rather
than carrying one global meaning. `*` is the shared symbol entry point for text
schemes, while `1` remains available to the scheme: Pinyin separator, Stroke
horizontal stroke, Zhuyin `ㄅㄆㄇㄈ`, or English case cycle. Number mode remains
numeric and keeps its current `*` operator behavior.

English case uses one short-press cycle (`abc`, `Abc`, `ABC`) so long `1`
remains the first candidate shortcut. Text-mode `*` owns a complete ordered
interaction: commit the pending word or Chinese candidate without adding a
space, stop English next-word prediction for this boundary, then open the
correct punctuation set. A second short `*` toggles the pending punctuation
set; long `*` inserts a literal star.

The key-flow Interface exposes semantic commands such as cycling English case,
showing Chinese or English punctuation, and committing a literal star. Command
names must not retain the old physical key that happened to trigger them. The
mode-specific Physical T9 Key Flow Modules decide commands from one immutable
state snapshot; the command executor and host Adapter own side-effect ordering.

Stroke and Zhuyin use scheme-specific adapters, not service-owned input
branches. Their composition sessions own raw codes and candidate presentation.
The candidate UI continues to consume render-ready snapshots, so a new
mechanism does not create a parallel row renderer or paging implementation.
Lookup dictionaries are warmed outside key handling, and each key press only
mutates a compact session key and requests one cached candidate snapshot.

## Project Goal

Maintain a physical-key-friendly Android T9 input method that remains usable in
Chinese, simple English, smart English, password entry, and non-text app
contexts such as games and emulators.

## Physical-Key Passthrough Design

T9 physical-key interception is scoped to real input sessions. For non-text
`TYPE_NULL` contexts where the input view was not started, hardware keys pass
through through the normal `InputMethodService` path instead of being remapped
or forwarded to Fcitx. This protects game/emulator key-mapping apps while
preserving existing T9 handling for normal text, password, and active IME
sessions.

## Smart English T9 Design

The current English multi-tap behavior remains the default simple mode. A
separate smart-English toggle is available in the compact status panel. When
enabled and the T9 mode is English, physical digit keys `2..9` build a local
digit sequence.

The candidate row shows dictionary matches from a small built-in English list
plus app-private learned words. The built-in list is stored as an asset grouped
by T9 digit sequence, adapted from TT9's English dictionary data. Left/right
moves the highlighted candidate, center/enter/space commits it, and Backspace
edits the digit sequence before touching editor text.

Learning is intentionally local and low-friction: words typed in simple
multi-tap mode are collected as consecutive letters and saved when the user
commits a delimiter such as space. This gives users a way to introduce special
names or technical terms without adding a full dictionary-management UI in this
pass.

Learned words are now treated as a small user dictionary. The settings home has
a Dictionary Management entry, with a Smart English learned-word list where
users can add, edit, and delete words. The runtime dictionary reloads the file
when it changes so edits made from settings affect later Smart English
candidates without restarting the app.

Learning avoids sensitive contexts. It is disabled for password variations and
editors that set `IME_FLAG_NO_PERSONALIZED_LEARNING`, so verification codes or
private fields are less likely to enter the learned dictionary. If an unwanted
word is still learned, the user can remove it from Dictionary Management.

## Smart English Display Design

Smart English does not expose the raw digit buffer as the main visible
feedback. The candidate row shows predicted words. The top reading mirrors the
currently highlighted word so the user's eye sees language, not numbers.

Candidate lookup supports prefix matches. Exact T9 matches are ranked first,
then longer words whose T9 digit sequence starts with the current input. This
lets incomplete sequences such as `435` surface `hello` or `help` instead of
falling back too early.

The runtime keeps an exact digit-sequence map and uses a bounded ranked prefix
pool for longer completions. This keeps lookup responsive while allowing the
dictionary asset to contain a full English word list.

Smart English candidate paging is local to the Android-side candidate surface.
It uses the same T9 candidate character budget as Chinese T9, maps the current
visible page back to original dictionary indices, and reuses the existing
shortcut-label UI (`1..9,0`). Long-pressing a number while a smart-English
sequence exists commits the matching visible candidate; if no candidate exists
for that shortcut, the key falls back to the literal digit behavior.

If the digit sequence has no dictionary match, the UI shows a compact no-match
state rather than raw digits or a long expansion of every key's letter group.

## Rime Reload Recovery Design

Reload/redeploy is treated as a state-refresh boundary, not only a native
config operation. After `reloadConfig()`, refresh the cached current input
method and status-area actions from Fcitx. This narrows the gap between in-IME
redeploy and the broader refresh caused by switching away to another system
IME and back.

## Physical Long-Press Robustness Design

Physical-key long press is based on held duration, not the first repeat event
alone. The existing keyboard long-press delay preference is reused as the
minimum duration for physical T9 long-press actions.

A repeat event before that threshold is consumed as part of the active key
press but does not trigger digit output or shortcut selection. Once a key has
triggered its long-press action, the existing per-key flag prevents key-up from
also running the short-press behavior.

The same rule applies to the physical OK/confirm key used to enter selection
mode, so laggy repeat events cannot trigger selection mode before the configured
long-press threshold.

## T9 Candidate UI Snapshot Pipeline Design

`CandidatesView` acts as an Android view adapter for T9 candidates. It may
collect measured view inputs, current Fcitx candidate data, and mode-specific
runtime snapshots, but it should not decide which T9 candidate surface wins or
how preview, paging, focus, and visibility rules combine.

The snapshot pipeline consumes an immutable `T9CandidateUiInputSnapshot` for a
single refresh pass. This keeps collection order separate from UI decisions and
prevents refresh bugs where one state source updates while a second source
rebuilds stale render state. The first narrowing slice preserves all existing
visual algorithms and only moves the builder seam from scattered getters to a
single collected snapshot.

The Chinese T9 pinyin filter row is represented as a render-ready T9 Pinyin Row
Surface. Width, folded ellipsis, focus-window display, highlight, and content
readiness are planned together before `CandidatesView` touches Android views.
This avoids repeated call-site recombination of the short-Hanzi-page folded row
rules.

Render-time pinyin row mutations are selected by a T9 Candidate Render Pass.
The pass decides whether the pinyin row should render, sync layout, clear, or
stay untouched before the Android renderer performs the view operations. This
keeps the pinyin reveal retry contract testable while preserving the existing
visual output.

Owned bottom-row interactions go through a T9 Candidate Interaction Controller.
The snapshot pipeline still owns the interaction state; the controller is the
adapter seam that applies pipeline interaction results to host side effects.
This keeps `CandidatesView` from carrying parallel Smart English, punctuation,
and bulk-selection dispatch branches while preserving fallback handling for
ordinary non-owned Rime candidates.

## Chinese T9 Scheme Module Design

`ChineseT9Scheme` is the stable scheme identity used by the key flow and
presentation pipeline. It classifies Rime's cached sub-mode as Pinyin, Stroke,
or Zhuyin and exposes only scheme-level decisions: which digits compose,
whether `0` confirms, how a literal code preview is validated, and how raw
input is displayed. Rime schema names and spelling encodings stay behind this
Module.
Fcitx IM-change events update this identity once at the adapter boundary; the
physical-key and candidate paths only read the cached value and never call
`runImmediately` to rediscover it.

`ChineseT9CompositionCoordinator` remains the service-facing Interface. It
delegates Pinyin to the existing resolved-segment implementation and delegates
Stroke/Zhuyin to a compact raw-code session. Switching schemes clears the old
session and presentation cache before the next key. The service and
`CandidatesView` do not branch on Rime schema details.

The Physical T9 Key Flow receives `ChineseT9Scheme` in its immutable state
snapshot. It emits the existing forwarding and candidate commands according to
the scheme contract. The reducer remains O(1): it does not query Rime, read a
dictionary, build candidates, or measure views.

Pinyin publishes its top reading and reading-filter row. Zhuyin publishes the
same generic reading-row contract from a local legal-syllable resolver rather
than fabricating Pinyin state or waiting for a candidate comment. Stroke
publishes only its deterministic code preview. Their Hanzi candidates, numeric
shortcut labels, paging, focus, width budgeting, and punctuation follow-up
reuse the T9 Candidate UI Snapshot Pipeline unchanged.

The Rime data Adapter provides two schemas:

- `t9_stroke` maps `1..5` to one exact project-curated `hspnz` stroke prism. The
  generated table is derived from a pinned official `rime-stroke` revision,
  contains complete Han ideographs instead of Unicode strokes/components, and
  carries project ranking weights. A bounded Lua query Adapter expands up to
  two `6` tokens at runtime, merges a small
  result pool by candidate quality, and leaves exact input on the native table
  translator path. Wildcard combinations are never compiled into the prism.
- `t9_zhuyin` converts `rime_ice` Pinyin spellings to Zhuyin, then collapses
  symbols into the agreed `0..9` phone groups. Candidate comments are formatted
  back to Zhuyin. A local `T9ZhuyinResolver` owns immediate legal reading paths
  and automatic syllable boundaries; comments only rerank or validate those
  paths and never serve as the first-frame parser.

Both schemas use the existing Rime candidate engine and user dictionary. There
is no Android-side dictionary scan or parallel candidate renderer. The existing
Dictionary Switch remains the complete schema menu, while the app's configured
short-`#` cycle provides the fast path among selected Chinese schemes without
lengthening the Chinese/English/number top-level cycle.

Candidate readiness is also scheme-owned. Pinyin validates Latin candidate
readings, Stroke validates the rendered stroke preedit, and Zhuyin validates
Bopomofo comments against the phone groups. This keeps stale frames out without
forcing non-Pinyin candidates through Pinyin inference. Only Pinyin uses the
bulk prefix-filter session; Stroke and Zhuyin use the shared local page budget
directly, avoiding an unnecessary asynchronous fetch before their first frame.

The local pager and Pinyin bulk loader have independent reset semantics. A bulk
reset must not erase a raw scheme's local page/cache; the complete candidate
pipeline reset remains the explicit operation that clears both. Entering a
waiting generation invalidates only the owned interaction snapshot while the
renderer may retain the previous complete frame until its replacement is ready.
This preserves the no-flash visual contract without allowing stale OK or
shortcut selection.

`ChineseT9CompositionTicket` identifies one candidate generation by scheme,
raw sequence, and monotonic session revision. Candidate readiness and queued
selection both validate this ticket. Input-panel and candidate events may
arrive in either order, so the loading Module retains the latest pair and
re-evaluates after either side changes. Stroke wildcard freshness compares each
concrete preedit stroke against the requested pattern; `6` matches exactly one
stroke rather than accepting an arbitrary non-empty page.

One scheme-transition operation owns cached identity, composition cleanup,
loading/source invalidation, focus reset, and presentation removal. Startup
also initializes the cache from Fcitx's existing `inputMethodEntryCached` when
the service attaches after the original IM-change event. The physical-key hot
path continues to read only this cache.

Raw Stroke and Zhuyin composition stores only scheme-owned digit codes. Short
`#` does not enter either engine: it commits the validated top preview as
literal code and clears composition. Pinyin uses the same command and commits
its selected Latin reading. This keeps the physical gesture independent of
Rime punctuation and delimiter configuration. Candidate selection is posted to
the service's serialized Fcitx queue with a composition and engine-event
ticket; an obsolete request is dropped before its original index reaches Rime.

## Chinese T9 Scheme Switching Design

Rime's Dictionary Switch remains the complete installed-schema menu. The app
adds a smaller user-configured Chinese T9 cycle containing Pinyin, Stroke, and
Zhuyin in fixed order. At least one item must remain selected; Pinyin alone is
the compatibility-preserving default.

Physical `#` has a state-shaped contract:

- composing Chinese: commit the active scheme's literal code preview;
- idle Chinese: perform Return;
- long press: clear composition and switch the top-level T9 mode.

Physical `*` keeps punctuation on short press. Idle long `*` activates the next
configured Chinese scheme; with only one configured scheme it commits a literal
star. Composing and punctuation-session long `*` retain literal-star input so
the shortcut never silently destroys an active composition or candidate row.

The key flow emits semantic commands only. A scheme-cycle Module chooses the
next configured scheme, then the Fcitx adapter activates the matching action
from the cached `fcitx-rime-im` menu. Scheme changes still enter through the
existing IM-change lifecycle, so there is one source of truth for composition,
loading, focus, and presentation reset. The space-bar mode label shows the
current Chinese scheme to make a successful shortcut visible without a toast
or another transient overlay.

The Pinyin Rime schema is named `拼音九键`. `中文九键` remains only as a
classification alias for already-compiled user data during deployment.

## Stroke And Zhuyin Local Modules

`T9StrokeCodec` is a pure O(1) Module. It is the only Android source for the
`1..6` token map, display symbols, unknown-stroke semantics, and literal-code
validation. It does not load Hanzi candidates. The generated Rime table is
validated before packaging, so runtime candidate code does not retain a second
non-Han filtering path.

`T9ZhuyinResolver` builds a compact immutable index from legal toneless
syllables. Lookup accepts partial final syllables, automatically segments prior
complete syllables, bounds the number of retained paths, and returns a stable
snapshot keyed only by raw digits. Candidate comments may move a matching path
to the front without changing the underlying path set. Resolver work is
independent of the Hanzi dictionary size and remains synchronous on key input.

A resolver result has exactly three presentation states:

- valid: selected Bopomofo reading, reading options, and a candidate ticket;
- pending engine data: the valid local reading remains visible without a
  no-match label;
- invalid: honest raw digits plus a localized, non-focusable no-match row.

Late Rime events must match the composition ticket before replacing a valid or
invalid state. An invalid state never exposes OK, numeric shortcut, or paging
actions, and short `#` cannot commit its internal digit sequence.

External custom fonts use the chosen font for supported glyph runs and the
system family for unsupported runs. The same fallback plan feeds Android text
measurement and TextView rendering so a legitimate rare Han character cannot
collapse the final candidate focus geometry.

The settings home uses a dedicated dial-pad icon for Chinese T9 Schemes rather
than repeating the generic language icon used by the complete input-method
list.
