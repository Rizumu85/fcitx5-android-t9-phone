# Design

## Project Goal

Provide a comfortable Android input method for physical T9-key phones, centered
on Chinese T9 through Rime, English multi-tap, numeric entry, compact on-screen
controls, and a readable keyboard surface.

## Current Task Design

Add physical OK-key selection mode and keep the T9 top/bottom row ratio default
at 82.

## Physical OK Selection Design

Use physical OK long-press detection from repeated `KEYCODE_DPAD_CENTER` or
`KEYCODE_ENTER` down events. When the long press is detected in a plain editor
state, set a local `physicalSelectionMode` flag and consume the event. Keep the
feature IME-owned; the attempted Android-native `startSelectingText` request did
not reliably surface native handles/toolbars.

While `physicalSelectionMode` is active, intercept physical Left/Right down
events and update `InputConnection.setSelection()` from a stored anchor and a
moving focus edge. This avoids the app's collapsed-selection delete workaround
that can trigger when Shift+Arrow collapses a selection back to the original
cursor position. For Up/Down, delegate to Shift+Up/Shift+Down because only the
target editor knows the rendered line layout and vertical cursor destination.

Keep the feature out of active T9 composition and candidate states. Short OK
while the mode is active exits selection mode. If any non-selection input key is
pressed after selecting text, leave selection mode first, show the existing mode
badge with an exit label, and then pass that key to the normal input pipeline so
the editor can replace the selected text or handle the command normally.
Preserve the selected range on exit so editors that support Android's native
selection toolbar can show it; the IME cannot force that toolbar in every app.
Use explicit badge labels: `进入选区` for entry and `退出选区` for all exit paths.
When the user exits physical selection mode with OK while a selection remains,
show an IME-owned selection action hint panel. The panel uses the same badge
styling as mode feedback but lays out action labels by physical key position:
Up=`复制`, Left=`剪切`, Right=`粘贴`, Down=`删除`, and center=`Ok`.
The next matching physical key performs the corresponding
`performContextMenuAction()` or local delete operation. Copy, cut, paste, and
delete close the panel after action. OK closes the panel. Back/Delete cancels
the selected range and closes the panel without showing another action badge.
The panel should stay visible indefinitely until one of those next actions,
rather than timing out. Render the center OK as a round key-like badge, and show
cut/paste as vertical labels around that center OK.
Force the cut/paste side hints into narrow stacked labels so they cannot render
as horizontal text on devices with different font metrics. Leave more space
between the side hints and the center OK, and size the OK circle large enough to
read as the visual center of the physical-key cluster.
Do not use `AutoScaleTextView` newline text for those side hints; render each
character in a vertical container so the layout is genuinely stacked.
Keep generous spacing between the center OK and all four surrounding action
hints so the cluster reads like physical keys rather than one crowded popup.
For horizontal spacing, constrain cut/paste directly to the OK circle and use
start/end margins; avoid relying on left/right margins against the invisible
anchor.
When OK exit will show the action panel, suppress the separate `退出选区` badge
so the overlay has only one message. Keep side spacing visually comparable to
the top/bottom copy/delete spacing.
Transient badge-style overlays should use filled shapes without stroke outlines.
The center Ok circle should use a larger label so the text fills the circle
more like a physical key cap.
The center Ok circle should be visually prominent enough to read as the main
cluster anchor.
Add an optional-looking guide layer behind the action hints: a dashed cross
through the center and a dashed circle around the cluster. It should share the
accent color at low opacity and sit below the action badges in z-order.
Size the guide to the actual action cluster, with the dashed circle passing
near the center of the four surrounding action hints.
Use the same selected IME font for the center confirmation label, but apply the
regular weight so it looks lighter than the surrounding action hints.

Track physical-selection-created ranges separately from the transient mode flag.
The collapsed-selection delete workaround must ignore those ranges even after
the user exits selection mode, otherwise normal arrows or replacement commits
can delete the selected text after the fact.

Scope the collapsed-selection delete workaround to a recent physical
text-producing key. Touching the editor to cancel or move a selection should
only collapse the range; it should never be interpreted as a delete request.
Do not make physical Delete operate on arbitrary touch-created Android
selections through the collapsed-selection workaround. Touch selections stay in
the touch interaction model, while the physical action panel handles
physical-mode selections explicitly.

