# Analysis

## Current Task: Scheme-Aware `1` and `*` Roles

The punctuation key cannot remain globally assigned to `1`. Future Stroke
input needs `1..5` for horizontal, vertical, left-falling, dot/right-falling,
and bend strokes, with `6` as the unknown-stroke wildcard. Future Zhuyin input
also needs `1` for the `ㄅㄆㄇㄈ` group. English does not use short `1` for a
letter group, so it can use short `1` for case selection instead.

The stable cross-scheme role is therefore `*` as the text-symbol entry point,
not `1` as punctuation. The change is not a literal global key swap: Pinyin
keeps composing `1` as its syllable separator, Stroke and Zhuyin use `1` as
input data, English uses short `1` to cycle case, and number mode keeps `1` as
the digit. Long-press digit shortcuts remain available, including long `1` for
the first visible candidate or literal `1` fallback.

Success criteria for the implemented modes:

- Pinyin short `1` still separates an active composition but no longer opens
  punctuation while idle.
- English short `1` cycles `abc -> Abc -> ABC -> abc`; long `1` keeps candidate
  shortcut/literal-number behavior.
- Text-mode short `*` commits pending text without a space when necessary and
  opens the mode-appropriate punctuation candidates.
- Pressing short `*` again while punctuation is visible toggles its Chinese or
  English set; long `*` commits a literal star.
- Number mode keeps its existing digit, literal-star, and operator-panel rules.
- The physical-key decision path remains synchronous and allocation-light; it
  emits semantic commands and does not perform dictionary lookup, Rime work,
  or Android view measurement.

That key-remapping slice intentionally stopped before adding Stroke and Zhuyin
engines. Their agreed key maps and performance architecture were recorded in an
ADR first; the current follow-up now implements those adapters without another
physical-key rewrite.

## Current Task

The active work is a T9 input-method follow-up covering hardware keyboard
passthrough, smart English T9, Rime state recovery, and physical-key
long-press robustness on laggy devices.

## Physical-Key Game Mapping Passthrough

User report: Java emulator or game apps that map a physical keyboard to game
controls can receive IME text input instead of game controls.

Finding: `InputDeviceManager` already has enough editor state to identify
non-text `TYPE_NULL` contexts where no input view was started, but
`FcitxInputMethodService.onKeyDown()` and `onKeyUp()` still forwarded physical
keys into Fcitx.

Success criteria: when there is no real text input session, hardware keys pass
through to the app/system instead of being remapped or forwarded to Fcitx.
Normal text fields and active T9 sessions keep their existing interception.

Follow-up bug: when a physical Back/Backspace key arrives immediately after an
editor starts, but before `onStartInputView()` marks the input view as started,
the key can be treated as passthrough. On devices that report Backspace as
`KEYCODE_BACK`, this becomes an app/system back action instead of delete. Input
mode is now tracked as an input session from `onStartInput()` for normal
editors, while `TYPE_NULL` passthrough remains available when no real input
session or input view exists.

Physical-key rules audit: the mode-specific key-down/key-up split is the main
fragile seam. Number mode still had direct `InputConnection.commitText()` calls
on key-up, bypassing the service commit path used by Chinese and English modes.
English smart punctuation also treated `*` as a pending-punctuation control on
key-down but as English shift on key-up. Delete handling recognized
`KEYCODE_FORWARD_DEL` in some places but not in pending-punctuation and smart
English cancellation paths. These inconsistencies are now normalized.

Long-term refactor direction: key classification is centralized in
`PhysicalT9KeyPolicy`, and physical T9 decision/execution routing now lives in
`PhysicalT9KeyHandler`. `FcitxInputMethodService` acts as the host adapter for
IME side effects such as committing text, refreshing candidates, and forwarding
Chinese T9 keys to Fcitx. The handler owns the key-down/key-up state for
digit/star/pound long press, smart-English deferred digits, candidate
navigation consumption, and pending-punctuation controls. Its core API uses a
plain `KeyInput` snapshot so the physical-key state machine can be tested
without Android runtime `KeyEvent` instances.

## Smart English T9

User request: keep the current simple English multi-tap mode, but add a smarter
English T9 mode inspired by Traditional T9. It should be switchable from the
compact status settings and should learn special words from real use.

Reference finding: TT9-style predictive input is dictionary-based. Missing
words are solved by adding or importing words, not by guessing arbitrary
unknown spellings.

Dictionary follow-up: the initial built-in Kotlin list was too small. TT9's
English dictionary contains about 174k ASCII alphabetic words. The app now
stores an adapted grouped asset keyed by T9 digit sequence, generated from the
TT9 English CSV. The notice file under `docs/third-party/` records the original
SCOWL/OpenBoard/TT9 source notes.

Implementation direction: smart English is optional. Digit keys `2..9` build a
local digit sequence, dictionary candidates are shown in the candidate row, and
confirm/space/enter commits the selected word. Simple multi-tap remains the
fallback path for unknown words. Words typed in simple mode are learned when a
delimiter is committed.

Learning-management follow-up: the original "learn from real input" wording was
too vague for users, and learned words had no management UI. Learning is now
documented as a concrete flow: turn off Smart English, type a continuous word in
ordinary English multi-tap, then commit a delimiter such as space, punctuation,
or enter. The learned-word file can be managed from the top-level Dictionary
Management settings area. Password fields and editors that request no
personalized learning do not record words.

Candidate interaction follow-up: smart English should match the Chinese T9
candidate surface. The visible English candidate page uses the same T9
candidate budget setting as Chinese, shows numeric shortcut labels, supports
local page offsets, and lets long-press `1..9,0` commit the corresponding
visible candidate.

Follow-up bug: the converted English asset did not include essential
single-letter words such as `I`. Smart English also created the first
candidate sequence on key-down, so an empty-state long press could first create
candidates and then select one before the literal digit was committed. The
first digit is now deferred until key-up; long press from an empty sequence
stays literal, while long press with an existing sequence selects candidates.

Follow-up bug: smart English candidate navigation was still gated on a non-empty
digit sequence. This let the first page's up key fall through when no previous
page existed, and it left English punctuation candidates unreachable because
they are represented as pending punctuation rather than smart-English digits.
Smart English navigation now treats pending punctuation as an active candidate
state and consumes candidate navigation keys even at page edges.

## Rime/Default-Mode Recovery

User report: sometimes the IME appears not deployed or has no Rime mode, only
the default mode. Redeploying does not restore it, but switching away to
another system IME and back does.

