# Design

## Scheme-Aware Physical Key Design

Physical keys map to domain commands through the active input scheme rather
than carrying one global meaning. `*` is the shared symbol entry point for text
schemes, while `1` remains available to the scheme: Pinyin apostrophe separator,
Stroke horizontal stroke, Zhuyin `ㄅㄆㄇㄈ`, or English case cycle. Short `1`
appends the separator during Pinyin composition and emits a literal apostrophe
while idle; long `1` retains shortcut/literal-digit behavior. Number mode
remains numeric and keeps its current `*` operator behavior.

Transient mode feedback is hosted by a dedicated root overlay attached after
both `InputView` and `CandidatesView`. The overlay owns its badge animation and
is non-interactive, so candidate and keyboard touch handling remain unchanged.
Mode, scheme, selection, and English case feedback all use this one topmost
surface instead of embedding duplicate badges in feature-specific views.

Release 4.2.0 is packaged as four signed ARM APKs: app and Rime plugin for
`arm64-v8a` and `armeabi-v7a`. Evergreen behavior remains in the README and
Baidu installation guide; version-specific additions and fixes live only in
`release-notes-v4.2.0.md`. Generated APKs are staged locally but remain ignored
by Git, while version metadata, notes, and staging readmes are committed and
tagged.

The performance-first release begins with a deep input-latency tracing Module.
Its Interface follows one latest T9 input generation through decision complete,
optional source event, render-ready snapshot, Android render, and the following
frame callback. The Module owns generation replacement, stage ordering,
aggregation, percentile calculation, and summary logging. Callers only announce
stage transitions; they do not build metric names or aggregate samples.

The normal trace mode does not activate the existing nested section timers.
Detailed section timing is an explicit diagnostic option because dozens of
synchronized measurements in the hot path distort low-end-device results. A
complete trace is published only when the same generation reaches the frame
callback; stale callbacks cannot complete a newer input.

Performance work proceeds through six deep Modules rather than scattered local
micro-optimizations:

1. Input latency trace owns generation identity and stage aggregation.
2. Smart English persistence owns immutable in-memory snapshots and serialized
   asynchronous writes; lookup and commit paths perform no filesystem work.
3. Physical T9 state capture owns mode-specific snapshots and reads only fields
   required by the active mode plus shared selection state.
4. Candidate refresh generation owns source acceptance, snapshot publication,
   render completion, and stale callback rejection.
5. Candidate surface geometry owns measured observations and publishes one
   frame geometry decision per generation.
6. Chinese engine operations own serialized Fcitx submission, composition
   tickets, result acceptance, and the resulting UI action.

These Modules keep the existing visual and key contracts. Their purpose is
locality and predictable hot-path cost, not another candidate UI design.

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

Pinyin publishes its top reading and reading-filter row. Zhuyin publishes a
candidate-driven top preview without a reading-filter row; its grouped digits
do not identify one honest local transcription before Rime ranks the Hanzi
candidates. Stroke publishes only its deterministic code preview. Their Hanzi candidates, numeric
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
  back to Zhuyin and preserve Rime's apostrophe syllable separators. A local
  `T9ZhuyinResolver` validates whether at least one legal segmentation exists;
  the focused candidate comment remains the sole reading presentation source.

Both schemas use the existing Rime candidate engine and user dictionary. There
is no Android-side dictionary scan or parallel candidate renderer. The existing
Dictionary Switch remains the complete schema menu, while the app's configured
short-`#` cycle provides the fast path among selected Chinese schemes without
lengthening the Chinese/English/number top-level cycle.

Candidate readiness is also scheme-owned. Pinyin validates Latin candidate
readings, Stroke validates the rendered stroke preedit, and Zhuyin validates
Bopomofo comments against the phone groups. This keeps stale frames out without
forcing non-Pinyin candidates through Pinyin inference. Only Pinyin uses the
bulk prefix-filter session. Stroke uses the shared local page budget directly;
valid Zhuyin waits for its ticket-matched engine frame, then uses that same
local paging path without a second cross-page reading filter.

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

`T9ZhuyinResolver` builds compact immutable complete-code and prefix-code sets
from legal toneless syllables. Lookup accepts a partial final syllable and uses
bounded dynamic programming to answer only whether at least one legal
segmentation exists. Its separate option API materializes a bounded reading set
once per raw-code mutation. Both result types are cached by raw digits, remain
independent of Hanzi dictionary size, and keep ordinary key validation
synchronous and allocation-light.

A resolver result has exactly three presentation states:

