# Design

## Project Goal

Provide a comfortable Android input method for physical T9-key phones, centered
on Chinese T9 through Rime, English multi-tap, numeric entry, compact on-screen
controls, and a readable keyboard surface.

## Current Task Design

Treat the InkPink gray candidate surface as a DPAD/focus hygiene issue first,
because the user can correlate it with physical direction keys and the affected
candidate/pinyin-chip controls are not `CustomGestureView`. Candidate controls
inside the IME should be touch targets and app-level logical selection targets,
but they should not participate in Android's generic DPAD focus highlight. Keep
the existing pink active-candidate/chip highlight controlled by the T9 state
machine.

## Previous Task Design

Treat the release color issue as a theme-source diagnosis before changing UI
code. The candidate UI should not special-case release builds; if the formal
package looks different, the cause should be either independent release-package
preferences or a view reading the wrong theme attribute. Any fix should keep
the pinyin filter visually tied to the same candidate bubble surface/shadow
model as the top-reading and Hanzi candidate rows.

## Previous Task Design

Use the existing Gradle release pipeline and signing convention to build the
formal 4.0.0 app and Rime plugin packages. Scope the ABI build to
`arm64-v8a,armeabi-v7a` so generated release assets match the previous public
release shape and avoid x86 artifacts.

Publish the GitHub release with the same user-facing structure as `v3.0.1`:
short update bullets first, then the reusable installation tutorial that tells
users to pick either the 32-bit or 64-bit app/plugin pair. The release title and
asset filenames should use `4.0.0`.

Keep Baidu staging separate from Gradle output. Copy the exact four release
APKs into `release-baidu/v4.0.0`, and update the Baidu-side installation guide
to describe the current feature set and dictionary-switch workflow.

For key sounds, public release text should avoid suggesting that Baidu-derived
sounds are bundled. The app owns only a generated built-in fallback sound. Any
Baidu skin sound comes from a user-selected Android `.bds` skin, imported
through the system file picker and copied into app-private storage. The README,
GitHub release body, and Baidu install notes should use the same wording:
manual `.bds` import, editable name, switch/rename/delete management, and clear
failure for encrypted or unsupported skins.

## Previous Task Design

Tune Chinese T9 floating candidate shadows so the compact top-reading bubble
does not create a dark seam against the lower pinyin/Hanzi bubble.

## Chinese T9 Shadow Design

Keep the top-reading bubble's normal platform elevation, because its downward
shadow gives the small reading row useful depth. Keep the lower pinyin/Hanzi
bubble as a direct elevated surface too. Do not wrap it in a clipping container:
the hard-clipped left blur is more visually disruptive than the natural overlap
between the two soft shadows.

Reserve shadow outsets on the candidate popup container itself, then offset the
popup position by the same amount so the visible bubble location remains stable.
This gives elevation shadows room to blur on the left, right, and bottom without
moving the candidate bubbles away from the text cursor.

## 4.0.0 Release Design

Set the release version name to `4.0.0` through the existing Gradle property
and fallback build-logic constant, and bump the ABI-derived base version code
for install/release compatibility. Document the release in the README as a
user-facing feature summary. The README should not mention low-level
state-machine fixes unless they explain a visible workflow; describe them as
smoother password mode, candidate display, and physical-key behavior instead.

## 4.0.0 README Design

Keep the 4.0.0 README section near the top so release users can quickly see why
this build matters before the detailed key tables. Cover only features the user
can perceive:

- Password mode from the three-dot panel and automatic password-field entry.
- Local password input preview, physical-key sync, and hold-to-peek.
- Improved Chinese T9 candidate display and physical-key shortcuts.
- Built-in fallback key sound, physical-key sound control, manual Baidu Input
  Android `.bds` key-sound import, package management, and sound preview.
- Theme/UI polish such as input panel top radius, clearer shadows, and the
  dictionary-switch status entry.

## Password Mode Design

Make password input fields use the existing full on-screen QWERTY keyboard
surface, while preserving the user's normal T9/26-key preference outside
password fields.

## Temporary Full Keyboard Design

Automatic password-field detection is not reliable enough for this workflow, and
verification-code fields need the same keyboard surface without being password
fields. Replace the automatic password override with an explicit temporary
shortcut in `StatusAreaWindow`, the three-dot status/quick settings panel.

The shortcut should be an Android-local status entry named `Full keyboard` /
`临时全键盘`. It owns only transient runtime state, not a stored preference. When
enabled, it asks `KeyboardWindow` to attach `TextKeyboard` and bypass the normal
T9-mode remap from `TextKeyboard` to `T9Keyboard`. It also asks
`KawaiiBarComponent` to show the existing `NumberRow` so password and
verification-code fields get direct digit access. When disabled, it restores the
normal T9/26-key preference.

Keep the implementation as composition of existing UI pieces. `TextKeyboard`
already owns the 26-key layout and per-letter swipe alternatives. `BaseKeyboard`
already routes those swipe alternatives through the configured
`SwipeSymbolDirection`. `KawaiiBarComponent` already shows `NumberRow` for
password fields when the active keyboard is not the numeric keypad, and the same
row can be reused for this manual mode. Do not add a new keyboard class or
duplicate symbol handling.

Starting a new input session should clear the temporary mode so the feature does
not silently become a persistent layout preference.

The previous patch that reused `KawaiiBar`'s number row is not a good UX model:
it hides the three-dot entry/cancel path and splits one mode across unrelated UI
owners. Replace it with a real keyboard layout owned by `KeyboardWindow`.