## Number Operator Design

Keep the feature scoped to `T9InputMode.NUMBER`. Short digit presses continue to
commit digits. Long-press digit actions commit the mapped operator symbol. A
long-press on `*` opens a physical-key-shaped operator cheat sheet, using the
same filled badge style as the selection action panel. While the cheat sheet is
open, short digit presses commit the shown operator and close the sheet. The
physical `*` key also commits the shown literal `*`; Back, Delete, OK, and `#`
close it without committing.
Ignore repeat key-downs while the cheat sheet is open so the long-press that
opened it cannot immediately close it.
Use the current physical-key hint layout requested by the user:
`1=-`, `2=+`, `3==`, `4=π`, `5=/`, `6≈`, `7=(`, `8=%`, `9=)`, `0=.`,
and `*=*`. Non-parser symbols are inserted literally and are not part of the
equals-expression parser.
The parser treats `π` as `Math.PI` and accepts implicit multiplication between
adjacent factors, so `2π` evaluates like `2*π`. Keep `≈` literal-only.
Treat `≈` as an approximate-result trigger that mirrors the `=` flow: commit the
literal `≈` first, then offer an optional `≈result` action. The approximate
result uses the same parser but formats with at most two decimal places.
Keep number-mode transient UI as a single state machine with three states:
none, operator hint, and result choice. This avoids overlapping panels and keeps
Back, repeat key-downs, OK confirmation, and fallback dismissal in one path.

Treat `=` specially. Before committing it, inspect the numeric/operator suffix
before the cursor and evaluate it with a tiny local parser for `+`, `-`, `*`,
`/`, `%`, parentheses, and decimals. Always commit the literal `=` first. If
parsing succeeds, show a result-choice overlay whose result cell displays
`=result`; `确认` commits only the result portion after the already-inserted `=`,
while Back/Delete only closes the overlay. If parsing fails, committing `=` is
the whole action. The result-choice overlay should ignore repeat key-downs from
the long-press that opened it. Render `返回` as the lower small label so it does
not compete with the result action label.
If the result-choice overlay is visually crowded, omit the visible return hint
entirely while preserving Back/Delete dismissal behavior.

## T9 Candidate Layout Design

Keep the current single integer preference value and storage key so existing
settings migrate without conversion. Rename the code-facing accessor and UI
string around the model that users see: `T9 top/bottom row height ratio`.

Interpret the value as the compact top-row height percentage relative to the
lower Hanzi candidate row. The preedit row and pinyin filter row continue to use
the same compact height, while the Hanzi row remains the baseline. Add a small
2dp bottom margin to the pinyin filter row only when it is visible.
Use 82 as the default for new installs or reset preferences while preserving the
existing stored key so current users keep their chosen value.

Return-key pinyin commit already works for normal tap input. Do not change the
Return/candidate-confirm key routing in this step.

## IME Font Design

Add one dynamic string-list preference under the virtual keyboard settings. The
first entry is `System default`, which keeps Android's currently active default
font. The rest are custom font files found in user-visible font folders.

Keep the setting scoped to the input method UI. It should not affect app
settings screens, logs, or unrelated Android UI. A shared helper should expose
`Typeface` resolution and an `applyTo(TextView, style)` function so all IME
text surfaces use the same choice while preserving per-key bold/normal styles.

Always create and scan the app data `fonts` folder under the app's external
files root. Also scan the primary storage `Fonts` folder as a best-effort
convenience for devices/file managers that expose it to the app. Load selected
custom fonts with `Typeface.createFromFile`; if loading fails, fall back to the
system default.
Label font entries with explicit source names: app data `fonts` or public
`Fonts`. Do not create or scan an app data `Fonts` alias, because it duplicates
the supported app-local `fonts` folder.
When `inputUiFont` changes, recreate both `InputView` and `CandidatesView`.
Existing text views do not observe typeface changes after construction, so a
full IME view replacement is the simplest consistent refresh path and matches
how theme changes already refresh the keyboard and candidate surfaces.
T9 pinyin filter chips are part of the IME candidate/preedit surface even
though they are rendered by a small RecyclerView adapter. They must also apply
the shared `InputUiFont` helper when their `TextView`s are created.