- valid: no speculative preview; wait for the matching candidate ticket;
- pending engine data: preserve the prior atomic frame rather than publishing
  a guessed reading or no-match label;
- invalid: honest raw digits plus a localized, non-focusable no-match row.

Once a valid Rime frame arrives, its focused candidate owns the top preview.
Zhuyin comments are normalized across apostrophe or whitespace syllable
boundaries; a candidate with no comment may preview its visible text, such as
an emoji. Focus movement and preview therefore consume one candidate snapshot
instead of reconciling an independent local reading selection.

### Default Zhuyin reading filter

`T9ZhuyinReadingFilterSession` owns the precision interaction. Every raw-code
mutation replaces its bounded resolver snapshot, so a valid composition exposes
reading options by default and an invalid composition exposes none. Focus
navigation is shared with Pinyin: Up selects the reading row, Down selects the
Hanzi row, and neither gesture changes visibility. Selecting an option records
one normalized reading prefix while leaving the row present. All raw-code
mutations reset the previous selection.

The filter options are complete or final-partial legal reading combinations
for the whole raw digit sequence. They are not independent symbol choices for
each key. Their resolver order is stable for a raw code; delayed Rime candidate
updates must not reorder the visible row and cause a misleading flash.

Candidate matching preserves the complete-versus-partial distinction. A
selected complete final matches the complete candidate syllable exactly, while
a final-partial option matches a candidate final by prefix. This prevents
selecting `ㄋㄧ` from also admitting `ㄋㄧㄠ`, while a one-key partial such as
`ㄏ` can still narrow to `ㄏㄠ` and other valid completions.

The existing Chinese bulk candidate pipeline is reading-scheme neutral:
Pinyin and an explicitly selected Zhuyin reading both provide normalized
prefixes and a scheme-owned candidate matcher. Zhuyin requests the cross-page
bulk source only after selection. Merely displaying the unselected row keeps
the current candidate page and bubble geometry, while the shared top-row
renderer handles focus and horizontal movement.

The Android row Adapter tracks both the logical option window and the visual
plan's displayed chip count. Cold reveal waits for the displayed count because
a focused folded viewport intentionally materializes only a subset; using the
logical count would leave a valid default row permanently invisible.

For an unfocused folded row, `minVisibleChips` is a lower bound rather than a
fixed count. A pure folded-preview planner receives the measured chip widths,
spacing, stable row width, and measured hint reserve, then returns the largest
whole-chip prefix that fits before the ellipsis. The renderer derives the hint
position from that same prefix, so width policy and pixels cannot disagree.
Focused navigation removes the hint but keeps the same row width and exposes a
sliding whole-chip window.

Late Rime events must match the composition ticket before replacing a valid or
invalid state. An invalid state never exposes OK, numeric shortcut, or paging
actions, and short `#` cannot commit its internal digit sequence.

External custom fonts use the chosen font for supported glyph runs and the
system family for unsupported runs. The same fallback plan feeds Android text
measurement and TextView rendering so a legitimate rare Han character cannot
collapse the final candidate focus geometry.

Stroke adds one device-local eligibility pass before the candidate pager. It
uses the same configured candidate Typeface as measurement and rendering,
caches `hasGlyph` by candidate text for the lifetime of the input view, and
returns `T9PagedCandidates` with original Rime indices. This is source
normalization rather than a visual hide: every downstream width, page, focus,
shortcut, and commit decision sees the same filtered candidate list. The Rime
generator keeps complete supplementary Han but assigns it minimal weight so
ordinary portable Han occupies the early pages.

The settings home uses a dedicated dial-pad icon for Chinese T9 Schemes rather
than repeating the generic language icon used by the complete input-method
list.

## T9 Candidate Publication Dispatch

`T9CandidateRefreshGeneration` owns coalescing and freshness; Android animation
timing is not part of its domain contract. Its Android Adapter posts one normal
main-queue callback for a generation. Input-panel and candidate callbacks in
the same queue can still coalesce, while `ChineseT9CandidateFrameGate` remains
the sole authority that decides whether the source is complete enough to
publish. A newer generation invalidates the old callback exactly as before.
When that gate accepts a ticket-matched engine pair on the main thread, the
generation Module may consume the pending generation and publish it
synchronously. The posted callback then fails its generation check and cannot
publish a duplicate. This immediate path is not available to incomplete source
events or speculative local reading data.

The renderer publishes one complete candidate surface. It does not introduce a
fast partial reading-row frame. The Canvas Reading Row reports synchronous
content geometry as part of its render plan. When candidate width and Canvas
geometry are ready, the row may participate in the same positioned reveal as
the Hanzi row. A genuinely missing width keeps the whole surface pending and
uses the existing deferred retry path.