`TemporaryFullKeyboard` should reuse `TextKeyboard` behavior where possible:
caps handling, punctuation mapping, input-method label updates, popup previews,
and swipe-symbol behavior should stay inherited from the existing text keyboard
implementation. Its layout adds a first digit row above the normal 26-key rows
and changes the bottom-left key to an explicit T9/normal-layout exit key.
Unlike the regular text keyboard, its lowercase state should always display
lowercase letters, because this temporary layout is optimized for dense
password/verification-code entry rather than visual consistency with the normal
keyboard preference.

`KawaiiBarComponent` should not know about this temporary full-keyboard mode.
It should keep showing the normal toolbar/status surfaces, so users can still
open the status panel, clipboard, text editing tools, symbol/emoji pickers, and
hide-keyboard control while the temporary keyboard is active. Picker windows
should return to the temporary full keyboard until the explicit exit key or
status toggle disables it.

The temporary keyboard's own bottom row should stay minimal: no comma key, no
emoji shortcut, and no Unicode shortcut. The only extra picker shortcut inside
the keyboard body is the same `符号` entry used by the T9 keyboard.

The status entry should be named password mode. Enabling it should activate the
`keyboard-us` Fcitx input method, because this is different from the app's T9
English mode and avoids Rime/T9 pinyin consuming letters or digits. Exiting the
mode should restore the previous input method only if the user has not already
switched away from `keyboard-us` while the mode was active.

Because password mode adds a digit row above the 26-key rows, its letter keys
should not reuse the regular text-key label scale, but the labels still need to
remain readable on small phones. Use only slightly smaller lowercase main labels,
keep swipe-symbol labels close to the regular scale, and recover most of the
space by reducing password alphabet-key margins. Every alphabet row, including
`zxcvbnm`, should use the same password-mode alphabet key factory. Keep the
fixed width before the space bar close to the regular keyboard's bottom row so
the space key starts in a familiar position.

Manual password mode should also mirror the native password-field input-method
path. While the mode is active, temporarily add `CapabilityFlag.Password` to the
current input context's capability flags before requesting `keyboard-us`. This
lets Fcitx select the built-in default keyboard input method even if the user
removed `keyboard-us` from the enabled input-method group. On exit, restore the
capability flags from the current input session and then restore the previous
enabled input method when that is still safe.

When a password field auto-enters password mode and the user presses the
in-layout `T9` exit key, that manual choice applies to the current password
field session. The Android `EditorInfo` still reports a password field, but the
effective Fcitx capability flags should temporarily remove `Password` so the
native fallback cannot immediately reactivate `keyboard-us`. The original
EditorInfo-derived flags remain the source of truth for future input starts;
the stripped flags are only an effective runtime override for the manually
disabled session.

The KawaiiBar number row has two password-related triggers: the visible
temporary password keyboard layout and the current input field's password
capability. Leaving password mode is an explicit UI transition, so it should
clear both triggers locally. Do not wait for a later `onStartInput` callback to
clear the cached password capability, because WebView/browser focus changes can
reuse the same input connection and leave the toolbar under the number row.

Password mode needs a manual peek affordance for small screens and captcha
forms. Add a compact eye key to the password keyboard bottom row by narrowing
the space key. The peek action should not exit password mode or restore the
previous Fcitx input method.

Place the peek key on the right side of the space bar, just before Return, so
the primary navigation cluster remains on the left and the auxiliary peek action
reads as part of the right-side command area.

The peek key should be momentary rather than toggled. Model it as a key hold
behavior: down sends `PasswordPeekAction(true)`, while up or cancel sends
`PasswordPeekAction(false)`. This keeps the user in direct control and avoids a
stuck collapsed state after a quick glance.

While the eye key is held, keep only the password keyboard's bottom command row
visible. Do not show a restore bar, because releasing the hold already restores
the keyboard. Also hide the KawaiiBar digit row during the held peek state,
because the user cannot practically press digits before release and the extra
row reduces the visible page area.

The held peek row should keep the same visual row height as the normal password
keyboard bottom row. Derive the peek body height from the current keyboard
height divided by the password keyboard row count instead of using a fixed
small value, so icon baselines do not jump when the body collapses. Keep the
eye key's compact preview bubble enabled as normal press feedback.

Password mode should rely on Android's standard IME avoidance behavior before
falling back to manual peek. Whenever the keyboard body height changes because
password mode enters, exits, or collapses, request a new layout/insets pass so
`FcitxInputMethodService.onComputeInsets()` can publish the new top edge to the
host app. The inset computation should treat a visible password keyboard as a
real on-screen keyboard even if the global virtual-keyboard or T9-layout flags
would otherwise skip the keyboard surface.

Leaving password mode should also avoid showing native input-method transition
states in the keyboard UI. Track the full `InputMethodEntry` that password mode
will restore, not only its unique name. While the restore job is pending,
newly-attached normal keyboards should render that target entry immediately,
and transitional `keyboard-us` IME updates should not be forwarded to the
visible T9/Text keyboard after password mode has been disabled. Keep this
pending display entry alive until a normal keyboard layout has attached and
used it; Fcitx restore events can arrive before layout attachment, and clearing
the pending entry too early exposes the cached `keyboard-us` label again.
The suppression must not depend on the pending entry being non-null: if the
previous method was not captured, the normal keyboard should keep its existing
space-label state and ignore `keyboard-us` until the first non-password IME
event arrives.
Because keyboard view instances are reused, keeping the existing label is only
safe when that existing label is known to be non-password. If the previous IME
was not captured, synchronously derive a fallback non-`keyboard-us`
`InputMethodEntry` from the enabled input-method list before the normal layout
attaches, and use that entry as the pending display target.
Also filter the display side of every IME update by active keyboard layout. When
the active layout is not `TemporaryFullKeyboard`, `keyboard-us` should be shown
only if it is actually present in the user's enabled input-method list. If it is
not enabled, it is the native password fallback and should render as the most
recent or first enabled non-password IME instead. The temporary password layout
still renders `keyboard-us` normally.

