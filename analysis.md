# Analysis

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