Finding: system IME switching forces a broader Fcitx/Android input-method-state
refresh than the in-IME reload path. The app caches the current input method
and status actions, so a successful reload can still leave stale state visible.

Success criteria: after `reloadConfig()`, refresh the cached current input
method and status-area actions from Fcitx.

## Smart English Display Polish

User retest: smart English works, but showing the raw digit sequence feels
unfriendly.

Finding: digits are useful internal state, but the user is thinking in words or
letter groups. Showing `43556` as primary feedback makes the mode feel like
number input.

Follow-up finding: expanding every unresolved key into a full letter group,
such as `ghi def jkl`, is also unfriendly. It becomes long quickly and still
does not tell the user which exact word will be committed.

Success criteria: smart English prioritizes real word candidates. The
dictionary should offer longer words whose T9 digits start with the current
sequence, so `435` can already show `hello` or `help`. If no word exists, show
a compact no-match state instead of raw digits or a long letter-group string.

Follow-up display correction: the smart-English top reading row should show the
user's typed progress, not the whole highlighted prediction. The bottom
candidate row owns full-word prediction. For example, after pressing `3`, a
candidate such as `doing` can appear below, but the top row shows `d`; after
`43` with `hello` highlighted, the top row shows `he`.

## Physical Digit Long-Press False Positives Under Lag

User report: when the phone is hot and laggy, short physical-key typing can
leak digits during Chinese or English input, or trigger digit shortcut output
even though the key was not intentionally long-pressed.

Finding: physical T9 long-press logic treated `repeatCount == 1` as long
press. Under device lag or keyboard-driver repeat behavior, a repeat event can
arrive for a press the user experiences as short. The code did not verify the
actual held duration against the configured long-press delay.

Success criteria: physical digit, `*`, and `#` long-press actions fire only
when the event's held duration reaches the configured keyboard long-press
delay, and each held key triggers the long-press action at most once.

Follow-up finding: the physical OK/confirm key path for selection mode still
used `repeatCount == 1`. It should share the same held-duration gate.

## Stroke And Zhuyin T9 Expansion

The accepted key contract in ADR-0001 now needs a working engine and app-side
scheme seam. Stroke means mobile five-stroke input, not Wubi 86. Its physical
codes are `1..5` for horizontal, vertical, left-falling, dot/right-falling,
and bend; `6` is an unknown-stroke token. Zhuyin uses all ten digit groups, so
`0` cannot remain a universal Chinese confirmation key.

The installed Rime plugin already ships the official `stroke` dictionary. The
external Rime configuration can therefore add a numeric Stroke schema without
duplicating the large dictionary. The existing `rime_ice` dictionary can also
power Zhuyin by converting Pinyin syllables to canonical Zhuyin symbols and
then collapsing those symbols into the agreed phone-key groups. This avoids a
second large word dictionary and keeps user frequency learning in Rime.

Rime's table translator does not provide an arbitrary input wildcard. An
initial spelling-algebra prototype compiled one wildcard at each of twelve
positions. Device evidence rejected that design: deployment exceeded 1 GB RSS
and still had not produced the numeric Stroke prism after about a minute.
Wildcard support must therefore stay out of the compiled dictionary.

The revised Stroke Adapter keeps one exact numeric prism and expands key `6`
only for the current query through a Lua translator. Exact input retains the
native table-translator path. Wildcard input supports at most two unknown
positions, performs at most 25 indexed table queries, collects a bounded number
of results per branch, deduplicates, and reranks by candidate quality. This
makes deployment independent of wildcard combinations and confines the extra
cost to the uncommon wildcard query. The semantic `UNKNOWN` token remains in
the app contract; expansion is an engine Adapter detail.

The original Chinese composition Module was Pinyin-specific: it counted only
`2..9`, resolved Pinyin filter chips, and mirrored selected Pinyin into Rime.
Reusing it unchanged for Stroke or Zhuyin would create false filter rows and
stale key counts. The new scheme-aware coordinator keeps that deep Pinyin
implementation and delegates Stroke and Zhuyin to a compact raw-code session
and render-ready presentation Adapter. All schemes still publish the existing
candidate snapshot contract, so paging, bubble width, focus, shortcuts, and
rendering stay shared.

The current Rime schema action already provides the required user switching
surface: it is labelled Dictionary Switch in compact settings and focuses the
active schema in its menu. Adding the two schemas to `default.yaml` makes them
available without adding another top-level mode or a duplicate settings item.
The app can classify the cached Rime sub-mode using schema-owned display names;
classification is O(1) and performs no Rime call in the physical-key hot path.

Device deployment validated the revised engine design. The exact Stroke prism
compiled to about 6.9 MB in a few seconds while the debug process stayed near
322 MB RSS; the rejected wildcard-algebra build had exceeded 1 GB without
finishing. Runtime `6` expansion produced candidates for one and two unknown
strokes without a memory spike, and Zhuyin code `38` produced `好` with the
`ㄏㄠ` reading.

The first device pass also exposed two Pinyin assumptions outside composition:
candidate freshness only recognized Latin T9 comments, and every Chinese
scheme entered the asynchronous Pinyin bulk-filter pass. That left valid
Stroke/Zhuyin candidates hidden or needlessly delayed. Freshness is now
scheme-aware: Pinyin checks Latin readings, Stroke checks its engine preedit
(with a bounded wildcard exception), and Zhuyin checks Bopomofo groups. Stroke
and Zhuyin bypass the Pinyin-only bulk filter and render from the shared local
page budget. Active scheme classification is updated by Fcitx IM-change events
and cached, so keys and candidate frames do not synchronously cross to the
Fcitx thread.

A follow-up architecture audit found that merely bypassing the Pinyin bulk
request was insufficient. Resetting that request also reset the shared local
pager on every raw-scheme frame, defeating its cache and potentially losing a
user-selected page. Waiting frames retained the previous interaction snapshot,
so an invisible stale candidate could still receive OK or a numeric shortcut.
The loading gate also lacked an explicit composition ticket and accepted any
non-empty Stroke wildcard page, which was too weak under delayed event order.

The same audit found lifecycle edge cases that need one scheme transition seam:
a reconnected service can miss the original IM-change event, switching between
two schema names that both classify as Pinyin must still clear session state,
and candidate/loading/focus state must be invalidated together. The early
Zhuyin prototype also exposed that any engine delimiter would need matching
local state. The final product decision avoids that duplicate state entirely:
raw sessions store digits only and short `#` terminates the code by committing
its preview. Finally, asynchronous candidate selection must use the service's
serialized Fcitx queue and reject a selection ticket after newer input or a
scheme transition.