Password mode should provide a local typed-text preview for apps that do not
move their password field above the IME. The preview belongs to the IME, not to
the target app: it records text committed through the keyboard while the
temporary password layout is visible, updates on Backspace, and clears on
Return, input restart, finish, or password-mode exit. Do not query the password
field contents through `InputConnection`, and do not mirror Autofill or
1Password inline suggestion content. Place the preview above the keyboard panel
rather than inside KawaiiBar so it does not compete with the digit row or inline
Autofill chips.
Style the preview as part of the input surface: use the same top-corner radius
setting as the IME panel and the same soft elevation/shadow parameters as the
Chinese T9 candidate bubbles. Track a local cursor in the preview buffer.
Left/right cursor movement should first move the target editor cursor through
the normal `InputConnection` path, then move the preview cursor by one code
point so later commits and Backspace operate at the same local position.
Gate this preview behind a default-on keyboard preference placed below the
password toolbar number-row setting. Disabling it should hide and clear the
local buffer, but should not disable password mode, its digit row, or the
password-layout left/right cursor-key handling.
While the temporary password layout is visible, physical phone keypad digits,
`*`, and `#` should be treated as literal password characters before the normal
T9 special-key state machine runs. Commit them through the same service
`commitText()` wrapper used by on-screen keys so the target editor, selection
prediction, and IME-local preview stay in one path. Consume the corresponding
key-up event to avoid a second pass through T9 mode switching, punctuation, or
numeric-mode shortcuts.
Physical Backspace/Delete in password mode should also bypass the ordinary T9
special-key paths. Delete from the target editor through the direct physical
delete helper, and only then update the local password preview with the same
selection-deleted information. This keeps repeats responsive while avoiding
stale preview text.

Input feedback preferences should apply to both input surfaces. On-screen keys
play sound through `CustomGestureView`; physical phone keys should play the
same configured sound effect on the initial key-down event in
`FcitxInputMethodService`, before the key is consumed by T9, candidate
navigation, password mode, or Fcitx forwarding. Repeated hardware key-down
events should not retrigger the press sound.
For sound mode semantics, `FollowingSystem` should continue to use Android's
system sound effects and respect `SOUND_EFFECTS_ENABLED`. `Enabled` should be
app-enforced and play through a separate sonification path so muted system sound
effects do not make the app preference look broken.
Physical key sound should be independently controllable because hardware keys
are used more often and may be annoying in public. Keep the switch default-on
so existing enabled-sound behavior is consistent across input surfaces. The
app-owned sound path should expose a small style list and make key classes
subtly different: ordinary keys should be shortest, Space slightly softer,
Delete distinct, and Return a little more affirmative.
Model the app-owned key sounds after Baidu skin `SOUND_STYLE` categories rather
than using Android `ToneGenerator` beeps. Expose the imported skin-style names
as `Muffled`, `Mechanical`, and `Crisp`, and synthesize each style from a short
noise transient plus a few fast-decaying resonators. Map ordinary keys to the
style-80 sound, Space/function-like keys to a lower/softer style-81 sound, and
Delete/emphasis keys to a sharper style-82 sound. This preserves the useful
structure without copying the original audio assets into the app.
The synthesized parameters should remain traceable to decoded BDS assets:
duration follows each skin's `aj`, `ajgn`, and `ajhc` file lengths, resonator
groups follow the strongest measured bands, and `Mechanical` keeps a secondary
tap to mimic its double-click feel. The explicit app-owned path should route as
media sonification and manage static `AudioTrack` replay directly, because the
Android system key-sound stream can be muted independently of the app setting.
Do not make the resonators the main sound source. They should only color the
short noise transient so the result feels like a sampled skin click rather than
a pitched synthetic tone. For physical keys, classify both literal Space and
the T9 phone's `DPAD_CENTER` input-mode space mapping as the Space/function
sound class.
If synthetic reconstruction still feels wrong, prefer the direct-sample path:
decode the user-provided BDS `aj`, `ajgn`, and `ajhc` assets into small mono WAV
resources and play them with `SoundPool`. This preserves the actual skin timbre
and keeps latency low; `Return` shares the delete/emphasis sample because the
skin sound model only has three sound classes.
The keyboard settings page should include a non-persistent preview action next
to the key-sound style setting. It should play through the app-owned sample
path using the currently selected style and volume, independent of whether
keypress sound is currently enabled, so users can audition a style before
committing to it. The preview UI should expose exactly the three mapping classes
the runtime uses: ordinary, Space/function, and Delete/Return.
Keep the preview action directly below the key-sound style row by assigning
explicit preference order values rather than relying on add/remove order. Render
the preview dialog as themed selectable list rows with standard text
appearances and tinted icons, matching the rest of the settings UI.
The earlier idea of adding purchased skin sounds as bundled styles is
superseded by user-imported sound packs. Persist only the user's local pack
name and extracted private sample files, not app-shipped skin assets.
For BDS sound packs that use numbered style classes instead of the older
`aj/ajgn/ajhc` names, keep the original class mapping when it is explicit in
the skin CSS/config. For the black/white filter packs, use `349 -> aj1` for
ordinary keys, `350 -> aj2` for Space/function keys, and `351 -> aj3` for
Delete/Return/emphasis keys.

User-imported key sounds replace packaged skin samples. The app should expose a
local key-sound pack library in keyboard settings. Import is a guided flow:
launch the system file picker for one Android `.bds` package, prefill the
display name from the selected package filename, then let the user edit and
confirm that name. Store the original file and the three extracted sound
samples under app-private files so users provide their own licensed assets and
the APK remains redistributable. Users can switch between imported packs or
rename or delete imported packs. A default option uses Android's built-in
keypress sound effects and does not require bundled audio assets.

