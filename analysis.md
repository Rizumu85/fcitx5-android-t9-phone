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