The follow-up implementation closes those lifecycle gaps. Bulk-source reset no
longer clears the local pager, candidate loading is keyed by a composition
ticket, hidden or pending frames invalidate their interaction ticket, and one
scheme-transition operation clears every transient candidate surface. Startup
also initializes the concrete scheme identity from the cached current input
method, covering reconnects that missed the original change event. Candidate
selection is serialized and validates both composition and shown-source
tickets before committing an original Rime index.

The final device pass used the freshly installed debug APK and the explicit
physical-keyboard source. A cold Zhuyin session accepted `38` immediately after
service reconnect and displayed `好 / ㄏㄠ`; center committed the selected
candidate. `2038` produced the ambiguous toneless phrase set and learned
`你好` to the first position after one selection. Zhuyin `0` remained a real
composition key, and short `#` committed exact Unicode `ㄋㄧㄏㄠ` rather than the
focused Hanzi. Stroke accepted exact input and key `6` wildcard input, consumed
unused short `7` during active composition, short `0` and center committed the
focused bottom-row candidate, and short `#` committed exact stroke symbols.
Backspace cleared both raw schemes without leaking to the editor. Short `*`
opened the shared Chinese punctuation row, whose Backspace cancellation also
remained intact.

One apparent Pinyin `#` failure was isolated to the test harness rather than
the key flow: repeatedly replacing the IME APK while Keep remained open left
the editor holding a stale `InputConnection`. Candidate UI still reacted, but
every direct editor commit, including punctuation, returned success without
changing the note. Force-stopping and reopening the editor recreated the
connection and restored all commits. Post-install QA must therefore reopen the
target editor and wait for the IME session before injecting physical keys; no
production fallback was added for this debugger-only lifecycle artifact.

The raw-scheme trace no longer contains a Pinyin bulk request or repeated
local-page rebuild after focus movement. On the target phone, a settled repeat
update measured about 4.35 ms; a cached focus move spent about 2.20 ms in
`buildState` and 21.22 ms overall. The remaining roughly 143 ms cold first
frame is dominated by font/layout/JIT startup rather than dictionary-size work
on the physical-key reducer.

## Chinese Scheme Commit And Quick Switching Follow-up

The user clarified that short `#` during Chinese composition means **commit the
visible input code literally**, not choose a Hanzi and not insert a reading
delimiter. This applies consistently to Pinyin, Zhuyin, and Stroke. The
existing device behavior does not satisfy that contract: after entering Pinyin
`43`, short `#` leaves `ge` composing and opens Rime's `#` punctuation menu.
Stroke currently consumes the key, while Zhuyin forwards it as an apostrophe
delimiter. These are three unrelated engine effects behind one physical-key
gesture.

The replacement must resolve the current top preview through the shared
presentation snapshot, validate that it is unambiguous for the active scheme,
commit it through the Android input connection, and clear both local and Rime
composition. Pinyin commits Latin reading text, Stroke commits the displayed
stroke symbols, and Zhuyin commits one resolved Bopomofo symbol per input key;
an unresolved fallback containing whole Zhuyin key groups is never committed.

Users also need a fast path when they deliberately use several Chinese input
schemes. The full Rime Dictionary Switch remains the escape hatch for every
installed schema. A new top-level settings page chooses the smaller ordered set
used by the physical shortcut. Device feedback rejected idle short `#` as that
shortcut because it removes Return. Idle short `#` must always perform Return;
during composition it commits the code; long `#` continues to cycle Chinese,
English, and number modes. Idle long `*` cycles the enabled Chinese schemes. If
only one is enabled, long `*` retains literal-star input instead of becoming a
dead gesture. Composing or punctuation-session long `*` also retains the
literal-star behavior, while short `*` remains the punctuation entry/toggle.

The quick-cycle path should activate the existing cached Rime schema action
rather than add another schema state store or call Rime synchronously from the
key reducer. The physical flow emits one semantic command only after a verified
long press, and the service resolves the next configured scheme at that
boundary. Normal digit input therefore gains no preference reads, action
scans, or engine round trips. Pinyin idle short or long `1` commits one literal
`1`; composing `1` remains its syllable separator.

Finally, `中文九键` is too broad now that Chinese mode contains three schemes.
The Pinyin schema is renamed `拼音九键`; classification keeps the old name as a
deployment-compatibility alias so an old compiled schema cannot temporarily
fall back to the wrong physical-key contract.

## Stroke Candidate And Zhuyin Resolver Follow-up

Device reproduction proved that the apparent blank Stroke candidates are not
empty strings. With raw code `1`, the third candidate committed U+18800 TANGUT
COMPONENT-001. The official `rime-stroke` dictionary intentionally includes
that component with code `h`. MiSans cannot draw it, and the current external
custom Typeface has no system fallback, so it renders as an empty slot and is
measured like a tiny final candidate. The system font renders a missing-glyph
box for the same value. UI blank filtering would therefore hide the symptom
while retaining invalid paging and selection data.

Stroke must use a project-owned generated Rime table derived from a pinned
`rime-stroke` revision. Generation keeps complete CJK unified ideographs,
normalizes compatibility ideographs, retains independently encoded radical
forms such as `亻`, `氵`, and `扌` at lower priority, and rejects dedicated
stroke symbols, component blocks, Tangut, and Latin entries. Common-character
weights are attached during generation. Validation fails the generation when
a non-Han code point leaks into the table. A later device sweep proved that
valid supplementary Han still needs a separate font-eligibility check because
Android glyph coverage varies by device.

Android does not need a second large Stroke candidate resolver. A small
`T9StrokeCodec` owns the deterministic digit-to-stroke map, unknown token,
preview, and literal commit validation. Rime's compiled table keeps candidate
lookup, user frequency, simplification, and paging off the Kotlin heap. Custom
font fallback remains independently necessary for legitimate rare Han
ideographs and must be shared by text measurement and rendering.

Zhuyin has the opposite shape: each digit represents several symbols, while
the set of legal toneless Mandarin syllables is small. A local resolver is
still useful for rejecting impossible digit sequences immediately, but it must
not guess which legal reading the user intended. The first resolver version
enumerated and alphabetically selected reading paths. Device evidence showed
that this produced a parallel and misleading UI: code `38` briefly displayed
`ㄍㄞ` while Rime's focused candidate was `好 / ㄏㄠ`, and `2038` displayed
`ㄉㄧ ㄍㄞ` while the focused phrase was `你好 / ㄋㄧ'ㄏㄠ`.