Playback continues to use `SoundPool` for imported packs, but loads sample file
paths from the active imported pack instead of `res/raw`. Treat Return as the
Delete/emphasis sample to preserve the existing three-class BDS model. If the
BDS local entry header marks any entry as encrypted, abort the import and tell
the user encrypted sound packs cannot be used. If a pack lacks all required
sound classes, abort with a missing-sound error. The importer only accepts
Android `.bds` skins.

The default option is not an imported pack and should not depend on Android
system keypress effects, because some target devices expose those APIs but play
nothing. Ship app-owned synthesized default samples instead. They should be
short, neutral sonification assets generated for this project, loaded through
the same `SoundPool` path as imported packs, and used for both preview and
keypress playback.

Performance work should focus only on no-behavior-change hot-path reductions.
Cache rarely changing keyboard and feedback preferences in memory and update
them via `ManagedPreference` listeners, because physical key input, candidate
movement, haptic feedback, and sound feedback call these checks for every key.
Keep listener references as fields because preference listeners are weakly held.
For key-sound sample IDs, use fixed ordinal-indexed arrays instead of per-press
data keys or hash maps.
Prefer branch helpers over allocating temporary key-code collections inside
`onKeyDown()`. Cache `InputDeviceManager`'s T9 layout and floating-candidate
mode preferences for the same reason. For compact candidate rows, compute the
first available measured width through straight-line checks instead of building
temporary lists. Preload only the currently selected key-sound style; other
styles remain lazy-loaded when the user selects or previews them.
Screen-key gesture handling should also avoid preference reads and unnecessary
objects in its touch path. Cache the long-press delay in `CustomGestureView`
and update it from the preference listener, then skip creating a gesture event
when no listener is present. Candidate refresh should use a cached T9-layout
flag instead of reading the keyboard preference during every UI update.
For fixed physical key-code flags, prefer primitive indexed storage while
preserving the existing call-site shape. A tiny key-code flag container can keep
nullable `get` semantics for out-of-range keys without paying `MutableMap`
costs on every physical digit key event.
Candidate adapters that consult layout preferences during bind should cache
those flags with preference listeners. This keeps candidate row refreshes
focused on text binding and avoids shared-preference reads in RecyclerView
binding paths.
For floating candidates, keep the cached T9-layout flag in the parent
`PagedCandidatesUi` rather than registering a preference listener per item view.
The parent already owns binding, orientation, and shortcut-label state, so it
can pass the flag into `LabeledCandidateItemUi.update()` with no display
semantic change.
Within floating candidate item binding, compare candidate label, text, and
comment directly instead of joining them into a synthetic signature string.
This preserves highlight-reset behavior while avoiding bind-time allocation.
Floating candidate shortcut labels should be a constant lookup table. The
display contract is only `1` through `9` and `0`, so binding should reuse those
strings rather than constructing them from positions on each refresh.
Number-mode expression scanning should use a direct character predicate instead
of a temporary set. The allowed-character contract is small and fixed, and a
`when` expression keeps the parser allocation-free during prefix extraction.
For physical digit commit paths, reuse constant digit strings instead of
constructing single-character strings from calculated digits. Keep this limited
to literal commits so T9 composition and display formatting remain unchanged.

The bottom-row `T9`, symbol, and language commands should stay visually
unframed, like the regular compact control row. Tune their fixed cell widths so
the perceived gaps between the three glyph groups are even. Prefer a slightly
narrower `T9` cell when the first visible gap feels too wide, because that pulls
the symbol command left without changing the unframed control style.

Use a narrow leading spacer before `T9` in password mode so the first command
starts with the same visual breathing room as the first symbol command in the
regular T9 layout. Offset that spacer by narrowing the `T9` cell, keeping the
following symbol and language commands in their established positions.

The three-dot status panel may show Fcitx plugin actions that are technically
useful but too noisy for the compact phone UI. Hide the Rime/Zhongzhouyun
`¥ -> $` shortcut by normalizing whitespace in `shortText` and matching actions
containing both `¥`/`￥` and `$`. Keep the filter local to status panel
presentation so the plugin action can still exist internally.

For the compact two-row status grid, keep five columns but make each cell less
dense: use a slightly smaller circular icon background, give labels more
horizontal inset, and keep a stable item height so two-line labels do not crowd
the next row. Avoid changing command order or adding decorative framing.
Use leading alignment for labels instead of centered text so wrapped Chinese
labels read like compact tool names rather than independent badges.
After user review, restore centered labels and use a rounded elevated tile for
each option. Prefer a soft shadow from view elevation instead of a drawn border,
matching the user's request for a blurred-shadow card treatment.
Keep the elevated tile at a fixed compact height and center it within the grid
item so RecyclerView row height does not stretch the cards.
Use square elevated tiles for the status options. Keep the icon and label group
compact inside the square by reducing the icon-label gap and label size, while
preserving centered alignment.
Leave the tile fill color unchanged, so separation must come from geometry:
make each square narrower than the grid column and rely on spacing plus
elevation. Apply text-size adaptation in `setEntry`: the normal label size is
used for short labels, while labels longer than five code points use a smaller
size so they fit without crowding.
If shadow-only separation fails, use an explicit rounded stroke for each tile
and remove elevation. The stroke should be subtle and derived from the theme
text color with alpha, preserving the existing fill color.
After user review, drop the boxed option treatment. Use an unframed compact
cell with the icon circle and a vertical label side by side. Convert the label
to grapheme clusters separated by line breaks so Chinese, emoji, and arrow
labels stack predictably without relying on narrow automatic wrapping.
If the icon circle causes clipping or visual crowding, remove icons from the
status cells. The vertical label becomes the whole control surface: inactive
entries are plain text, and active entries use a rounded active-color label
background with active foreground text. In vertical labels, convert `→` to `↓`
so directional status names read naturally from top to bottom.
Use a horizontal two-row `RecyclerView` grid for the status page. The first page
should keep fixed Android settings on the top row and Fcitx status actions on
the bottom row. Order the fixed settings so input method settings appears as the
last top-row item. Appended future entries should naturally continue to the
right, occupying top and bottom positions in each new column.
Name the status theme shortcut separately from the global theme resource so the
Chinese compact UI can say "skin theme" without changing settings screens.
Keep vertical labels top-aligned with a small top inset to preserve bottom
breathing room in the second row.