Do not request broad storage permissions for this step. If public `Fonts` is
not readable on a scoped-storage device, the app data `fonts` folder remains
the supported path.

## Theme Preset Design

Create two `Theme.Builtin` presets in `ThemePreset.kt`:

- `InkBlack`: gray/white/black base, black accent key, black active selection.
- `InkPink`: same base, pink accent key, pink active selection.
- `InkBlackDark`: dark black/gray base, white accent key, white active
  selection with black foreground.
- `InkPinkDark`: same dark base, pink accent key, pink active selection
  with white foreground.

Register both in `ThemeManager.BuiltinThemes` so they appear with other built-in
themes. Do not add theme settings, custom serialization changes, or unrelated UI
changes.
Set `keyboardColor` to pure white for the keyboard body, and keep
`altKeyTextColor` black so symbol, emoji, language, and backspace controls do
not inherit the gray secondary text color.
Keep KawaiiBar `ToolButton` icons on the gray secondary color by tinting them
with `candidateCommentColor`; keyboard key icons continue to use
`altKeyTextColor`.

Because the theme model has `spaceBarColor` but no separate space-bar text
color, add a small contrast guard in `TextKeyView`: derive the space label color
from the actual space-bar background, using white text on dark bars and black
text on light bars. The dark Ink themes use white space bars with black labels.
Use the same small corner radius for the mode/Caps indicator badge as the space
bar background. Size it as a compact key-like strip with a space-bar-like height
ratio, but keep the width tight enough for the three-character mode labels.

For T9 candidate focus, keep color state in the candidate row components:
active-row non-focused candidates use `candidateTextColor`, inactive-row
candidates use `candidateCommentColor`, and the focused candidate uses
`genericActiveForegroundColor` on `genericActiveBackgroundColor`.
Candidate bubble backgrounds should use `keyboardColor` so the pinyin/Hanzi UI
matches the keyboard body.
Give the combined pinyin/Hanzi candidate bubble a low-elevation outline shadow
with reduced shadow color opacity where the platform supports it. Keep enough
bottom padding around the candidate view so the softer shadow is not clipped.

Keep the return key icon/background circle sizing in the shared key rendering
path so all keyboard layouts and themes use the same smaller return visual.

## Keyboard Defaults Design

Use 39% as the default regular virtual keyboard height in portrait orientation.
Keep the existing landscape default and the separate T9 keyboard height defaults
unchanged.

## Release Version Design

Label this release as `2.0.0`. Gradle's actual `versionName` is resolved from
`BUILD_VERSION_NAME`/`buildVersionName`, then `git describe`, then
`Versions.baseVersionName`, so set the committed `buildVersionName` override and
the fallback base version name to the same value. Keep the already incremented
`baseVersionCode` so APK updates remain installable, and keep the matching
ABI-derived Play release note file.

## Pending Physical-Key Behavior Design

For the physical Delete key, add an empty-editor guard before normal backspace
handling. If there is active composition, pending punctuation, candidate focus,
or editor text before the cursor, Delete should keep its current deletion
behavior. Only when the editor is truly empty should Delete trigger the exit
behavior.

The empty-editor exit destination should match the existing on-screen exit-IME
button exactly, so physical Delete and the visible exit control share behavior.
Implement this as the first small testable step in `FcitxInputMethodService`:
intercept mapped physical Delete on `ACTION_DOWN`, verify that the editor and
local transient input states are empty, call `requestHideSelf(0)`, and consume
the matching key-up event.
Use the most reliable editor text signal available for that empty check. Prefer
the tracked cursor position and `InputConnection.getExtractedText()` so search
fields that do not expose one-character surrounding text are not mistaken for
empty; use `getTextBeforeCursor()`/`getTextAfterCursor()` only as a fallback.
When physical Backspace is pressed while there is no local composition,
selection reopen, pending punctuation, or pending English multi-tap character,
delete directly through `InputConnection`. This keeps search fields from
requiring a second press because an idle BackSpace key event was first consumed
inside the Fcitx/editor key-event path.
For that direct-delete path, prefer `InputConnection.getExtractedText()` as the
source of text/cursor truth before falling back to `getTextBeforeCursor(1)`.
Search suggestion UIs may refresh surrounding-text queries around the first
Backspace press; if extracted text reports a cursor after at least one
character, issue `deleteSurroundingText*` directly anyway.