An exhaustive device sweep of all 100 two-key codes found no disagreement
between local legal-sequence validation and settled Rime candidate
availability. The defect was therefore presentation ownership, not the phone
key map. Normal predictive Zhuyin now waits for the ticket-matched Rime frame,
uses the focused candidate comment as the top preview, and exposes no permanent
reading-filter row. The resolver's normal key path only answers valid or
invalid in O(n) with bounded syllable lookahead. Invalid input retains its digits and shows a
non-interactive no-match state; valid input never flashes a speculative local
reading.

The TT9 Bopomofo branch supports this ownership boundary: normal predictive
mode presents ideogram candidates, while transcription filtering is a separate
interaction rather than an always-visible row. This project keeps explicit
confirmation and does not copy TT9's eager continuation acceptance.

The device sweep also found a separate Rime formatting defect. Phrase comments
use apostrophes between syllables, but `t9_zhuyin` treated only spaces as regex
boundaries. That left Latin `y` and `w` fragments in 232 observed comments.
Every comment transform now recognizes both separators, and the repeated
100-code sweep produced 10,704 candidate comments with zero Latin fragments.

## Default Zhuyin Reading Filtering

Candidate-driven preview fixes the misleading first frame, but grouped Zhuyin
still has more ambiguity than candidate paging alone should carry. The filter
must operate on legal reading combinations, not one symbol choice per keypad
digit. Per-key selection would expose invalid Cartesian products, require too
many physical actions, and lose syllable boundaries. For `38`, for example,
the meaningful filter values are readings such as `ㄍㄞ`, `ㄍㄠ`, and `ㄏㄠ`,
not separate selections from the `ㄍㄎㄏ` and `ㄞㄟㄠㄡ` key groups.

The first implementation kept this row hidden until Up was pressed. Device use
showed that the gesture was undiscoverable and inconsistent with the established
Pinyin interaction. Zhuyin must therefore publish its legal reading combinations
by default whenever the current code is valid. Up and Down only move focus
between the reading and Hanzi rows, exactly as they do for Pinyin; they do not
change row visibility. Left/right moves within readings and OK applies one
across candidate pages. Digit input, Backspace, candidate commit, scheme change,
and composition reset rebuild or clear the filter state so a choice cannot leak
into a newer composition.

Physical-device verification exposed a renderer assumption with a long,
folded reading row. The visual plan renders only the chips that fit the
viewport, while reveal readiness compared the adapter count with the larger
logical option window.
The row therefore stayed invisible even though focus had moved to it. Readiness
now follows the displayed chip count produced by the visual plan, preserving
the existing folded-row geometry for both Pinyin and Zhuyin.

### Folded reading preview must use its real viewport

Device reproduction with Zhuyin code `68` showed ten Hanzi candidates and a
wide candidate bubble, but the reading row still rendered exactly four chips
plus an ellipsis. The overflow decision correctly detected that the complete
reading list exceeded the bubble; the rendering contract then incorrectly used
`minVisibleChips` as a fixed preview count. Four chips are only the minimum for
the short-Hanzi-page edge case, not the preview size for every overflowing row.

The folded preview must therefore fit the largest whole-chip prefix inside the
actual stable row width while reserving the measured ellipsis and edge safety.
It may never show fewer than the configured minimum when that minimum defines
the bubble width. Focused navigation continues to use the same stable viewport.
This rule applies equally to Pinyin and Zhuyin because both use the shared
reading-row surface.

## Pinyin `1` Separator Contract

Pinyin already used short `1` as a syllable separator during composition, but
the idle branch still emitted the digit `1`. That split meaning is difficult to
learn and conflicts with the scheme-aware key map: Stroke and Zhuyin own `1` as
input, while Pinyin owns it as the apostrophe separator. Short `1` must therefore
emit an apostrophe when idle and append the same separator to a live Pinyin
composition. Long `1` remains candidate shortcut 1 while composing and literal
digit `1` while idle.

## Transient Mode Indicator Layering

The English case badge is owned by `InputView`, while `CandidatesView` is added
later as a full-screen sibling under the IME content root. Android therefore
draws the candidate surface above the badge regardless of the badge's elevation
inside `InputView`. When Smart English places its candidate bubble over the
center region, the `Abc`/`ABC` feedback can be partially or fully covered.

This is a hierarchy defect rather than a Smart English state or timing defect:
the case coordinator emits the expected label and the badge animation runs, but
no child elevation can cross the sibling boundary. The transient indicator must
be a final, non-interactive child of the IME content root, above both the input
and candidate surfaces. Existing visual behavior and timeout remain unchanged.
There is no useful JVM seam for Android sibling draw order; physical-device
capture is the regression signal.

## 4.2.0 Release Boundary

The post-4.1.0 work is a feature release rather than a patch release. It adds
Stroke and Zhuyin T9 schemes, configurable Chinese-scheme cycling, continuous
Smart English prediction, and substantial candidate/input pipeline changes.
The release must therefore advance both the public version name to `4.2.0` and
the base version code from 15 to 16.

The stable README already describes most current behavior, but the Baidu
instructions still contain an obsolete short-`#` scheme-switch contract. The
release must align every public instruction with the shipped key contract:
idle short `#` is return/search, composing short `#` submits the visible raw
code, long `#` changes top-level mode, and idle long `*` cycles enabled Chinese
schemes. Release artifacts must be signed with the existing `key0` key and
verified before publication.

## Performance-First Release Baseline

The next release must optimize measured input responsiveness rather than file
size or Module count. Existing `T9ResponsivenessTrace` spans are local,
string-keyed stopwatches. They cannot correlate physical input with Fcitx/Rime
wait time, candidate snapshot publication, Android rendering, and the first
complete displayed frame. Enabling them also measures dozens of nested blocks,
updates one synchronized map per block, and may log on the input thread.

The first architecture slice is therefore observability, not an optimization.
One accepted T9 command generation should be followed from command execution to
the first complete candidate frame. A newer input invalidates the older trace,
just as a newer composition invalidates stale candidates. Chinese paths include
the engine event wait; Smart English and local interactions can proceed directly
to snapshot creation. Results are grouped by input path and report p50, p95,
maximum, and average stage costs. No fixed latency budget should be invented
before the target phone supplies a trustworthy baseline.

Detailed nested section timing remains available only as an explicit diagnostic
mode. The normal responsiveness setting should use the lower-overhead
transaction trace so the measurement mechanism does not materially create the
latency it is intended to observe.