Use a task-oriented fallback label for the Rime status action. When the action
has no visible label yet, show `Dictionary Switch` / `词库切换` rather than the
engine name. This makes the fresh-install state less misleading before the user
has deployed the Rime data and before a schema name is available.

Keep submenu labels stricter than top-level status labels. `StatusActionMenuUi`
should display only actions whose `shortText` or `longText` is non-empty. It
must not fall back to the top-level Rime label for child actions, because empty
redeploy/schema items would otherwise look like duplicate Rime entries.

Extended-window title back behavior should be window-owned when a window has a
real parent. Keep the default title back action as returning to `KeyboardWindow`,
but allow an extended window to consume the back click. The Rime dictionary
switch action menu should consume it and attach `StatusAreaWindow`, because that
menu is a child of the three-dot status panel rather than a peer of the
keyboard.

The input panel top radius should be controlled by the theme setting
`inputPanelTopRadius`, but the clipping should not depend on Android outline
behavior. Use a top-only path clip on the full input panel container so every
KawaiiBar state and every keyboard/window surface is clipped consistently. Keep
the KawaiiBar background using the same radius for color continuity, and make
the panel background child fill the full panel before clipping.

Floating Chinese T9 candidate bubbles should separate from white app surfaces
without becoming heavy cards. Use a modestly stronger platform elevation shadow
on both the top-reading bubble and the combined pinyin/Hanzi bubble, with
slightly darker ambient/spot shadow colors on Android P and newer. Keep extra
bottom padding in the wrapper so the blurred shadow remains visible below the
candidate UI.

If the path-clipped input panel does not render rounded top corners reliably on
device, prefer outline clipping with an extended-below rounded rectangle. The
dedicated panel layout still owns the radius setting and invalidation, while
the platform outline renderer handles the actual clipping.

Password-mode exit should clear both visible UI state and Fcitx IME state as one
transition. KawaiiBar must not preserve password-layout number-row state when a
new non-password input session starts, even if Android marks it as a restarting
session. `KeyboardWindow` should also remember the most recent non-`keyboard-us`
Fcitx input method from IME updates, because automatic password fields can make
Fcitx select `keyboard-us` before password mode has a chance to save the
previous method. On restore, prefer the saved previous IME, then the remembered
non-password IME, then the first enabled IME that is not `keyboard-us`.

Password-mode IME restoration must be tied to leaving the temporary mode, not
only to pressing the explicit T9 exit key. Picker windows can keep temporary
mode alive, but switching from a picker into a real non-temporary keyboard
layout must restore the capability flags and previous input method before the
layout switch completes.

The numeric keyboard is a temporary password-mode sublayout when it is opened
from the symbol picker. While temporary mode is active, `NumberKeyboard` should
not restore the previous IME by itself; its `ABC` key should resolve back to
`TemporaryFullKeyboard`. Only switching to a real non-temporary layout such as
T9 or the normal text keyboard should end password mode and restore the prior
capability flags/input method.

Do not duplicate the dedicated digit row in the Q-row swipe alternatives. The
top alphabet row should expose a second set of common password symbols, while
the lower rows keep their existing punctuation alternatives.

To reduce the perceived space between the password digit row and `qwertyuiop`,
adjust the digit-row label placement rather than shrinking or moving alphabet
labels. The regular text key layout should keep its centered text behavior; only
password-mode digit labels use a lower vertical bias.

Password-mode Q-row long-press popups must match the password symbols shown on
the key face. Add a narrow custom-key override to `KeyDef.Popup.Keyboard` so
only password-mode keys bypass the shared Latin `PopupPreset`; regular text
keyboard popups continue to use the global preset.

The password digit row should match the symbol picker's `Density.High` digit
text size so opening the symbol page does not make the visible `1` through `0`
row resize. Keep this separate from the password alphabet label size.

After size matching, tune the password digit row's vertical bias against the
symbol picker screenshot. The goal is stable perceived baseline during
password-mode to symbol-page transitions, while leaving alphabet-row layout
unchanged.

The top control strip should read as the rounded top edge of the IME surface.
Give the `KawaiiBarComponent` container a background with only top-left and
top-right corner radii so all bar states keep the same silhouette. Preserve the
transparent background behavior used when key borders are enabled.

Because the keyboard background is drawn behind the bar, the full input panel
must also be clipped to a rounded outline; otherwise the square keyboard
background fills the apparent bar corners. Use the same small radius so the
container and bar background agree visually.

The full input panel clipping should expose only the upper-left and upper-right
corners. Use an outline rectangle that extends below the visible panel so the
bottom of the keyboard surface stays square.