For Chinese/English/numeric mode switching, introduce a clear animated
confirmation that is separate from text composition and candidate commit paths.
The animation should be controlled by mode state changes, not by inserting or
confirming text. Keep the existing space-key Chinese/English label behavior.
The first implementation should be an `InputView` overlay badge shown from
`switchToNextT9Mode()`: a centered, non-clickable label that fades/scales in,
holds briefly, then fades out. It must not call `InputConnection` APIs.

For this migration, generalize the `InputView` badge API so both T9 mode labels
and English case labels can use it. English Caps/Shift should show `abc`,
`Abc`, or `ABC` in the badge only when there is no pending multi-tap character.
When a pending multi-tap character exists, continue updating the editor
composing text for that actual character.
Keep the badge animation brief: fast fade/scale in, short hold, fast fade out,
so repeated physical-key switches do not feel delayed.

When T9 mode is enabled, start each input session with Fcitx's `fullwidth`
status action inactive. The app's T9 Chinese/English workflow expects ASCII
English characters by default, but a later manual press on the full-width status
button should be respected so the user can intentionally type full-width/spaced
English.

## T9 Punctuation Design

Handle Chinese `1` punctuation locally when the user is not already composing
Chinese text. Use a pending punctuation character, similar to English multi-tap,
so repeated `1` cycles punctuation and timeout commits it. While a Chinese
punctuation character is pending, `*` toggles the pending character between the
Chinese and English punctuation sets.

Keep `*` as a literal star in Chinese mode when there is no pending punctuation,
preserving existing behavior outside the new `1` punctuation workflow.

Clear transient inline suggestions when the local punctuation flow starts. This
keeps Android autofill suggestions such as password-manager chips out of the
punctuation interaction without turning inline suggestions off globally.

Represent pending T9 punctuation as a local candidate page in `CandidatesView`.
The candidate page should use the active Chinese or English punctuation list,
highlight the current multi-tap punctuation index, and commit the selected
punctuation through the service without calling Rime candidate selection.
Moving the Hanzi focus across this local page should preview the focused
punctuation in the input method's top preedit row so later confirmation commits
the visible choice.
Do not auto-commit local Chinese punctuation on the multi-tap timeout while the
candidate page is visible; consume the matching key-up locally so the composing
punctuation is not mistaken for regular Chinese composition. Pending punctuation
is a higher-priority transient state than Chinese composition for `1`, `*`, `0`,
and `#` handling.

Do not use editor-side composing text for local punctuation preview. While
punctuation is pending, `getT9PresentationState()` should return the focused
symbol as `topReading` and an empty pinyin option list.

Treat DPAD arrows and OK as candidate-control keys while local punctuation is
pending. They should bypass the generic "commit pending punctuation before other
input" guard and be handled by normal candidate focus navigation.

Keep the full Chinese/English punctuation pools in the service, but paginate
them in `CandidatesView` with the same `T9CandidateBudget` logic as Hanzi
candidates. Map shown symbol indices back to their global punctuation indices so
preview and commit work correctly across pages.

When local punctuation is pending, consume DPAD arrows and OK even if the
candidate page cannot move further. Boundary navigation should be a no-op inside
the input method, not a cursor movement in the target editor.

Keep pending local punctuation as a small grouped state inside the service. The
state owns the active punctuation set, highlighted index, visible punctuation
text, and deferred short-`1` flag. This keeps punctuation cleanup local while
leaving the larger Chinese T9/Rime composition model untouched.

Keep Chinese physical digit handling behavior-preserving while shortening the
main key handlers. Extract the Chinese `0`, `1`, and `2`-`9` timing rules into
small helper functions, but keep short-press-on-key-up and long-press shortcut
semantics exactly as tested.
When active Chinese T9 composition exists, long-press shortcuts cover all ten
visible candidate slots: `1`-`9` select indices 0-8 and `0` selects index 9.

## T9 Pinyin Design