The follow-up performance rollout has six bounded slices. After the baseline,
Smart English persistence must stop reading file metadata or writing complete
files from the synchronous input lifecycle. Physical key state collection must
read only the active mode's state instead of materializing Chinese, English,
and candidate snapshots for every key. Candidate refresh generations must own
their complete source-to-frame lifecycle so delayed callbacks cannot publish or
complete a newer frame. Geometry measurement and frame publication must have a
single owner rather than independent view callbacks. Finally, Chinese engine
operations must expose one serialized operation interface instead of spreading
ticket checks, Fcitx jobs, and UI refresh decisions through the service.

Each slice replaces its old path. The rollout must not retain a synchronous
persistence fallback, a broad all-mode state snapshot, a parallel refresh
scheduler, duplicate geometry observations, or direct service-side Chinese
operation sequencing after the replacement Module is verified.

Target-phone Zhuyin baseline, using ADB keyboard events against an editable
candidate surface, produced 20 completed generations with no replacements. At
600 ms key spacing the pre-refactor sample was average 91.86 ms, p50 88.76 ms,
p95 133.54 ms, maximum 187.55 ms. A 200 ms warm burst measured average 70.78
ms and p95 85.52 ms. After the six architecture slices, the 600 ms smoke run
measured average 93.07 ms, p50 81.20 ms, and p95 117.31 ms, with one 294.91 ms
cold/outlier frame. The architectural work therefore reduced typical tail
latency without claiming an improvement from the isolated maximum; future
optimization should target snapshot and render stages, which still dominate.

## Reading Row Frame Latency Follow-up

The first Pinyin digit exposes a more specific latency shape than the earlier
Zhuyin baseline. Twenty paced `4`/Backspace cycles on the target phone measured
the Pinyin candidate frame at average 132.01 ms, p50 130.26 ms, p95 151.49 ms,
and maximum 182.38 ms. The trace attributed 23.72 ms to the first engine event,
80.14 ms to snapshot publication, 19.20 ms to synchronous rendering, and 8.91
ms to the final frame. Fcitx logs show the matching input-panel and candidate
events only 4-9 ms apart, so the 80 ms snapshot stage is not local Pinyin lookup.

The dominant avoidable wait is the frame-aligned refresh dispatch. Every source
event calls `postOnAnimation`, even though the Chinese Candidate Frame Gate
already rejects an incomplete input-panel/candidate pair. On a busy or
thermally constrained phone, that dispatch can miss more than one frame before
snapshot work begins. The generation still needs one queued main-thread turn
to coalesce source events, but it does not need to wait for the next animation
callback.

The reading-row reveal also retains a legacy one-layout delay from the former
per-chip view implementation. The row is now a Canvas strip whose item bounds
and content width are calculated synchronously. Once the candidate toolbar
width and Canvas geometry are present, forcing an additional `post` plus
pre-draw listener no longer protects against partial chip layout; it only
delays the complete atomic surface. Width-unavailable frames must still stay
hidden rather than publishing a clipped row.

Finally, the candidate toolbar currently performs a natural hierarchy measure
and may immediately repeat the same hierarchy measure after applying the stable
tail width. The first measure already provides exact child geometry. The stable
width can be published to the shared geometry Module and assigned as the root
minimum for Android's real layout pass without synchronously measuring the same
children a second time.

Success requires preserving the accepted visual contract: no speculative local
row, no stale Hanzi frame, no partial/clipped first reading row, no bubble-width
flash, and no changed spacing or focus scale. The target signal is a lower
Pinyin p50/p95 and a screen-recording in which the preview, reading row, and
Hanzi row still first appear together.

The first main-queue implementation reduced three consecutive 20-sample Pinyin
windows to averages of 106.74, 90.04, and 89.43 ms. The latter two p95 values
were 97.66 and 102.63 ms, and video still showed preview, reading row, and Hanzi
in the same first visible frame. Snapshot publication nevertheless remained
48-58 ms after the accepted source pair. Because the source-ready callback is
already on the main thread and the Candidate Frame Gate has accepted both
engine halves, another queued turn has no coalescing work left to perform.
`T9CandidateRefreshGeneration` should therefore consume and publish its pending
generation immediately at that exact readiness transition; its already-posted
callback becomes stale by generation ownership. Incomplete and local refreshes
continue to use normal main-queue coalescing.

Targeted detailed timing and a one-key event probe found the remaining 48-58 ms
wait. The ticket-matched Pinyin candidate event publishes immediately, but that
first build starts an asynchronous `getCandidates(0, 80)` request and defers the
frame until a second refresh. The request is made even when `filterPrefixes` is
empty. In that state it performs no reading filtering; it merely reloads and
deduplicates candidates that the accepted Rime page already contains. The
normal local-budget pager can page the accepted Rime source and preserve its
original indices without this engine round trip.

The Bulk Candidate Loader is needed only after a concrete Pinyin or Zhuyin
reading prefix must be matched across engine pages. Empty-prefix Pinyin input
must use the accepted engine page immediately. This preserves selected-reading
behavior and removes the largest first-reading-row delay rather than trying to
micro-optimize the 2-4 ms snapshot builder or the local resolver.

The final low-overhead rerun used the same twenty paced `4`/Backspace cycles on
the same phone. It measured average 62.24 ms, p50 59.59 ms, p95 67.36 ms, and
maximum 123.27 ms. Average, median, and p95 latency fell by approximately 53%,
54%, and 56% respectively. The stage averages were 28.80 ms source wait, 15.36
ms snapshot publication, 15.18 ms rendering, and 2.85 ms frame wait. Extracted
screen-recording frames still show the preview, complete `g h i` reading row,
and Hanzi row appearing together, and selecting a reading still triggers the
cross-page bulk-filter path without a stale or empty frame.

## Reading Row Focus Rendering

The next render cost is local to the reading row rather than Rime. On the first
bottom-focused candidate frame, `renderPinyin()` already submits and plans the
complete Canvas row. The same frame then calls `renderFocus(BOTTOM, BOTTOM)`,
which enters `renderWindow()` a second time even though no focus transition
occurred. That repeats surface planning, chip-list submission checks, and every
pinyin text measurement. The repeat is unnecessary on the initial bottom
state; a row replan is required only for an actual `BOTTOM -> TOP` or
`TOP -> BOTTOM` transition because those transitions change folded/full
viewport behavior.