Popup preview keys should not also draw the regular pressed foreground overlay.
Keep the overlay for keys without preview bubbles, but allow key appearances to
opt out when the bubble is the primary press feedback. Picker pages, including
recently used items, should all receive the same popup listener so feedback is
consistent.
Preview bubble content should match the source key label scale. Carry an
optional text size through `KeyDef.Popup.Preview` and `PopupAction` so compact
function keys keep their original proportions instead of being enlarged by the
generic popup view.
Emoji and emoticon picker pages should use the same short preview path as the
symbol picker. Long-press skin-tone or popup-keyboard choices already dismiss
the short preview first, so the picker-level `popupPreview` gate should not
disable ordinary press feedback for emoji, emoticon, or picker backspace cells.
Short preview bubbles should not share the long-press popup keyboard key width.
Measure the preview label with the selected key text size, add compact
horizontal padding, and clamp between small and large bounds. Icon previews use
the compact minimum/icon size. The long-press popup keyboard keeps its fixed key
width so multi-choice popups stay predictable.

Password mode should keep Fcitx in the direct `keyboard-us` input method for as
long as the temporary password layout is active. If Return or an editor update
causes Fcitx to drift back to another input method while the layout is still
`TemporaryFullKeyboard`, reapply the password capability flags and request
`keyboard-us` again without replacing the saved pre-password input method.

Password-mode auto-enable should use two entry paths. The manual three-dot
toggle remains authoritative and can be turned off for the current password
input session. For automatic entry, `KeyboardWindow` should react to
`CapabilityFlag.Password` in the same `InputView.startInput` broadcast that
already selects number and phone layouts. Because browsers can refine
`EditorInfo` between `onStartInput` and `onStartInputView`, recompute
`CapabilityFlags` from the view-stage `EditorInfo` before broadcasting to the
keyboard UI.

Physical T9 mode does not normally enter the virtual-keyboard start path, but
automatic password mode is a deliberate exception: when the view-stage flags
contain `CapabilityFlag.Password`, start the input UI anyway unless
`InputDeviceManager` has classified the field as dialer passthrough. This lets
`KeyboardWindow` switch to `TemporaryFullKeyboard` from the compact T9 surface.

When a new non-password input session starts while password mode is still
temporary-active, clear the temporary layout state without restoring stale
password capability flags from the old field. Restore the saved pre-password
input method when one exists; otherwise run the non-password `keyboard-us`
leak recovery so ordinary T9 does not remain on `English`.

Same-editor restart preservation is source-aware. Manual password mode can stay
active across restarts even for non-password fields, because the user explicitly
asked for it. Automatic password mode stays active across restarts only if the
new capability flags still report `Password`; otherwise it exits and restores or
recovers the prior input method.

The input UI should also be started for a non-password field when the temporary
password keyboard is currently visible. This is needed in physical T9 mode,
where ordinary non-password fields otherwise skip the virtual-keyboard start
path and would leave `KeyboardWindow` unable to clear automatic password mode.

Move password mode's digit row out of `TemporaryFullKeyboard` and into the
KawaiiBar region. The KawaiiBar can show the existing `NumberRow` whenever the
active keyboard layout is `TemporaryFullKeyboard`, while the keyboard body
returns to the four-row QWERTY-plus-controls structure. This keeps the overall
IME height stable and avoids compressing the alphabet rows.

The KawaiiBar-hosted number row should preserve the previous password-mode digit
appearance: `19f` text and the lower vertical bias used by the old dedicated
password digit row.

Use a user-facing theme preference for the IME top edge radius instead of the
device display rounded-corner radius. Its default is `4dp`, matching the
default T9 pinyin/candidate chip radius, but it remains independent so users can
tune the input panel top edge separately. Apply the same value to both the
KawaiiBar background and the full input-panel outline.

Manual password mode should survive same-editor restarts but still clear when a
new input session starts. `InputBroadcastReceiver.onStartInput` carries the
platform `restarting` flag so `KeyboardWindow` can keep the temporary keyboard
for restarts and reset it for new focus. `FcitxInputMethodService.onStartInput`
also applies the password capability overlay before refocusing Fcitx during
such restarts, preventing a transient switch back to the user's Rime input
method.

Leaving manual password mode should always attempt to restore the exact input
method that was active before the mode entered `keyboard-us`. Do not require
that method to appear in the current enabled-list snapshot, because Fcitx can
select built-in password fallbacks and plugin-backed methods through different
paths. As an additional recovery guard, if a non-password input session starts
while Fcitx is still on `keyboard-us` and `keyboard-us` is not one of the user's
enabled input methods, activate the first enabled non-`keyboard-us` input method.

The password-mode bottom row should not use a separate interactive spacer before
`T9`. Give the `T9` exit key the full leading area formerly occupied by the
spacer plus the key, and shift only its text slightly right so the visible gap
stays balanced while the press highlight reads as one natural key region.
For safety, the password-mode symbol command should come before the `T9` exit
command. This preserves the user's regular left-side symbol-key muscle memory
and moves the destructive password-mode exit one position away from the edge.
After that swap, keep the password-mode symbol key's width equal to the regular
T9 symbol key (`0.15f`) so the label center aligns across layouts. Offset the
extra width by narrowing the adjacent `T9` exit key rather than moving the
language, space, peek, or return controls.

Layout and picker switch keys are navigation controls, not text-entry keys.
Their touch feedback should match the bubble-first keys used elsewhere: show a
short preview bubble for visible labels such as `符号`, `T9`, emoji, and `123`,
and disable the underlying pressed foreground so no gray block appears under
the bubble.

The manual password-mode IME guard should be scoped to the visible password
keyboard, not just the temporary-mode flag. A stale temporary flag must not
override a deliberate user input-method switch back to Zhongzhouyun when the
password QWERTY surface is no longer the active keyboard window.

Use bubble-first feedback for the keyboard surface by default. Space and Return
keep their existing pressed feedback because their shaped backgrounds are part
of the touch affordance; status/settings/KawaiiBar controls stay outside this
keyboard feedback policy. For keys that do not define a custom popup, derive a
small preview label from their text or known icon role. Keep the preview bubble
compact and avoid enlarged text so multi-character labels are not cropped.