The current static T9 pinyin map is incomplete for longer syllables. The audit
shows 71 Rime dictionary syllables that cannot appear in the pinyin candidate
row. The next pinyin fix should complete the map against the Rime dictionary
syllable set rather than adding only individually reported examples.

Prefer a surgical completion of the existing map for now: merge missing strings
into existing group keys where keys already exist, and add missing group keys
where needed. This keeps UI behavior and ordering close to the current design
while removing the known coverage gaps.

After the map change, rerun the same static coverage comparison against the
bundled Rime dictionary. Success means zero missing Rime syllables in the local
T9 pinyin map.

When a Hanzi candidate is selected without an explicit pinyin filter, update the
local T9 model from the selected candidate's pinyin comment when possible. This
keeps the displayed pinyin composition aligned with consumed Hanzi segments.
Helpers outside `update()` should use `service.isChineseT9InputModeActive()` for
T9 state instead of relying on local variables scoped to `update()`.

When no explicit pinyin filter has been selected, the top pinyin preview should
prefer the currently highlighted Hanzi candidate's comment reading over the
default digit-to-pinyin guess. Moving the Hanzi focus should refresh the preview
row so ambiguous digit sequences show the selected candidate's reading.
Because the same row also indicates how many T9 digit keys have been entered,
candidate comment readings should be cropped by the current T9 key count before
display. This keeps prefix matches intuitive: after one `2`, a highlighted `ai`
candidate previews `a`, and a highlighted `ba` candidate previews `b`.
Match candidate comments to the user's actual T9 keys at the letter level:
preserve selected-candidate pinyin letters that correspond to the typed T9
digits, skip non-matching candidate letters, and continue matching later letters
in the selected candidate reading. This lets a selected reading like
`deng deng wo shi` preview typed initials as `deng deng ws` rather than
incorrectly turning `ws` into `wo`. If the selected reading cannot cover the
remaining typed keys, append the normal digit-based pinyin preview for the
remaining suffix.
When the on-screen Return key is tapped during active Chinese T9 pinyin
composition, commit the same predicted pinyin shown in the top row as plain text
with separators removed. This is a virtual-keyboard Return behavior; normal
Return/editor actions remain unchanged when there is no active pinyin
composition.
When the current T9 digit count becomes zero during deletion, the top pinyin
preview must stay empty. Do not use a highlighted candidate comment as fallback
while there are no active T9 keys, because that can flash a full reading such as
`gan` after the last preview letter has been deleted.
When T9 pinyin deletion changes the candidate context, the Hanzi row should
render with a deterministic local focus immediately. Do not allow a transient
candidate page to display an engine-provided or stale `cursorIndex` such as a
previous fifth item before the local cursor reset moves focus back to the first
item.
Show numeric shortcut labels only on the bottom candidate row: Hanzi candidates
while Chinese T9 composition is active, and local punctuation candidates while
punctuation is pending. Do not show numeric prefixes on the pinyin filter row.
Render those shortcut labels as a very small second line under the candidate
text, not as inline prefixes. This preserves horizontal room for 10 budgeted
T9 candidates on small screens while keeping long-press discoverability.
Keep enough vertical separation between the candidate glyph and the shortcut
line for Latin descenders and emoji bounds; shortcut labels may sit lower than
typographic center as long as the candidate row remains compact.
For shortcut-label candidates, use an explicit centered text alignment and a
small minimum cell width. This stabilizes symbol/punctuation candidates whose
glyph side bearings or bidi-neutral characters otherwise make them appear
uneven, while preserving the centered Hanzi appearance.
Chinese T9 `1` punctuation should keep Chinese curly quote marks in its local
punctuation list. The broader symbol picker should not apply an extra quote
ordering fallback; keep straight ASCII or full-width quote marks in their normal
symbol-page positions and leave decorative/curly variants out of the ordinary
punctuation page.
Long-pressing a physical digit should select the matching visible bottom-row
candidate. For physical Chinese T9 `2`-`9`, consume key-down locally and send
the digit to Rime only on key-up when no long press was detected. This avoids
the earlier undo path where the first long-press digit had to be removed before
candidate selection.
Apply the same long-press selection rule to `1` while active pinyin composition
exists: short press sends the apostrophe pinyin segmentation separator to Rime
on key-up, and long press selects the first visible Hanzi candidate. Update the
local T9 tracker with the same apostrophe before sending the Rime key so the
local pinyin row and Rime composition stay aligned. When local punctuation
candidates are already visible, consume `1` on key-down and run the short-press
punctuation cycle only on key-up; a long press selects shortcut `1` directly.
Use the Rime bridge's direct input-buffer API for separator insertion:
`getRimeInput()` followed by `replaceRimeInput(input.length, 0, "'", input.length + 1)`.
This matches the existing pinyin-filter replacement path and avoids depending
on whether a synthetic apostrophe key is accepted by the active Rime schema.
Decide whether short `1` is active from local T9 composition state, not only
from editor composing state. The local tracker is the earliest reliable signal
after key-up-delayed digit input.
When the raw T9 composition contains a manual apostrophe separator and no
resolved pinyin segment yet, keep the pinyin option row bound to the first
unresolved digit segment before that separator. This lets the user type
`58'23` and still choose/filter the pinyin for `58` before moving on to `23`.
Selecting a pinyin in this separator state should replace the first digit
segment plus the explicit separator with the normal `pinyin'` Rime replacement,
avoiding a double separator while leaving the following digits available as the
next unresolved segment.
After a separator is entered, preserve the local raw T9 display (`gan'`,
`xi'an`, etc.) as the fallback and ignore the transient empty Rime preedit
produced by separator entry so the local preview does not disappear while Rime
updates. The primary preview should still come from the focused Hanzi candidate
when possible. For separator-aware matching, split the user's raw T9 input on
apostrophes and match each digit segment against the corresponding candidate
comment segment without crossing boundaries. If one segment cannot be validated
against the candidate reading, use the normal digit-based display for that
segment while keeping matched candidate-derived segments. Render the preview
with apostrophes between manually separated segments, and preserve a trailing
apostrophe when the user has just pressed short `1`.
When a pinyin filter is selected from a manually separated composition, keep
the original source raw preedit with apostrophes in `T9CompositionModel`.
Display can replace resolved source digit spans with their chosen pinyin, but
the raw model and replay path must keep the separator boundaries so reopening a
filter restores the same segmented input.
When reopening a selected pinyin segment, restore `pinyin'` back to `digits'`
if the saved raw preedit contains a manual separator at that point. This keeps
Rime's input buffer aligned with the segmented raw model instead of flattening
it back to plain digits. Also add a defensive cleanup path: when the candidate
bubble is suppressed because the local T9 composition is empty, clear any hidden
Chinese T9 engine composition so invisible leftover letters cannot survive
after the UI disappears.
Treat the raw T9 source string with apostrophes as the canonical composition
shape for separator-aware behavior. The top preview should split that raw source
into digit segments and match each segment against one or more Hanzi candidate
comment syllables, advancing through the comment syllables sequentially without
crossing a user-entered apostrophe boundary. Resolved pinyin selections replace
their source digit spans only for display; they must not flatten or hide the
remaining raw segments. The pinyin filter row should ask for the first
unresolved raw segment after the resolved prefix, or the first raw segment
before a separator when there is no resolved prefix yet.
Keep `T9CompositionModel.rawPreedit` source-only: digits `2`-`9` plus
apostrophes. Rime's rendered preedit can still be used as an input-panel
fallback, but it should not overwrite the canonical source model once local T9
tracking exists.
For Chinese T9 segmentation, insert ASCII apostrophe directly into the Rime
buffer when possible. Do not fall back to sending an apostrophe key event,
because the punctuation addon can remap that key to Chinese quote marks.
When the local raw T9 composition ends with an apostrophe, prefer the local
raw-preedit display over candidate-comment preview. The separator has just been
entered and the next segment is empty, so candidate/Rime display can be
temporarily stale or punctuation-mapped.
For Chinese idle `1`, delay opening/cycling the punctuation list until key-up,
the same as the pending-punctuation `1` path. This lets Android report a
long-press repeat before any symbol list is opened; if long-press occurs, cancel
the deferred punctuation action and commit literal digit `1`.
When a Hanzi candidate loses focus, clear its active background immediately
instead of fading it out. The incoming focus may still animate, but the old
highlight must not linger during T9 deletion or candidate-context resets.
When the pinyin candidate row becomes empty, hide it immediately. The expanding
animation is useful when pinyin choices appear, but deletion to an empty pinyin
state should feel like the symbol-list path: a direct state change, not a
decorative collapse.
When the active T9 composition key count is zero and there is no pending
punctuation, suppress the stale Hanzi candidate page as well. This ensures the
whole candidate bubble disappears immediately after deleting the final pinyin
letter or committing the final Hanzi candidate, even if Rime has not yet emitted
the empty candidate event.
In Chinese T9, `1` should only open or cycle local punctuation when there is no
active pinyin input. If pinyin digits are active and no punctuation is already
pending, consume `1` as a no-op so accidental presses do not replace the visible
candidate context with punctuation.
Show small numeric shortcut labels before local T9 selectable options. Use
`1`-`9` for the first nine options and `0` for the tenth. Long-pressing that
physical number selects the matching option when the pinyin row is focused, and
selects the matching pending punctuation/symbol when the local symbol list is
open. Do not apply the bottom Hanzi shortcut until digit-key timing can avoid
first inserting an extra T9 digit into Rime.
Pinyin candidate display should be short and crisp. Keep the pinyin row reveal
under a tenth of a second with minimal vertical travel, and add a left-to-right
content reveal so rows such as `pqrs` feel like they unfold from the start edge.
Keep the Hanzi focus highlight timing/scale close to the earlier softer version;
only stale outgoing highlights should clear immediately.
When a newly filtered Hanzi candidate becomes active, do not set the active
background to full opacity during binding. Start the incoming active highlight
from transparent and let the normal focus animation bring it in, so selecting a
pinyin filter does not produce a strong white flash on the first Hanzi item.
For candidate animation experiments, remove all candidate-area animations. The
pinyin row itself should appear and disappear immediately without reveal,
collapse, translation, or left-to-right scaling, and pinyin/Hanzi focus changes
should switch immediately without animated highlight transitions.
The pinyin row should not be made visible while its synced width is still
unknown, because a later `0 -> candidate width` layout pass reads visually as a
left-to-right reveal even without animation.