The shortcut toolbar performs at most one explicit hierarchy measurement for a
new content structure. That measurement yields exact child bounds; the stable
tail policy then returns the geometry width immediately and sets the root's
minimum width for Android's upcoming layout pass. The geometry Module consumes
that stable width, so the reading row and toolbar retain one width source
without a second synchronous measurement.

The low-overhead transaction trace marks Chinese source readiness only when the
ticket-matched candidate/input-panel pair releases the Candidate Frame Gate.
This keeps engine wait separate from queued snapshot publication and makes the
next bottleneck visible without enabling high-overhead nested section timing.

The Chinese Bulk Candidate Loader is a cross-page reading-filter Module, not a
default Pinyin source. Pinyin and Zhuyin request it only when their composition
snapshot carries at least one selected/resolved reading prefix. With no prefix,
the accepted Rime page flows directly into the local width/character pager.
This keeps ordinary first-key publication on one engine event while retaining
`selectFromAll` and bulk original-index mapping for actual cross-page filters.

## Reading Row Geometry Cache

The Pinyin Row Android Adapter treats focus as a transition, not a general
request to rebuild row content. A same-focus render only synchronizes highlight
state. Entering the reading row replans its focused viewport; leaving it
replans the folded bottom-focused viewport. This preserves the existing visual
contract while preventing the initial bottom-focused frame from rendering the
same row twice.

`T9CandidateSurfaceGeometry` owns reusable reading measurements. Its cache is
private to one geometry instance and therefore one stable input-view font
configuration. A bounded per-string width cache avoids repeated `TextPaint`
work across nearby reading windows, while last-value row-width and chip-width
snapshots avoid rebuilding identical measurement aggregates. Keys include the
reading text and every metric that affects the result; candidate content,
focus-window planning, and Android rendering remain outside the cache.

## Physical Input And Cold-start Performance Design

The existing Physical Input Responsiveness Module becomes the common lifecycle
for accepted physical T9 actions. A transaction records a semantic path and one
completion kind: editor effect, input-surface frame, candidate frame, or
Fcitx-source candidate frame. Physical T9 Key Flow continues to emit commands;
it does not time Android work. Command execution and platform adapters mark the
owned completion boundary, and a newer accepted input invalidates an older
unfinished transaction.

Fcitx readiness and Rime availability are separate states. The Rime adapter
publishes deployment state and active schema identity. Chinese T9 engine
operations accept input only against a ready generation and revalidate their
composition ticket after asynchronous readiness. Input-surface creation does
not wait for Rime deployment, while a late ready transition reliably refreshes
the active Chinese session.

The main and Rime plugin APKs may update independently. If a loaded older Rime
addon lacks the availability export, the native Adapter catches the addon-call
exception and publishes `Unavailable` instead of terminating the IME process.
This is a fail-closed compatibility guard, not a fallback readiness path: Rime
operations stay gated until a plugin implementing the explicit contract is
installed.

`KeyboardWindow` remains the owner of layout creation, attachment, detachment,
and caching, but its implementation materializes one requested keyboard at a
time. Picker catalogs are independently lazy and are loaded only with their
picker. Feedback preference observation remains eager enough to preserve user
settings, while sound decoding starts after the first input-surface frame and a
not-yet-loaded sound is skipped rather than blocking a physical key.

Fcitx publishes one immutable cached-state revision containing the current
input method, status actions, preedit/input panel, and Rime availability. The
connection exposes that revision and the event flow without `runBlocking`.
Input views seed themselves from one revision and then consume events, avoiding
both main-thread dispatcher entry and internally inconsistent multi-read state.

`DataManager` remains the Data Installation Module. It uses a persisted source
fingerprint that includes the app descriptor and installed plugin identities.
An equal, completed fingerprint bypasses plugin descriptor parsing, hierarchy
construction, copies, and descriptor writes. Any app/plugin version change,
missing completion marker, or interrupted install uses the existing complete
diff-and-copy implementation. Android-only T9 dictionary assets do not enter
the native installation plan.

The completion record and merged descriptor use atomic replacement. The record
contains the canonical plugin package version/update identities and the loaded
plugin metadata required to start native Fcitx, so the fast path does not need
to reopen plugin resources. It is created only when every discovered plugin
has a successful installation outcome. The data-descriptor build Module treats
an excluded directory as a subtree, allowing `t9/` to stay in the APK while
remaining absent from the native filesystem hierarchy.