Preview bubbles and long-press popup keyboards need separate geometry. The
preview bubble can be shorter than the legacy key preview, but the long-press
keyboard should keep the old vertical offset so it remains above the finger and
does not overlap the short bubble. When a long-press keyboard or menu opens,
dismiss the short preview entry and show only the long-press surface.

Floating Chinese T9 candidates should not be displayed until their content has
been measured and positioned. When the T9 candidate view transitions from
hidden to visible, keep it `INVISIBLE`, request layout, update the position in
pre-draw using the final measured height, then make it visible in that same
pre-draw pass. If the pinyin row needs a deferred width sync, keep the whole
floating view hidden until that sync has completed.

Password mode has two entry paths. Manual entry remains the three-dot status
toggle and works for any field. Automatic entry is opportunistic: when
`onStartInput` receives `CapabilityFlag.Password`, attach
`TemporaryFullKeyboard` and activate `keyboard-us` through the existing password
capability overlay. Do not rely on automatic detection as the only path, and
honor a manual off toggle in the current password input session by suppressing
automatic re-entry until a non-restarting input start resets that suppression.

Picker-window embedded keyboards should receive popup actions directly, even
when the picker page itself disables per-item previews. This keeps bottom-row
controls such as `ABC`, comma, period, and `123` consistent without re-enabling
emoji-cell previews. Image-key previews may carry the same vector drawable used
on the key face.

The compact preview bubble's visible content belongs in the top key-sized
region of the bubble, matching the legacy preview shape. Text previews should
use a fixed-size `TextView`, not auto-scaling text, so multi-character labels
such as `ABC` do not grow or distort. Keep preview text single-line and make
the compact bubble wide enough for common three-character labels.

## 3.0.1 Release Design

Use version name `3.0.1` with ABI-derived version codes based on
`baseVersionCode = 13`. Keep the public GitHub release shape consistent with the
previous release: Chinese release notes, four APK assets, and clear
32-bit/64-bit installation guidance.

The local Baidu Netdisk handoff directory should contain exactly the same four
user-facing APK assets as the GitHub release so users do not have to choose from
debug builds or emulator-only ABIs.

## Chat Return Key Design

Keep the change in `FcitxInputMethodService` and use one shared Return helper
for short `#`, physical Enter, and the on-screen Return key.

The helper should derive actions using TT9's standard-action mask:
`imeOptions & (IME_MASK_ACTION | IME_FLAG_NO_ENTER_ACTION)`. This lets plain
Search/Go/Send/Unspecified perform normally, while `NO_ENTER_ACTION`-combined
variants fall through to Enter instead of being performed as Done/Send and
hiding the keyboard. `IME_ACTION_DONE`, action labels, and Done action IDs also
fall through to Enter.

Device logs show both Discord and QQ chat editors expose `IME_ACTION_DONE`
combined with `IME_FLAG_NO_ENTER_ACTION`, so that combined value must not be
reduced back to plain Done before dispatch.

If no usable action exists, or if a real editor action fails, send the Enter
down/up pair through `InputConnection.sendKeyEvent()` with the same simple event
shape used by TT9's active OK path. If the target connection rejects either
event, fall back to `InputMethodService.sendDownUpKeyEvents(KEYCODE_ENTER)`.
Do not keep the package whitelist, the forced `IME_ACTION_SEND` fallback, or
any short-`#`-only Return branch.

Do not send `KeyEvent.FLAG_EDITOR_ACTION` for these chat fields. Device testing
showed that path can move focus to another UI element. Discord should use the
TT9-style all-zero Enter event. QQ accepts the same Enter event but treats it as
a newline by app design, so there is no IME-side send fix without an app-level
setting or a separate explicit automation strategy.

## Virtual Keyboard Settings Design

Keep the existing preference key and range for the regular virtual keyboard
height. Only change the default portrait value back to 40 so existing user
overrides remain untouched.

Keep the status/settings panel data and actions unchanged. Only increase the
RecyclerView grid span count from four to five so the compact option cells can
share one row when five entries are visible.

For each status/settings cell, keep the icon geometry unchanged, but constrain
the label to the parent cell width with small horizontal padding. Allow up to
two centered lines so Chinese and English labels wrap early instead of
overlapping neighboring cells.

## README Design

Keep the README additions concise and user-facing. Use tables for physical-key
shortcuts and short bullets for optional features. Mention behavior only at the
level a user can test on a phone: what to press, what appears, and what gets
committed. Do not include internal architecture, implementation names, or bug
history.

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

Label this release as `3.0.0`. Gradle's actual `versionName` is resolved from
`BUILD_VERSION_NAME`/`buildVersionName`, then `git describe`, then
`Versions.baseVersionName`, so set the committed `buildVersionName` override and
the fallback base version name to the same value. Increase `baseVersionCode` so
APK updates remain installable, and keep a matching ABI-derived Play release
note file.

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

For future cleanup, keep `FcitxInputMethodService` as the Android/Fcitx boundary
and move self-contained responsibilities outward in small steps. UI-only
transient panels should live under dedicated input view classes. Number-mode
operator mappings and result-choice state can become a small controller with
callbacks for committing text and showing panels. Physical selection mode can
become a state/action controller that owns anchor/focus/action-panel flags.
Chinese T9 preview and segmentation helpers can later move into a model/helper
module, but only after the current behavior remains stable under user testing.

The first cleanup pass should preserve behavior by extracting `InputView`'s
selection action panel and number operator/result panels into view helpers.
`InputView` remains responsible for adding them to the root constraint layout
and exposes the same show/hide methods to the service. The second pass can move
number-mode operator mappings, transient panel state, expression evaluation,
and result-choice key handling into a controller that depends only on callbacks
for text commit, text-before-cursor lookup, and panel visibility.
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