`T9CandidateSurfaceGeometry` also measures identical reading strings whenever
the row is replanned. The geometry instance has one stable font measurement
function for its lifetime because changing the input UI font recreates the
input views. It can therefore retain a bounded text-width cache and last-value
row/chip geometry snapshots keyed by the reading items and relevant metrics.
Focus navigation can then reuse exact measurements without replacing the
real-measurement layout algorithm or changing the accepted bubble geometry.

Success requires identical row width, folding, ellipsis, focus scale, and
whole-chip scrolling. Repeated planning for the same input must perform no new
text measurements, while changed text or metrics must produce a fresh geometry
snapshot.

Target-device verification preserved the Pinyin bottom/top/bottom transition
and the folded Zhuyin `68` row while navigating through its later readings.
The first post-install 20-sample Pinyin window measured p50 56.21 ms with a
230.72 ms cold outlier; its average render stage still fell from the previous
15.18 ms to 12.52 ms. A second warm window measured average 51.65 ms, p50 49.31
ms, p95 60.73 ms, and maximum 63.68 ms. Its snapshot and render stages averaged
10.07 ms and 7.83 ms respectively. The warm result is not used to erase the
cold outlier, but it confirms that eliminating the duplicate focus pass and
reusing geometry materially reduce the local UI work once the input view is
ready.

### Device-dependent Stroke glyph coverage

The curated dictionary removed non-Han components, but a two-key device sweep
still reproduced blank slots. For Stroke code `12`, Rime returned U+2C09B and
U+2CEB0 in slots 5 and 6. Both are valid supplementary-plane Han ideographs,
yet the active custom-font-plus-system-fallback Typeface reports
`Paint.hasGlyph() == false`; surrounding BMP candidates render normally. The
same sweep found only supplementary-plane candidates among unsupported glyphs.

Static Unicode-category filtering therefore cannot guarantee a visible row on
different Android font stacks. Unsupported candidates must be removed at the
owned Stroke candidate-source boundary before local paging and original-index
mapping, not hidden by the Android row after paging. The filtered source keeps
the original Rime indices so focus, numeric shortcuts, and commit remain exact.
The generated dictionary additionally assigns supplementary Han the lowest
weight so portable BMP candidates fill early pages while supported rare Han
remain available on devices that can draw them.

## Physical Input And Cold-start Performance Rollout

A target-device cold-start probe exposed a different bottleneck from the warm
candidate path. One process-cold sample took about 4.86 seconds from process
start to `onStartInputView`, skipped 183 frames while creating the input
surface, and spent about 2.37 seconds between the Fcitx ready event and
`KeyboardWindow` attachment. An unchanged `DataManager.sync()` still took
about 375 ms. These are diagnostic samples rather than percentile baselines,
but they are large enough to define the next investigation boundaries.

First-install work and repeated-start work must remain distinct. Native Fcitx
and Rime files must be materialized on first install or update, and Rime may
need to compile the selected schema. An unchanged installed process should not
rediscover every plugin, rebuild the complete data hierarchy, rewrite the same
descriptor, construct inactive keyboards, initialize unopened picker catalogs,
or decode feedback sounds before the first visible frame.

The current transaction trace also has incomplete coverage. It begins inside
the T9 handler only for Chinese and Smart English candidate decisions and ends
only at a candidate frame. Simple English composition, number-mode effects,
local D-pad selection, physical delete, Return, mode changes, and transient
panels either produce no sample or leave a candidate-frame transaction pending.
Performance work on those paths must begin with one semantic transaction model
that distinguishes editor effects, input-surface frames, candidate frames, and
Fcitx-backed source frames.

The Physical T9 Key Flow reducer is already mode-shaped and O(1). The rollout
must not move file access, Rime readiness checks, editor IPC, dictionary scans,
or Android measurement into that reducer. The Android adapters own effects and
frame observation; deeper runtime Modules own state, invalidation, and ordering.

Seven bounded slices are accepted, in this order:

1. Extend responsiveness transactions across physical T9 commands and the
   remaining router-owned effects.
2. Publish explicit Rime availability separately from generic Fcitx readiness.
3. Materialize only the active keyboard, opened picker catalog, and post-frame
   feedback resources.
4. Give native data installation a trustworthy unchanged fast path.
5. Replace broad candidate refreshes for local selection and punctuation with
   local frames or atomic source transitions.
6. Publish one Smart English snapshot per input revision and remove repeated
   lookup/list construction from a frame.
7. Capture physical-delete editor state once and move number expression work
   after the immediate operator effect.

Each slice replaces its previous path rather than retaining a compatibility
fallback. Focused tests must lock down endpoint ownership and stale-result
rejection before device performance is compared.

The Rime availability slice also exposed an existing source-control hazard:
the parent repository already referenced a local-only fcitx5-rime commit for
the Android get/replace APIs, while `.gitmodules` still pointed at upstream.
Fresh clones could not fetch that commit. The maintained Android changes now
live on the `android-t9` branch of the project fork, and the submodule URL points
at that fetchable history before adding the availability callback.

Device installation exposed the corresponding runtime-version hazard: the
main APK can be updated while an older Rime plugin APK remains installed. That
plugin loads successfully under the shared `0.1` plugin descriptor but does
not export the new availability callback; the generic Fcitx addon call throws
`std::runtime_error`. Allowing that exception to escape aborts the native Fcitx
thread and repeatedly restarts the whole IME. The native Rime Adapter now
treats a missing availability export as `Unavailable`, logs the incompatibility,
and keeps the IME alive. It does not restore the removed readiness heuristic;
Chinese Rime input remains disabled until the matching plugin is installed.

The input-surface slice removes two independent forms of eager work. Fcitx now
publishes one immutable cached-state revision that the Android views can read
without entering `runBlocking`; event collection is also exposed directly by
the connection. Separately, `KeyboardWindow` constructs only its requested
layout, picker windows and their large catalogs materialize only when opened,
and SoundPool decoding is scheduled after the first visible input frame. A key
pressed before a sound sample is ready intentionally has no sound rather than
paying decoder initialization on the input path.

The Data Installation Module now persists a completed fingerprint only after
the merged descriptor has been written atomically. The fingerprint combines
the app data descriptor, the merged descriptor, and canonical installed-plugin
package identities, and also carries the loaded plugin metadata needed by
native startup. An unchanged process start therefore avoids plugin XML and
asset descriptor parsing, hierarchy construction, file diffing, copies, and
descriptor rewrites. A missing/corrupt marker, interrupted write, app data
change, or plugin install/update/removal takes the full path. Failed plugin
outcomes intentionally do not authorize the fast path so transient failures
are retried rather than made sticky. English T9 dictionaries remain APK assets
for `AssetManager` lookup but are excluded as a directory from the native data
descriptor, removing a redundant multi-megabyte install copy.