Local candidate selection produces a render-ready selection frame from the T9
Candidate UI Snapshot Pipeline. Content and geometry remain untouched when
only the cursor changes. T9 Punctuation Lifecycle replaces one candidate
source atomically; it requests a global transient clear only when incompatible
composition must actually be discarded.

The local frame is accepted only when candidate content, visibility, focus, and
the reading-row snapshot still match the last complete frame. It may update the
candidate cursor and top preview directly through the pooled shortcut toolbar.
The established final-chip focus scale may perform its bounded tail measurement
without rebuilding source pages or candidate geometry. Page transitions and
any failed invariant continue through one complete snapshot publication.

Smart English Lifecycle publishes one immutable snapshot keyed by input and
content revisions plus dictionary, prediction, readiness, and case generations.
Ranked candidates, cursor validity, paging data, and presentation derive from
that publication. Cursor-only revisions reuse the candidate array and stable
content key; casing changes reuse the raw lookup but republish transformed
content. Candidate UI consumes this single snapshot rather than asking for
paged candidates and presentation separately.

The built-in English dictionary uses one ordered exact-sequence index. Prefix
candidate pools are bounded, computed on demand, and retained in an LRU; common
prefixes are warmed off the input thread. This avoids the eager all-prefix
memory expansion while preserving exact order, the learned-word overlay,
candidate quality ranking, and pair-frequency reranking. Dictionary and
prediction Modules publish monotonic generations so an unchanged input can
invalidate stale content without broad cache clearing.

Physical Delete Coordinator owns the precedence of resolved-segment reopening,
active-composition pass-through, empty-editor IME hiding, and direct editor
deletion. It captures at most one Editor Snapshot after local state proves an
editor read is necessary. The Android adapter alone performs InputConnection
IPC and applies the resulting cursor prediction/delete plan; password deletion
uses the same plan rather than a parallel editor-read implementation.

Number Mode Controller commits every operator immediately. For `=` and `≈`, it
then defers the editor read until after the current input turn and performs
expression parsing/formatting on a computation dispatcher. One monotonic
generation ticket owns the read, calculation, and result-choice publication;
newer input, panel dismissal, mode changes, and input lifecycle changes cancel
that generation. The controller strips its just-committed operator when the
editor has already applied it, so both immediate and delayed InputConnection
visibility produce the same expression. These policies stay behind their
existing domain Modules rather than returning to `FcitxInputMethodService`
condition chains.

## Chinese T9 Output Script Defaults

`ChineseT9OutputScript` is the persisted product preference shared by all
Chinese T9 schemes, with one value stored for Pinyin, Stroke, and Zhuyin.
`ChineseT9OutputScriptPolicy` is the scheme policy Module. Its Interface maps a
scheme and product script to one typed Rime option assignment. Its
Implementation owns the differing option names and polarities, so neither the
settings UI nor `FcitxInputMethodService` knows that Stroke simplification is
inverted relative to Pinyin/Zhuyin traditionalization.

`ChineseT9OutputScriptSession` owns only asynchronous request identity. Scheme
activation, Rime-ready, and active-preference changes create a generation-tagged
request. Before the serialized Fcitx job applies it, the session verifies that
no newer request and no scheme transition made it stale. The Android/Fcitx
Adapter also verifies that Rime is still the active input method, then calls the
typed `FcitxAPI.setRimeOption` Interface.

The Rime plugin Adapter applies that option to the active Rime session. It does
not parse translated status-action labels or infer state from action checkbox
fields, because script actions expose neither reliable checkable state nor a
stable language-independent direction label. An older independently installed
plugin that lacks the new export fails closed and logs the mismatch; there is
no status-action fallback. Consequently a later manual Rime toggle remains in
effect until the user enters the scheme again. No output-script work runs
during a physical-key decision or candidate frame. The complete Chinese T9
preference category participates in the existing device-protected preference
sync, preserving the same scheme subset and script defaults during Direct Boot.

## Startup Performance Transaction

`StartupPerformanceTrace` is the Cold-start Transaction Module. Its Interface
accepts only named startup stages and lifecycle milestones, exposes one stable
snapshot, and owns process generation, first-occurrence rules, paired stage
duration, partial/complete publication, and Android Perfetto sections. Callers
do not pass arbitrary timer names or calculate elapsed time.

`FcitxApplication`, the Data Installation Module, native Fcitx startup,
`Fcitx`, and the input-window lifecycle are Adapters at this seam. They report
their own semantic transition and do not know how stages are combined. The
Implementation uses the elapsed-realtime clock shared with Android's process
start timestamp, so all offsets use one time base across threads.