## T9 Candidate Refresh Design

When a new bulk-filter request is needed, keep the last stable filtered page
visible while the async request is pending. Replace it only when the matching
request returns. This avoids a transient empty candidate render while preserving
the existing fallback path for the first request or when no previous page exists.
Exception: when the resolved pinyin filter prefix changes, clear the old bulk
page immediately. Showing stale unfiltered Hanzi for the pending frame is more
confusing than a brief empty or locally filtered state.
Also hide the Hanzi row while a selected pinyin segment is still pending engine
replacement. The top reading/pinyin row may remain, but stale Hanzi should not
be used as a placeholder during that asynchronous handoff.

T9 Hanzi rendering should prioritize the user's character budget over the raw
Rime page size. Request a bulk candidate pool for both no-filter and
prefix-filtered T9 states, then slice that pool with `T9CandidateBudget`. Keep
the last stable bulk page visible while a replacement request is pending.

Also ship Rime's default `menu/page_size` as 24 instead of 5, matching the app
preference's upper bound. This improves the normal paged callback and reduces
the chance that the first visible state is starved, but Android should still use
the bulk pool as the authoritative T9 budget source.

Reset the Hanzi candidate cursor from both the visible candidate-list signature
and the T9 context signature. The context signature should cover the current
preedit text and resolved pinyin filter prefixes so deletion and filter toggles
do not temporarily reuse an old highlighted candidate index.

## Non-Goals

- Do not redesign the T9 engine or Rime bridge.
- Do not add user-configurable punctuation maps.
- Do not remove Android inline suggestions globally.
- Do not run full Android builds or device tests in this task.
- Do not change the pending English multi-tap character preview while migrating
  Caps/Shift's no-pending-character feedback.

## Previous Completed Design

- The abandoned remote APK build workflow was deleted instead of disabled.
- The T9 Hanzi candidate character budget remains an integer managed preference
  with default `10` and range `4..24`.
- The Rime plugin remains on inherited shared versioning.