## Status Action Menu Design

The three-dot status panel should not expose implementation fallback icons as
installation instructions. For Fcitx actions, prefer the localized
`shortText`, then `longText`, and finally a localized generic fallback. Rime
actions can be identified by their registered action name/icon prefix
(`fcitx-rime`/`fcitx_rime`) or by Rime deploy/sync submenu items; those should
fall back to a localized Rime/Zhongzhouyun label instead of an empty status
cell.

Submenus opened from the status panel should be ordinary in-IME windows, not
platform `PopupMenu`s. Reuse `InputWindow.ExtendedInputWindow` so the title bar,
back behavior, theme colors, and `InputUiFont` match the rest of the compact
IME UI. Menu rows should activate the original Fcitx action id and then return
to the status panel so deploy/sync actions remain discoverable.

The README installation path should describe the current UI by text:
open the three-dot panel, tap the Rime/current-schema status item such as
`朙月拼音` or `雾凇拼音`, then choose `重新部署` or `同步`. If the Rime status item is
missing, users should first recheck plugin detection, addon enablement, and the
Zhongzhouyun input method entry.

## Password Auto Mode Exit Design

The dedicated `T9` key inside `TemporaryFullKeyboard` is an explicit user exit
from password mode. Treat it the same as manually turning password mode off
from the status shortcut for the current input session. This matters most for
automatic password mode, because the focused editor still advertises password
capabilities after the user exits. Without a same-session manual-off marker,
the automatic path can reattach password mode or keep reasserting the
`keyboard-us` fallback while the visible keyboard is no longer the password
layout.

Keep the suppression narrow: it should block only automatic password re-entry
for the current focused password session. New non-restarting input sessions
clear it, and the user can still turn password mode back on manually from the
status panel.

## Password Preview Session Design

Password preview ownership follows the temporary password input session, not
only the currently attached keyboard window. This distinction matters when the
user opens symbol or number picker surfaces: the full password keyboard view is
temporarily replaced, but the password session and its local preview buffer are
still active. Commit and delete paths that originate from those auxiliary
surfaces should keep updating the same preview until password mode exits or
input focus finishes.

## Chinese T9 Candidate Position Design

The floating candidate window's automatic above/below placement works for
ordinary candidate popups, but Chinese T9 has staged local content: first a
local pinyin preview/filter row, then Rime or bulk-filtered Hanzi candidates.
Using the staged content height to decide above vs below lets the first short
frame appear below the cursor and the later full frame jump above it.

For Chinese T9 composition and pending local punctuation, prefer the
above-cursor placement from the first visible frame. Keep the existing
below/above fallback for non-T9 candidate windows. Clamp the above position into
the visible parent bounds so top-screen fields still remain visible.

## Chinese T9 Candidate Staging Design

Chinese T9 should treat local composition changes and engine candidate updates
as one visible transaction. Local key-down still updates the Kotlin-side model
immediately so pinyin selection and shortcut logic know the current raw digits,
but `CandidatesView` should mark the visible candidate surface as waiting for
the next engine candidate page. While waiting, suppress the floating candidate
window instead of drawing a preview-only or stale-candidate intermediate frame.

Clear the waiting state when a non-empty `PagedCandidateEvent` arrives, or when
the composition becomes empty. Pending local punctuation is excluded because it
is intentionally local and does not wait for Rime candidates.

The waiting state should not blank an already visible complete candidate bubble.
For the first keypress, hiding until the engine page arrives avoids a
half-populated first frame. For later keypresses in the same composition, keep
the last complete frame visible and skip the local partial redraw; the incoming
engine page then swaps the content in place.

For the first pinyin row reveal, treat RecyclerView layout as part of the same
staging boundary. If the pinyin row is not already visible and its adapter list
has changed, keep the row invisible until the RecyclerView's next pre-draw
after layout. This prevents child views from appearing one at a time while
preserving immediate in-place updates for rows that are already visible.

If revealing in that pre-draw still exposes the first attached child, defer the
actual `VISIBLE` transition by one animation frame. The row remains laid out as
`INVISIBLE`, so it participates in measurement, but the user does not see the
intermediate child attach sequence.

Because Chinese T9 may submit the pinyin chip list while the entire candidate
view is still hidden waiting for engine candidates, carry a pending first-reveal
flag across that hidden update. The flag is cleared only when the pinyin row is
actually shown or when the row is reset to hidden with no pinyin candidates.

The pinyin-row width synchronization listener may update layout params while
the delayed reveal is pending, but it must not make the row visible directly in
that state. Only the delayed reveal path owns the first `VISIBLE` transition.

Do not change the first-row `topReading` to solve second-row chip flicker. The
top row has separate semantics and should keep showing the current reading
preview. The pinyin selection row should instead avoid RecyclerView
virtualization: it has only a compact set of chips, so a horizontal container
can synchronously rebuild all chip views and then reveal them as one row.

After the pinyin row becomes a synchronous horizontal container, remove the
RecyclerView-specific delayed reveal path. The row should show immediately once
its width can be synchronized with the Hanzi row; delaying by pre-draw or
animation frame makes the row feel slower than the other candidate surfaces.

## Chinese T9 Local Commit Design

Local Chinese T9 punctuation is IME-owned text, but it still needs to behave
like any other commit into the target editor. Route local punctuation and the
literal characters emitted immediately after it through the service-level
commit helper instead of calling `InputConnection.commitText` directly. This
keeps composing-range cleanup, selection prediction, and editor-facing commit
behavior consistent with Rime candidate commits without disabling host-app
spellcheck for normal text fields.