The first input-surface frame and Rime-ready milestone are independent. If the
surface appears first, the Module publishes one partial snapshot and later one
complete snapshot; if Rime is already ready, it publishes only the complete
snapshot. Repeated lifecycle calls are ignored for the process generation.
The existing responsiveness preference gates publication, not capture, because
the preference itself is unavailable during the earliest Application stage.

Input-surface attribution remains part of the same transaction rather than a
second timer Module. Nested named stages distinguish navigation-bar evaluation,
input view construction/attachment, candidate view construction/attachment,
and mode-indicator replacement. The first occurrence remains authoritative for
process-cold startup; later theme or preference recreations cannot overwrite it.
Only the largest measured nested stage is eligible for the next behavior change.

The measured selection is `InputView` creation. Its final attribution pass
measures dependency-scope setup, active `KeyboardWindow` materialization, and
root input-tree assembly inside the same transaction. Unmeasured constructor
time remains an explicit residual for Kotlin property initializers. This keeps
the instrumentation Interface semantic while making it possible to distinguish
an expensive active keyboard from general class loading and chrome creation.

`ConcreteUniqueComponent` is the Input Dependency Identity Module. Its
Interface is the existing `IUniqueComponent.type`; its Implementation binds the
identity directly to the final runtime class instead of asking the external
dependency library to rediscover generic type arguments through Kotlin
reflection. Static input components and `UniqueViewComponent` use this base.
Dynamic `InputWindow` bases apply the same concrete-class identity because they
already have a separate class hierarchy.

`DynamicScope` remains the window lifecycle seam: pickers and keyboard windows
can still enter and leave the scope. Only identity discovery changes. Equality,
hashing, dependency arrival order, receiver registration, and view ownership
remain unchanged. Focused tests lock down concrete type identity and same-type
uniqueness so the optimization cannot silently alter scope semantics.

ADB-imported Rime configuration is a development Adapter concern, not a Rime
lifecycle fallback. The repeatable debugging workflow normalizes imported
files to group-writable mode because Android exposes the app through the
`ext_data_rw` group while ADB creates files as `shell`. Production keeps the
existing external app-data location so users can replace Rime configuration;
the runtime does not relocate or copy compiled state to hide an unwritable
source tree.

The Data Installation Module remains fail-closed while its fast path is
attributed. Named startup stages measure main descriptor loading, plugin package
identity discovery, merged descriptor loading, installation-state loading, and
completion cleanup inside the existing data-installation stage. No cached value
is trusted or removed until the largest measured operation is known.

The Data Installation Fingerprint Module validates four independent inputs:
the build-emitted main descriptor identity, the exact installed
merged-descriptor content digest, canonical plugin package identities, and
restorable loaded-plugin metadata. The build task emits
`descriptor.json.sha256` beside `descriptor.json` but excludes the companion
from the described native hierarchy.

The steady-state Adapter never decodes a `DataDescriptor`. It reads the tiny
build fingerprint, hashes the installed descriptor stream, and decodes only the
small installation state. The full installation Implementation remains the
sole owner of descriptor decoding, hierarchy merge/diff, file copies, and the
atomic completion record. Earlier JSON records perform one full migration
rather than receiving a compatibility fallback.

Chinese Scheme Cycle keeps confirmed state and immediate feedback separate.
The long-press command requests the next configured scheme and immediately
shows that target through the existing transient mode indicator, matching the
top-level T9 Mode Coordinator's feedback timing. It does not optimistically
change `activeChineseT9Scheme` or the space-bar label. The later Rime
input-method-change event remains authoritative, and a rejected action only
clears the pending cycle request.

The Startup Performance Transaction splits installation-state byte reading
from decoding. Measurement selected decoding, so `DataInstallationStateCodec`
becomes the private persistence Module behind the fingerprint Interface. Format
3 uses a fixed magic value, explicit version, bounded record counts, and
length-prefixed strings plus a payload checksum. Decode returns no state for
malformed, corrupt, truncated, oversized, trailing, or old-format data; Data
Installation then takes its existing complete path and atomically publishes a
fresh record last.

The proof itself no longer carries serialization-only version state or the
unused logical merged-descriptor identity. Its runtime Interface contains only
the build descriptor identity, exact installed descriptor-file digest,
canonical plugin package identities, and restorable plugin metadata. The JSON
codec remains only for full `DataDescriptor` work and is not initialized by an
unchanged fast path.