Candidate navigation no longer republishes the complete candidate pipeline for
an in-page cursor move. T9 Candidate Source Sessions update the owned cursor,
the Snapshot Pipeline derives a selection frame from the last accepted source
snapshot, and the Android adapter updates only the affected shortcut chips and
top preview. Candidate content, paging, and reading-row geometry are validated
as unchanged before this path is accepted; otherwise the normal complete frame
is requested. The existing final-chip scale reservation remains the one bounded
measurement exception so local navigation preserves the accepted focus visual.

T9 Punctuation Lifecycle now publishes its replacement candidate source without
first clearing the whole input surface. This removes the blank hide/show frame
between a committed word or Hanzi and the punctuation row. In-page punctuation
preview moves use the same local selection frame. A broad transient clear is an
explicit lifecycle request reserved for a genuinely incompatible composition,
not the default punctuation-entry behavior.

Smart English now has one publication owner. A changed digit sequence,
dictionary/prediction generation, prediction context, or case state builds one
immutable candidate-content snapshot; a cursor-only revision reuses that
candidate array and its page-cache content key while publishing only the new
cursor and preview. Candidate paging and presentation therefore cannot perform
independent dictionary lookups or observe different rankings in one frame.

The 3.6 MB built-in dictionary previously expanded every word into every T9
prefix during preload, creating a large map before first use. It now parses one
ordered exact-sequence index and computes only bounded 64-word prefix pools on
demand. Common one-, two-, and three-digit prefixes are warmed on the IO
dispatcher, and the bounded LRU retains later prefixes. Exact ordering,
essential words, learned-word overlay, prefix ranking, and pair-frequency
reranking remain unchanged. Dictionary and prediction generations invalidate
only the affected Smart English snapshots; cursor movement performs no lookup.

Physical Backspace previously passed through three independent router routes:
empty-editor detection, resolved-segment reopening, and idle deletion. A
non-empty idle editor could therefore execute `getExtractedText` once to reject
IME hiding and again to decide the deletion, with extra surrounding-text IPC
when extraction was unavailable. Physical Delete Coordinator now decides the
ordered outcome from local composition state and one lazily captured Editor
Snapshot. Resolved-segment and active-composition decisions perform no editor
read; empty detection and deletion share the same extraction/surrounding-text
result. Password Backspace reuses the same delete plan, while virtual-keyboard
deletion retains its separate Fcitx behavior.

Number `=` and `≈` previously read up to 96 editor characters and parsed the
expression before committing the operator, so editor IPC was part of the
physical-key completion time. Number Mode Controller now commits the operator
first and queues the editor read for the next main-loop turn; parsing and
formatting run on the computation dispatcher. Each request owns a monotonic
generation ticket. A newer number-mode key, text effect, panel dismissal, mode
change, or input-session transition cancels the job and rejects any late
result. The deferred reader accepts either the post-commit text (and removes
the committed `=`/`≈` suffix) or a still-stale pre-commit editor snapshot.

Target-device verification covered all seven slices with the debug IME and its
matching debug Rime plugin. Twenty-sample Smart English input and delete windows
measured p50 20.25 ms and 22.39 ms respectively. Local Smart English candidate
navigation measured p50 10.10 ms and performed no snapshot or full-render work,
confirming that cursor-only frames stay on the local path. A twenty-sample
Chinese Zhuyin input window measured p50 47.92 ms and p95 63.23 ms, with one
201.29 ms outlier; its remaining cost was distributed across the Rime source,
snapshot, render, and frame stages rather than the O(1) key decision.

The number-mode device scenario `1+2=` committed the equals sign immediately,
then published `=3` through the deferred result panel. Backspace first dismissed
that transient result and only the next Backspace edited the expression, which
also validates stale-panel ownership in the physical-delete path.

One process-cold probe after `am kill` reached `onStartInputView` in about 4.00
seconds versus the earlier 4.86-second diagnostic sample. The unchanged data
installation fast path completed in 314.79 ms rather than the earlier roughly
375 ms full check, but startup still skipped 136 frames before the input view
and another 51 around Rime readiness. This is a measurable improvement, not a
claim that cold start is solved: process creation, Android view inflation,
Rime deployment, font work, and JIT remain the next evidence boundaries.

The Rime callback contract makes the main and Rime plugin APKs a coordinated
release unit. A matching plugin was rebuilt and installed for device testing.
The fail-closed guard keeps an older plugin from terminating the IME, but Chinese
input intentionally remains unavailable until the updated plugin is installed;
future releases that include this main-app change must therefore publish the
corresponding Rime plugin update.

## Stroke Naming And Per-scheme Output Script

The implemented numeric Chinese scheme is mobile **Stroke** input, not Wubi.
Its English domain identifiers (`STROKE`, `T9StrokeCodec`, and the
`t9_stroke` schema id) are already correct; only user-facing Chinese text was
misnamed as `五笔画`. The maintained name is `笔画九键` (`筆畫九鍵` in
Traditional Chinese). Existing deployed Rime configurations may still report
the old label, so classification keeps that old text as an input alias without
showing it in current UI or documentation.

Pinyin, Stroke, and Zhuyin use different Rime conversion options. Pinyin and
Zhuyin enable `traditionalization` to request Traditional output, while Stroke
enables `simplification` to request Simplified output because its source table
contains Traditional forms. A per-scheme setting therefore cannot safely treat
the Rime checkbox polarity as uniform.

The setting means the output script selected when a Chinese scheme becomes
active, not a permanent lock. The user may still toggle Simplified/Traditional
from Rime for the current visit. Scheme entry, Rime readiness, and an active
preference change each issue a one-shot option assignment through a typed Rime
Adapter. A monotonic request generation rejects assignments made stale by a
newer scheme or preference transition. Later status updates do not force the
configured default back.

Device inspection showed that Rime's script actions are display actions, not
checkable state: both `isCheckable` and `isChecked` remain false while the text
changes between direction labels such as `简 -> 繁`. Parsing those labels would
couple behavior to translated UI text and still leave ambiguous polarity. The
app therefore sets the schema-owned option directly and keeps option name and
polarity in `ChineseT9OutputScriptPolicy`. This work stays on scheme/readiness
lifecycle events and adds no preference, action, or Rime read to the physical-key
or candidate-frame path. The Chinese T9 preference category is also copied to
device-protected storage so enabled schemes and output defaults remain the same
when the IME starts before the first device unlock.

## Cold-start Phase Attribution

The remaining process-cold delay cannot be optimized safely from the current
single total. The existing trace starts at a physical key, while startup spans
Application initialization, data installation, native Fcitx startup, Rime
readiness, input-view construction, and the first visible input-surface frame.
Those stages run on different threads and may overlap, so isolated ad-hoc
timers would not provide one internally consistent startup history.

The next slice therefore records one process startup generation from Android's
process-start elapsed time. Synchronous stages use paired semantic tokens;
asynchronous lifecycle points use first-occurrence milestones. Duplicate view
creation, Fcitx restart, theme recreation, or later Rime events must not rewrite
the cold-start sample. The trace retains data even before the preference is
read, but publishes logs and the developer report only when T9 responsiveness
tracing is enabled.

Success requires a report containing Application create, data installation,
native Fcitx startup, input-view construction, Fcitx ready, Rime ready, and
first input-surface frame offsets. It must remain correct when the first frame
precedes Rime readiness, produce at most one partial and one complete log, add
no work to the physical-key path, and preserve the existing IME UI behavior.

The first target-device process-cold capture attributed the visible delay
instead of treating startup as one number. The first input-surface frame arrived
at 4570.77 ms and Rime became ready at 6680.26 ms. Synchronous stage costs were
392.78 ms for Application create, 226.33 ms for unchanged data installation,
643.21 ms for native Fcitx startup, and 2597.24 ms for input-view construction.
Input-view construction is therefore the next optimization target. The data
fingerprint fast path is measurable but is not the largest visible-stage cost,
so optimizing it first would not address the dominant first-frame delay.

The 2597.24 ms input-view total still combines unrelated work: navigation-bar
evaluation, keyboard/input view construction and attachment, floating candidate
view construction and attachment, and the transient mode indicator. The next
diagnostic slice must split those phases inside the existing Cold-start
Transaction before changing view lifecycle behavior. This avoids attributing
debug-build class loading or `setInputView` attachment cost to whichever
constructor merely appears largest in a static review.

The nested target run measured 3114.06 ms for the complete input-view stage:
2771.65 ms was `InputView` creation, 273.89 ms candidate attachment, 49.51 ms
candidate creation, 10.65 ms input attachment, 4.38 ms mode-indicator
replacement, and 3.26 ms navigation-bar evaluation. `InputView` creation is
unambiguously dominant, but its Kotlin property initialization, dependency
scope setup, active keyboard materialization, and root layout assembly still
share one constructor interval. Those subphases need one final attribution pass
before changing lazy/eager ownership.

The final split measured dependency-scope setup at 2149.91 ms, of which
2149.39 ms was component registration and only 0.26 ms was the ready callback.
The active keyboard itself took 136.16 ms to create and 69.27 ms to attach.
Inspection of the registration implementation found the structural cause:
every input component computes its uniqueness key through
`IUniqueComponent.defaultType()`, which walks Kotlin generic supertypes with
`kotlin-reflect` the first time `DynamicScope` hashes that component. Ten cold
component registrations pay this reflection cost before the first frame.

The selected optimization is therefore not to make keyboard UI lazy again or
move view creation to a background thread. Input components have a concrete
runtime class that already is their uniqueness identity. A local component base
can publish that concrete `KClass` directly, preserving DynamicScope's dynamic
window behavior while deleting generic-supertype reflection from the startup
path.

Two target-device process-cold runs after the identity change confirmed the
cause and the gain. Scope registration fell from 2149.39 ms to 10.88 ms and
10.70 ms. Complete input-view construction fell from 2723.43 ms to 695.82 ms
and 478.92 ms; the first input-surface frame moved from 4247.25 ms to 2248.78 ms
and then 1978.70 ms. The second run also completed a real physical-key Chinese
composition without restarting the IME. Rime readiness still arrived later at
6306.71 ms and 5696.53 ms, so engine readiness rather than Android input-view
construction is now the largest user-visible cold-start gap.

The apparent Rime gap was then reproduced as an environment failure rather than
a runtime architecture cost. T9 configuration files pushed with ADB were owned
by `shell` with mode `0644`, so Rime could read them but could not persist the
new `last_build_time` in `user.yaml`. Logcat reported `failed to save config to
stream`, and every process cold start repeated maintenance and rewrote compiled
schema files. Granting the app's `ext_data_rw` group write permission and
allowing one maintenance run removed the repeat: the next process reached Fcitx
ready at 2246.50 ms and Rime ready at 2249.77 ms. The 3.27 ms gap does not
justify moving user-editable Rime data away from external app storage.

With input-view reflection and repeated Rime maintenance removed, unchanged
`DataManager.sync()` remains a 242-304 ms startup stage. Its fast path still
combines four different operations: APK descriptor read/JSON decode, plugin
package discovery through PackageManager, installed merged-descriptor
read/decode, and installation-state read/decode, followed by stale credential
storage cleanup. The files are only about 21-25 KB, so file size alone cannot
identify the cost; PackageManager IPC and first serialization class loading are
plausible but must be measured separately before changing fingerprint trust.

Target attribution measured 163.72 ms in the APK descriptor read/decode and
68.60 ms in the installed merged-descriptor read/decode. Plugin discovery was
5.14 ms, installation-state loading 11.66 ms, and completion cleanup 1.21 ms.
The fast path therefore spends over 90% of its measured work materializing two
complete file maps only to compare each descriptor's top-level identity.

The safe optimization is a two-part fingerprint contract rather than trusting
file existence. The build task emits the main descriptor identity as a tiny
companion asset. The completed installation state records a SHA-256 digest of
the exact merged-descriptor file bytes. An unchanged start reads the small
identity, hashes the 25 KB installed file, validates plugin package identities,
and restores plugin metadata without decoding either descriptor. A missing,
malformed, stale-format, or digest-mismatched record still enters the complete
descriptor merge and atomically replaces both descriptor and state.

Target-device verification confirmed the intended migration and steady-state
paths. The first format-2 launch performed one complete installation in
272.84 ms and persisted both descriptor digests. The next two process-cold
launches reused the completed fingerprint in 112.57 ms and 115.21 ms, down from
the 253.92 ms attributed baseline. Main and merged descriptor work fell from
163.72 ms and 68.60 ms to 7.42 ms and 5.48 ms in the first steady capture. The
remaining roughly 86-90 ms installation-state decode is now the largest part
of this Module, but it is outside this bounded slice: descriptor decoding was
the selected operation, and the fast path is already about 55% shorter without
weakening interrupted-install or plugin-update invalidation.
