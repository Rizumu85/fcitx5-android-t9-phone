# Analysis

## Current Task

Diagnose the intermittent gray candidate background seen with the InkPink
theme, especially in Google Keep title editing.

## InkPink T9 Candidate Surface

- User clarified that the issue is not a release-vs-debug difference. It is
  theme-specific to InkPink and appears intermittently after editing in Google
  Keep's title field.
- The gray region is not limited to the pinyin filter row; the Hanzi candidate
  row can also show the same gray-looking background.
- InkPink uses a white keyboard/candidate surface over a gray outer keyboard
  background. Any transparent gap inside the floating candidate rows is much
  more visible than in themes where the two colors are closer.
- The candidate popup has an outer rounded bubble background, but the pinyin
  row and Hanzi row contain scroll/recycler children with transparent padding
  and overflow space. In app-specific editor paths and during popup reuse or
  measurement, those transparent areas can visually expose the gray outer
  background or shadow instead of the intended white candidate surface.
- User observed that physical direction-key hover may be involved. This is
  plausible because the previous DPAD focus fix covered `CustomGestureView`
  keyboard/bar controls, while floating Hanzi candidates and pinyin chips are
  plain clickable `TextView` instances. Clickable text views can still enter
  Android's focus/highlight chain when DPAD navigation is used.
- Success criteria: both T9 pinyin filter row and Hanzi candidate row should
  always render on the same candidate bubble surface color, independent of the
  selected app/editor and without changing the active pink highlight. Physical
  direction keys should not leave Android system focus/hover highlights on
  pinyin chips, Hanzi candidates, or their scroll/recycler containers.

## Previous Task

Diagnose why the formal release build shows a strange/different background
color behind the pinyin filter UI.

## Formal Build Pinyin Filter Background

- User reports that the formal release build's pinyin filter background color
  looks different from the expected/debug appearance.
- Need reproduce on the device with the formal IME selected, then compare the
  candidate/top-reading/pinyin-filter UI color sources with debug behavior.
- First hypotheses to check:
  - Release and debug packages have independent shared preferences, so the
    formal build may be using a different skin/theme than the debug package.
  - The pinyin filter row may still read a semantic theme color that differs
    between selected skin variants, instead of the same candidate bubble
    surface/background used elsewhere.
  - The apparent color may be an overlay/shadow/pressed/focus state from the
    candidate popup rather than the real background color.
- Success criteria: identify whether this is a data/theme mismatch or a code
  bug, and if it is code, make the release and debug pinyin filter backgrounds
  use the same intended surface color without changing unrelated keyboard
  theming.
- Device screenshot with the formal IME showed the pinyin-filter row sitting on
  a gray-looking rectangular strip inside the lower candidate bubble. The active
  pinyin chip and active Hanzi candidate both used the same pink theme active
  color, so the mismatch was not the selected-chip color.
- Root cause: `pinyinRowWrapper` had its own `elevation = 1f` while being
  embedded inside `bubble2Wrapper`, which already owns the candidate bubble
  background and shadow. On the light/pink formal theme this internal elevation
  reads as a separate gray background behind the pinyin filter row.
- Device check after removing the internal elevation still showed the gray
  strip. The remaining cause is the `HorizontalScrollView`/container used by
  `T9PinyinChipAdapter`: in the input-view themed context it can draw a default
  row background instead of staying transparent. The pinyin chip scroller and
  its child container should explicitly be transparent so the shared lower
  candidate bubble surface is the only background.

## Previous Task

Build, install, stage, and publish the signed 4.0.0 formal release packages.

## 4.0.0 Formal Release

- Build signed release APKs with the keystore at
  `/Users/rizum/StudioProjects/fcitx5-android-t9-phone.jks`.
- Publish only the ARM artifacts, matching the previous release format:
  app and Rime plugin, each for `arm64-v8a` and `armeabi-v7a`. Do not include
  `x86` or `x86_64` APKs in the GitHub release or Baidu staging folder.
- Install the formal app and formal Rime plugin on the connected phone after
  the build, then switch the active IME to the formal package.
- Create a new GitHub release for `v4.0.0` in
  `Rizumu85/fcitx5-android-t9-phone`, named `安装包下载v4.0.0`, using the
  existing tutorial-style body format from `v3.0.1`.
- Stage the same four APKs in `release-baidu/v4.0.0` so they can be uploaded
  elsewhere.
- Update the Baidu-side installation help files so their feature introduction
  matches the README and mentions the 4.0.0 password mode and current
  key-sound behavior.
- The public key-sound explanation must state that 4.0.0 does not ship
  third-party Baidu skin sounds. It ships a built-in fallback sound and lets
  users manually import Baidu Input Android `.bds` skins from the system file
  picker. Imported samples are copied into app-private storage, so the original
  downloaded `.bds` file can be deleted after import. Encrypted or unsupported
  `.bds` skins cannot be used.
- Signing credentials are not stored in the repository. If the local Gradle or
  shell environment does not provide the keystore password and alias, they must
  be supplied before a real signed release can be built.
- Current blocker: the local release build accepts
  `fcitx5-android-t9-phone.jks`, but `:plugin:rime:packageRelease` fails because
  the signing config has no `storePassword`. The active shell/local Gradle
  files do not expose `SIGN_KEY_PWD` or `signKeyPwd`, so the keystore password
  is needed before signed APKs can be rebuilt and published.

## Current Success Criteria

- Four signed APKs exist for version `4.0.0`: app/plugin Rime times
  `arm64-v8a`/`armeabi-v7a`.
- No x86 APK is uploaded or staged.
- The connected phone has the formal app and formal Rime plugin installed, and
  the active IME is the formal component.
- GitHub release `v4.0.0` contains the four expected assets and the new feature
  notes.
- `release-baidu/v4.0.0` contains the same four APKs, and the Baidu install
  help text describes the current installation flow and user-visible features.
- README, GitHub release notes, and Baidu help files all describe manual
  `.bds` key-sound import rather than bundled key-sound styles.

## KawaiiBar DPAD Focus Bug

- Reproduced on device by sending DPAD left/right/up/down while the toolbar is
  visible. Android moves focus into the KawaiiBar and the left toolbar toggle
  button receives the system focus/ripple highlight.
- The highlighted state is not a KawaiiBar state-machine state; it is View
  focus navigation treating clickable `ToolButton` views as DPAD focus targets.
- Follow-up: after removing `ToolButton` from focus navigation, the same DPAD
  focus highlight moved to the on-screen `符号` key. That key is a `KeyView`,
  and `KeyView` also inherits from `CustomGestureView`.
- Root cause: all IME-local touch controls built on `CustomGestureView` should
  be touch targets, not Android DPAD focus targets. The fix belongs in
  `CustomGestureView`, not only in KawaiiBar's `ToolButton`.
- Success criteria: physical arrow keys should keep moving text/candidate focus
  only, and no KawaiiBar tool button should remain visually highlighted unless
  it is actually touched.

## Previous Task

Reduce Chinese T9 floating candidate shadow overlap between the top-reading
bubble and the lower pinyin/Hanzi bubble.

## Chinese T9 Shadow Overlap Follow-up

- Device screenshot shows the top-reading bubble's lower platform shadow
  landing on the lower pinyin/Hanzi bubble's top edge. Because both bubbles use
  the same strong elevation shadow, their shadows overlap and create a darker
  visible line between the two bubbles.
- User preference after the first fix: keep the top-reading bubble's original
  downward shadow, and instead remove the lower pinyin/Hanzi bubble's upward
  shadow where it overlaps the top-reading shadow.
- Follow-up correction: the harsh seam is not mainly caused by top/bottom
  shadow overlap. It is caused by the lower bubble's left-side blur being
  clipped by the wrapper, so the soft shadow stops abruptly and reads like a
  solid gray strip between the two bubbles.
- Debug-package correction: after each debug install the selected input method
  must be `org.fcitx.fcitx5.android.debug/org.fcitx.fcitx5.android.input.FcitxInputMethodService`.
  Selecting the release package can show the pink release theme and invalidate
  screenshot checks.
- Root cause refinement: the candidate popup view itself also tightly wrapped
  the bubble content. Android elevation shadows extend outside the bubble
  background, so the popup bounds need explicit horizontal/bottom shadow
  outsets; otherwise the left-side blur is clipped at the popup edge.
- Success criteria: the top-reading row should keep its previous visible
  downward shadow, while the lower pinyin/Hanzi bubble should keep an
  uninterrupted soft side shadow without a clipped left edge.

## 4.0.0 Release Prep

- The formal APK should report version name `4.0.0`. This project derives the
  Android `versionName` from `buildVersionName` in `gradle.properties`, falling
  back to `Versions.baseVersionName` when no override is provided.
- The release should also bump `Versions.baseVersionCode` so formal APKs can be
  installed over 3.0.1 and accepted by release channels.
- README updates should focus on user-visible additions rather than internal
  debugging, restoration guards, or performance implementation details.
- User-visible 4.0.0 highlights include password mode, password input preview
  and peek, physical-key preview synchronization, improved key sounds and
  previews, candidate/UI polish, and the clearer dictionary-switch status entry.

## Password Mode History

Make password fields automatically show a password-friendly on-screen keyboard:
the existing 26-key QWERTY layout, the existing top number row, and the existing
letter-key swipe symbols.

## Status Action Label Follow-Up

- The compact status panel previously used `中州韵` / `Rime` as the fallback
  label for the Rime status action when Fcitx did not provide visible action
  text. On a fresh installation this is confusing because the user has not yet
  deployed or selected a real Rime schema.
- The friendly fallback should describe the user task: dictionary/schema
  switching.
- Rime action submenus should not reuse the top-level fallback. During redeploy
  or schema transitions, some submenu actions can have empty display text; those
  rows should be hidden instead of being rendered as the fallback label.
- Success criteria: the top-level empty Rime status action shows the dictionary
  switching fallback, while action-menu rows only appear when Fcitx provides a
  real short or long label.
- Follow-up bug: after opening the dictionary-switch action menu, the title-bar
  back arrow returned directly to `KeyboardWindow`. This happens because
  `KawaiiBarComponent` hard-codes every extended window's title back button to
  the keyboard. The dictionary-switch action menu needs a parent-window return
  path back to `StatusAreaWindow`.
- Top-corner follow-up: the input panel top radius is already a theme setting,
  but the visible effect is fragile. The panel relied on `ViewOutlineProvider`
  with a round rect taller than the view to fake top-only rounded corners, while
  the KawaiiBar also drew its own rounded background. On some devices or during
  state changes, this can expose square corners. The input panel should own a
  direct top-only clip path and keep the panel background filling the whole
  clipped surface.
- Chinese T9 candidate shadow follow-up: the floating candidate bubbles can
  blend into white app backgrounds because only the lower pinyin/Hanzi bubble
  had a very low-opacity elevation shadow, and the top-reading bubble had none.
  Apply the same stronger soft shadow to both bubbles and reserve enough bottom
  padding so the platform shadow is not clipped.
- Device check correction: replacing the top panel outline with a path clip made
  the bottom input panel read as square on the test phone. Keep the dedicated
  layout and runtime setting refresh, but use Android outline clipping again
  with an extended-below rounded rect so the visible top corners return.
- Password-mode residual-state audit: switching between normal text fields and
  password fields can leave two independent stale states. The KawaiiBar keeps
  `isPasswordKeyboardLayout` during restarting input sessions, so the number row
  can remain visible even after the keyboard layout/input method has returned
  to Zhongzhouyun. Separately, Fcitx may switch to `keyboard-us` through its
  native password fallback before `KeyboardWindow` saves the previous IME, so
  `imeBeforeTemporaryTextKeyboard` can be null and normal fields can stay on
  the English fallback.
- Root cause: manual password-mode disable only suppressed the Android layout
  auto-enable path. It restored Fcitx capability flags with the original
  `Password` flag still set, so Fcitx's native password fallback was free to
  switch back to `keyboard-us` even though the user had explicitly exited
  password mode for the current password field.
- Success criteria: while the user has manually disabled password mode for the
  current password field, the keyboard should stay in the normal T9 layout, the
  toolbar number row should remain hidden, and Fcitx should use the previous
  non-password input method instead of `keyboard-us`. Focusing a different
  non-password field should still restore normal Rime/T9 behavior.
- Manual password-mode follow-up: after entering password mode from the
  three-dot status panel and returning to T9, the KawaiiBar number row can cover
  the toolbar even when a normal text field has focus. This happens because
  exiting the temporary password layout clears `isPasswordKeyboardLayout`, but
  can leave the KawaiiBar's cached password-capability state true until Android
  sends a clean non-password input-start callback. The exit path should clear
  both password-layout state and password-capability number-row state.
- Password-mode bottom-row safety follow-up: placing `T9` at the far-left
  position conflicts with regular T9 muscle memory where the left-side command
  opens symbols. In password mode this can accidentally exit password mode when
  the user intended to open symbols. Swap the symbol and `T9` commands so the
  symbol key owns the first bottom-row position.
- Password-mode symbol alignment follow-up: after the swap, the password-mode
  symbol key still used a narrower `0.13f` width while the regular T9 symbol
  key uses `0.15f`. This makes the symbol label center slightly misalign with
  the user's learned position. Match the symbol key width and compensate by
  narrowing the adjacent `T9` exit key so the rest of the row keeps its spacing.
- Password preview physical-key follow-up: the IME-local password preview is
  updated only through the service-level `commitText()` wrapper. Physical phone
  keypad digits, `*`, and `#` can be intercepted by T9 special-key handling or
  forwarded to Fcitx without passing through that wrapper, so the app receives
  the character while the preview misses it.
- Success criteria: when the temporary password keyboard is visible, short or
  repeated physical digit, `*`, and `#` key presses should insert those literal
  password characters through the same service commit path as on-screen keys,
  update the local preview, and consume the matching key-up so the T9 mode
  state machine does not also treat them as mode-switch or punctuation keys.
- Physical Backspace follow-up: the idle physical backspace path uses
  `deleteBeforeCursorDirectly()`, which deletes from the target editor without
  calling `handleBackspaceKey()` or `recordPasswordInputPreviewBackspace()`.
  This leaves the IME-local password preview stale after deleting characters
  typed with physical keys.
- Success criteria: while the temporary password keyboard is visible, physical
  Backspace/Delete should delete from the target editor and update the local
  password preview in the same key-down pass, including repeated deletes and
  selected-text deletion.
- Password preview indirect-commit follow-up: the IME-local password preview is
  still updated only through the service-level `commitText()` wrapper. Auxiliary
  UI paths are riskier because they can commit literals without using that
  wrapper. The password digit row uses `SymAction`, and `NumberModeController`
  was given a lambda that directly calls `currentInputConnection.commitText()`.
- Device retest finding: routing those paths through `commitText()` fixed the
  visible password digit row, but picker-window characters still missed the
  preview. Opening a picker detaches the keyboard window, so checks based on
  "temporary password keyboard is visible" become false even though the
  temporary password input session is still active.
- Success criteria: while the temporary password keyboard is visible, text
  inserted from auxiliary symbol/number UI should reach the target editor and
  update the local password preview through the same service commit path. When
  a picker window replaces the keyboard surface, preview recording and preview
  chrome should stay active until the temporary password input session exits.
- Password peek follow-up: on small screens, the password QWERTY layout can
  hide the password field or captcha image. A manual peek control should be
  available directly inside password mode because KawaiiBar is occupied by the
  digit row. The bottom-row space key can give up a small amount of width for an
  eye icon. Pressing it should temporarily collapse the keyboard body to a
  narrow restore bar while keeping password mode active.
- Visual follow-up: the eye key reads better to the right of the password-mode
  space bar, as an auxiliary control before Return, rather than between the
  language key and space bar.
- Interaction follow-up: the peek key should behave as a press-and-hold
  affordance. Pressing the eye should immediately collapse the keyboard, and
  releasing or canceling the press should immediately restore it.
- Momentary peek follow-up: because release now restores the keyboard, the
  restore button and password digit row are not useful during peek. The peek
  state should keep only the password keyboard's bottom command row visible,
  hiding the KawaiiBar number row and the letter/digit rows until the key is
  released.
- Peek polish finding: removing the eye key preview also removed visible press
  feedback. The preview should return, but only as key feedback, not as a
  persistent restore affordance. The bottom row shifted because peek used a
  fixed `48dp` keyboard body instead of the password keyboard's real per-row
  height; the held peek height should match one normal password keyboard row.
- Password-mode viewport follow-up: the manual peek key is a useful escape
  hatch, but the normal full password keyboard should still participate in
  Android's IME avoidance path. The service reports IME insets from
  `InputView.keyboardView` through `onComputeInsets()`. Two fragile points can
  make host text fields stay behind the keyboard: switching into password mode
  dynamically changes the keyboard height without explicitly asking the IME
  window to recompute insets, and `onComputeInsets()` only treats virtual
  keyboard or T9 mode as visible keyboard cases instead of recognizing the
  visible password keyboard directly.
- Same-password-field T9-exit root cause: `InputView` can be created or restart
  Fcitx event handling while Fcitx's cached IME is the native password fallback
  `keyboard-us`, before `KeyboardWindow.onStartInput()` receives the password
  `EditorInfo` and enables the temporary password layout. During that gap the
  normal T9 keyboard is attached and receives cached `keyboard-us`, so the space
  label flashes `English`. Autofill/1Password inline suggestions do not directly
  switch the input method, but they can make password-field input view restarts
  more common, which exposes this cached fallback path. Treat `keyboard-us` as a
  display-only password fallback on non-password layouts unless the user has
  explicitly enabled `keyboard-us` in the enabled input-method list.
- Cleanup finding: temporary debugging pages and rejected password-mode
  restoration hypotheses should not remain in project docs or source after the
  final root cause is known.
- Password preview follow-up: some apps and WebViews do not move their password
  field above the IME even after the keyboard reports correct insets. Password
  mode needs an IME-local preview that shows what the user typed through this
  keyboard session. It should not read the target app's password text or mirror
  Autofill/1Password-filled content. Clear it when password input starts,
  finishes, exits password mode, or Return is pressed.
- Password preview polish: the preview should look like the existing input UI,
  not a separate card. Reuse the input panel radius and the Chinese candidate
  bubble shadow. The local preview also needs a cursor; left/right cursor
  movement should update both the target editor and the local preview cursor.
- Password preview preference follow-up: the IME-local password preview is
  useful as a default safety net, but it shows sensitive typed content. Add a
  default-on keyboard setting directly under the password toolbar number-row
  setting so users can disable the preview without affecting password mode
  itself.
- Physical key sound finding: on-screen keys call `InputFeedbacks.soundEffect`
  from `CustomGestureView.ACTION_DOWN`, but physical T9 keys enter through
  `FcitxInputMethodService.onKeyDown()` and never reach that touch handler.
  The sound preference can therefore appear enabled while physical key presses
  stay silent.
- Device audio finding: the existing sound path uses
  `AudioManager.playSoundEffect`, which is tied to Android's system sound-effect
  stream. On the test device, `STREAM_SYSTEM` is muted even though the app
  preference can be set to enabled, so both on-screen and physical keys stay
  silent. The app's explicit `Enabled` mode should use an app-owned sonification
  path; only `FollowingSystem` should depend on system key-sound settings.
- Key-sound preference follow-up: users may want screen keys to make sound
  while physical keys stay quiet. Add a default-on physical-key sound switch
  beneath the existing key-sound controls. Also add a key-sound style list so
  the explicit app-owned sound path can offer a few short tone profiles, with
  slightly different tones for ordinary keys, Space, Delete, and Return.
- Baidu skin sound finding: the imported `.bds` files are malformed ZIPs for
  standard extractors, but the local deflate streams can still be read. The
  three skins provide only three key sound files per style and map keys through
  `SOUND_STYLE=80/81/82`: ordinary letters and digits mostly use 80, bottom-row
  function keys and Space mostly use 81, and Delete/emphasis keys use 82. The
  shipped audio is short MP3/AAC under misleading `.ogg` names, roughly
  180-340 ms with tails. Recreate this structure with app-synthesized short
  transients instead of bundling the original audio.
- Key-sound silence follow-up: the app-owned `AudioTrack` path could fail
  before playback because a static track was paused, flushed, and reloaded even
  when it was not already playing; the resulting `IllegalStateException` was
  swallowed and `play()` was skipped. The explicit app-owned sound path should
  rewind a static track by stopping it only when it is currently playing, then
  set the playback head to zero and play. It should also use media-routing
  audio attributes so the explicit app setting is not muted by Android's system
  sound-effect stream.
- BDS sound-analysis follow-up: decoded skin audio confirms that the three skin
  styles are not simple beeps. `Muffled` has low resonances around
  260-760 Hz with a long soft tail, `Mechanical` has a brighter double-tap
  transient around 1-7 kHz, and `Crisp` has a shorter high transient with
  strongest early energy around 760-1450 Hz. The synthesized app sounds should
  use those approximate durations and resonator groups for the three
  `SOUND_STYLE`-like key classes.
- Key-sound likeness follow-up: the first BDS-derived synthesis still sounded
  unlike the skin preview because it over-weighted clean sine resonators. BDS
  key sounds read more like short sampled noise/transient clicks with only
  subtle tonal color. The synthesized path should keep the BDS-derived
  durations and frequency groups, but make noise/envelope the dominant part and
  keep resonators quiet.
- Physical-space sound bug: physical key sounds were chosen before the service
  remapped T9 input-mode keys, and the sound classifier did not include
  `KEYCODE_SPACE` or the phone's `DPAD_CENTER -> SPACE` path. Physical Space
  therefore used the ordinary-key style instead of the Space/function style.
- Direct-sample decision: synthetic reconstruction still does not match online
  skin previews closely enough because the preview sound is the actual skin
  sample, including its recording/noise/compression characteristics. Technically
  the reliable implementation is to decode the BDS `aj`, `ajgn`, and `ajhc`
  audio into small mono WAV resources and play them through `SoundPool`.
  `Return` can share the BDS emphasis/delete sample because the skin only
  provides three sound classes.
- New Meow skin sound finding: the purchased English and Korean variants have
  identical audio for the same crisp/muffled style. Their differences are only
  keyboard-image resources and preview metadata, so the app can use the English
  crisp and English muffled packages as the sound source. These should be added
  as new named sound-style options instead of replacing the existing crisp and
  muffled options.
- Black/white filter sound finding: the purchased Android BDS packages
  `可爱1`, `脆1`, and `闷1` are extractable and use `SOUND_STYLE=349/350/351`
  with files `aj1/aj2/aj3`. These map cleanly to ordinary, function/space, and
  emphasis/delete classes. The earlier plan to add them as bundled sound styles
  is superseded by the user-imported sound-pack design.
- Key-sound preview follow-up: choosing between imported sound styles is hard
  from names alone. The keyboard settings page should provide a quick local
  preview for the three sound classes used by every style: ordinary keys,
  Space/function keys, and Delete/Return emphasis keys.
- Key-sound preview polish: `PreferenceScreen` sorts entries by `Preference`
  order, so removing and re-adding trailing preferences can still leave the
  preview action in the wrong visual position. Insert the preview by adjusting
  preference order values. The preview dialog should also use themed list rows
  instead of plain platform buttons. The original synthesized styles should
  carry a small `Mini` / `小小` prefix so they do not read like the imported
  skin-sample styles.
- Performance audit finding: several hot input paths read managed preferences
  through delegated properties on every physical or on-screen key event. Those
  delegates ultimately hit `SharedPreferences`, which is unnecessary for values
  that change rarely. Cache input-feedback preferences inside `InputFeedbacks`
  and cache frequently checked service-level keyboard toggles inside
  `FcitxInputMethodService`, updating the caches through existing preference
  change listeners.
- Sound playback hot path finding: the app-owned `SoundPool` path constructs
  an `AppSoundKey` data object and performs map lookup on every sound effect.
  The sound matrix is fixed by enum style and three sample classes, so an
  ordinal-indexed `IntArray` cache can avoid per-key allocation while preserving
  the same lazy load and pending-play behavior.
- Second performance audit finding: after the feature work settled, a few hot
  paths still do small avoidable work on every physical key press or T9
  candidate refresh. `onKeyDown()` builds temporary key-code collections for
  password/navigation checks, `InputDeviceManager` reads two managed
  preferences through `SharedPreferences`-backed delegates during physical key
  evaluation, and `CandidatesView.syncPinyinRowWidthToCandidates()` allocates
  short lists while synchronizing the pinyin row to the Hanzi row. Key-sound
  startup also preloads every bundled sound style even though only the current
  style needs low-latency playback.
- Success criteria: remove those per-key/per-refresh allocations and
  preference reads without changing visible keyboard behavior, candidate
  behavior, or the selected key-sound style's first-play responsiveness.
- Third performance audit finding: screen-key touch handling still reads the
  long-press delay through a preference delegate for every down/up path, and
  `dispatchGestureEvent()` allocates a gesture event even when no gesture
  listener is installed. Floating candidate refresh also reads the T9 layout
  preference directly in every `updateUi()` call.
- Success criteria: cache those rarely changing preferences with weak-listener
  safe field references, and skip gesture-event allocation when there is no
  listener, without changing long-press timing, double-tap timing, swipe
  behavior, or T9 candidate visibility.

## User-Imported Key Sounds

- User report: the bundled Baidu-derived key-sound samples cannot be safely
  shipped in an open-source APK because their license only allows commercial
  use and does not grant redistribution as open-source assets.
- Replace the 4.0.0 key-sound implementation so the app no longer packages
  those samples. Users should import one `.bds` file, then confirm or
  edit a display name prefilled from the selected package name. Keep the copied
  file and extracted samples inside the app's private storage on the phone.
- Import should reject encrypted packs with a clear message. The BDS local ZIP
  entry encryption flag is enough to identify packs the app cannot decode.
- Runtime playback should keep the existing three sound classes: ordinary
  keys, Space/function keys, and Delete/Return keys. Supported BDS layouts are
  the older `aj`/`ajgn`/`ajhc` names and numbered `aj1`/`aj2`/`aj3` names.
- Success criteria: no bundled Baidu sample resources remain in the app, the
  settings UI lets users pick a package before naming it, preview and keypress
  playback use the imported samples, and encrypted or incomplete packs report
  an actionable error.
- Device smoke test finding: an Android `.bds` selection correctly opens the
  app's name dialog with the package-name-derived default, but the encrypted-pack
  scanner kept reading past the ZIP local file headers into the central
  directory. That can surface an internal `EOFException` before normal
  extraction runs.
- Follow-up device finding: some BDS packages contain both `.aiff` and `.ogg`
  entries with the same `aj*` base names. Taking the first matching entry can
  save AIFF bytes under the app's `.ogg` sample filenames, which makes
  `SoundPool` fail to load the imported samples. The importer should choose the
  OGG entries that Android can play directly.
- The import flow only supports Android `.bds` skin packages. The earlier iOS
  package compatibility path was removed so the UI and code do not imply
  support for iOS packages.
- Refined storage requirement: imported packs are retained as a local library.
  Users can switch between imported packs, delete packs they no longer like,
  and fall back to a default option that uses Android's built-in keypress sound
  effects without shipping additional audio files.
- UI bug: the import-name dialog's single-line `EditText` text collides with
  the underline on the device. The name field needs enough vertical padding or
  a wrapper layout so the baseline clears the underline.
- Follow-up UI requirement: long Baidu Android `.bds` skin names should remain
  readable in the local pack manager, and the import summary should say these
  are key sounds extracted from Baidu Input Android `.bds` skins.
- Follow-up management requirement: imported key-sound packs should support
  renaming after import. The existing local pack name is stored in each pack's
  `name.txt`, so renaming can update only that metadata file without touching
  extracted samples or the original imported `.bds`.
- Device/user follow-up: the Android default key-sound option is still silent on
  the target phone even after explicitly loading Android system sound effects.
  Replace that fallback with app-owned synthesized default samples so the
  default option is asset-clean, redistributable, and independent from device
  system sound-effect behavior.
- Cleanup requirement: remove obsolete single-pack directory migration code and
  stale iOS-package compatibility wording now that the pack library only accepts
  Android `.bds` skins.
- Fourth performance audit finding: physical T9 digit and star long-press
  tracking uses a `MutableMap<Int, Boolean>` even though key codes are small,
  fixed integer values. This adds hashing/boxing overhead in the physical-key
  down/up path.
- Success criteria: keep the existing nullable `map[keyCode] == true` behavior
  at call sites, but back it with a small primitive boolean array.
- Fifth performance audit finding: horizontal candidate binding strips pinyin
  comments based on the T9 layout preference, but the adapter reads that
  preference through a delegate while binding candidate rows.
- Success criteria: cache the horizontal candidate adapter's T9-layout flag so
  candidate binding avoids preference reads while preserving the existing
  comment-stripping behavior.
- Next single-step target: floating candidate item binding has the same pattern.
  `LabeledCandidateItemUi.update()` reads the T9-layout preference through a
  delegate for every bound floating candidate row. The surrounding
  `PagedCandidatesUi` is the stable owner for the candidate list, so it can
  cache the flag once and pass it into each item bind.
- Success criteria: floating candidate item binding should no longer read the
  T9-layout preference directly, and candidate label/comment/shortcut rendering
  should remain unchanged.
- Next single-step target: `LabeledCandidateItemUi.update()` still builds a
  combined `label|text|comment` signature string for every bind just to detect
  candidate content changes. Compare the three candidate fields directly
  instead, avoiding one string allocation per floating candidate bind.
- Success criteria: active highlight reset should still happen only when the
  candidate content changes, but candidate binding should not allocate a
  signature string.
- Next single-step target: floating candidate shortcut labels are derived with
  `(position + 1).toString()` during binding. The label set is fixed for
  positions 0-9, so the adapter can reuse a small constant table instead of
  allocating a short string during candidate refresh.
- Success criteria: shortcut labels should remain `1` through `9` and `0`, with
  no behavior change when labels are hidden.
- Next single-step target: number-mode expression extraction creates a
  temporary `setOf(...)` inside the character predicate. Replace it with a
  direct `when` predicate so expression evaluation does not allocate that set.
- Success criteria: the accepted expression characters should remain digits,
  decimal point, arithmetic operators, parentheses, spaces, and `π`.
- Next single-step target: physical digit handling commits one-character digit
  strings from several hot paths by calling `toString()` on calculated digits.
  Reuse a fixed digit string table for literal digit commits while leaving
  composition display formatting untouched.
- Success criteria: long-press digit commits, number-mode digit commits, and
  password-mode physical digit commits should produce the same characters.

## Temporary Full Keyboard Mode

- User testing found the password-field auto trigger unreliable: 1Password,
  browser password fields, and app password fields did not consistently expose
  capability flags that activated the automatic 26-key override.
- User also wants the same full-keyboard surface for verification-code fields,
  which are not necessarily password fields.
- User decision: stop relying on automatic password detection. Add a temporary
  full-keyboard toggle in the input method's three-dot/status shortcut area.
- Reuse finding: `TextKeyboard` already provides the 26-key layout, with digit
  alternatives on the top letter row and punctuation alternatives on other
  letter keys.
- Reuse finding: `AlphabetKey` plus `BaseKeyboard` already implement swipe
  symbol input through `Behavior.Swipe` and `swipeSymbolDirection`.
- Reuse finding: `NumberRow` already provides the top digit row, and
  `KawaiiBarComponent` can show it above the keyboard.
- Implementation finding: `KeyboardWindow.switchLayout()` normally remaps
  `TextKeyboard` back to `T9Keyboard` while T9 mode is enabled. The temporary
  full-keyboard mode needs a narrow bypass for that remap.
- Success criteria: tapping the status shortcut toggles a temporary 26-key
  layout with the top number row; tapping it again restores the normal T9/26-key
  preference; ordinary input start resets the temporary mode so it is not a
  persistent user setting.
- Follow-up bug: once temporary full-keyboard mode shows the number row, the row
  replaces the toolbar and hides the three-dot status shortcut, so the user has
  no visible way to cancel the mode. Use the number-row collapse gesture as the
  keyboard-surface cancellation path and switch back to the normal layout.
- Follow-up bug: after opening symbol/emoji windows and returning, the keyboard
  can leave a stale number row even though the current window is not the
  temporary full keyboard. Number-row visibility must be tied to the active
  keyboard window, not only the temporary flag.
- UX review: the patched approach is still wrong because it combines one mode
  across `StatusAreaWindow`, `KeyboardWindow`, and `KawaiiBarComponent`. The
  entry point disappears after activation, cancellation depends on a hidden
  number-row gesture, and picker windows can still expose mismatched state.
- Redesign direction: make temporary full keyboard a real `KeyboardWindow`
  layout. Its own keyboard body should contain the digit row, 26-key text rows,
  swipe symbols, and an explicit key to return to the normal T9 layout. The
  KawaiiBar should remain the toolbar/status surface and should not be replaced
  by this mode's digit row.
- Follow-up UX tuning: in this password/verification-code-focused layout,
  lowercase state should display lowercase letters even if the regular keyboard
  preference keeps letters uppercase. Remove comma, emoji, and Unicode shortcuts
  from this layout; keep only the same symbol entry used by the T9 keyboard.
- Follow-up behavior requirement: entering this mode should activate the Fcitx
  `keyboard-us` input method, not the app's T9 English mode, so letters and
  digits commit directly instead of feeding Rime/T9 pinyin. On exit, restore the
  previous input method if the current method is still `keyboard-us`.
- Rename the user-facing status entry from temporary full keyboard to password
  mode because the feature is for passwords and verification codes.
- Follow-up visual tuning: the password keyboard has five rows, so reusing the
  regular text key label sizes makes the main letter and swipe-symbol labels
  collide. Password-mode alphabet keys need smaller main and alt labels.
- Follow-up activation bug: checking whether `keyboard-us` was enabled before
  activation could skip the password-mode input-method switch on device. The
  mode should directly request `keyboard-us` activation and let Fcitx handle the
  known built-in input method.
- Follow-up user testing: the password-mode alphabet labels were reduced too
  aggressively for a small screen, and the `zxcvbnm` row did not use the tuned
  password alphabet key at all because it still reused the regular
  `TextKeyboard` third row.
- Input-method activation finding: Fcitx's native password-field path can reach
  `keyboard-us` even when the user removed it from the enabled input-method
  group, because `CapabilityFlag.Password` bypasses the current group and falls
  back to the default keyboard layout. Manual password mode should mimic that
  capability for the current input context instead of mutating the user's
  enabled input-method list.
- Success criteria for this follow-up: all three alphabet rows use the same
  password-mode label sizing, the labels are only slightly smaller than the
  regular text keyboard, extra vertical room comes from password-key spacing,
  and entering/exiting password mode temporarily applies/restores the original
  capability flags so `keyboard-us` can be selected through the same path as
  automatic password fields.
- True-device screenshot finding: the password-mode bottom-left `T9`, `符号`,
  and language keys should remain unframed controls, but their visual gaps must
  read evenly. Balance the fixed widths so the glyph gap from `T9` to `符号`
  matches the gap from `符号` to the language icon.
- Follow-up visual correction: user testing still perceived `T9` as too far
  ahead of the symbol command. Narrow the `T9` cell so the symbol command moves
  left and the first two commands read as one tighter control cluster.
- Follow-up reference comparison: after comparing the real T9 bottom row,
  password mode needs leading whitespace before the first `T9` glyph, similar
  to the natural leading whitespace before `符号` in the T9 layout. Add a narrow
  spacer before `T9` and reduce the `T9` cell by the same total width so the
  symbol command's position does not drift.
- Status shortcut cleanup request: keep the emoji-face shortcut visible, but
  hide the money-symbol shortcut from the Android three-dot status panel.
  User testing confirmed the effective match is the Rime/Zhongzhouyun plugin
  action text shown as `¥ -> $`, not the Chinese Addons icon name. Hide only
  Fcitx status actions whose compact short text contains both a yen/yuan sign
  and `$`, leaving the underlying plugin action available internally.
- Status panel visual finding from device screenshot: with two rows and five
  columns, the status panel reads crowded because the 48dp icon circles dominate
  each cell, labels have little horizontal inset, and the first/last columns sit
  close to the screen edges. Tune the cell geometry rather than changing the
  feature set: slightly smaller icon circles, more label inset, and stable cell
  height.
- Follow-up visual direction: centered two-line labels still look visually
  unstable in a compact tool grid because each wrapped label has a different
  center. Align labels to the leading edge inside each cell so row text starts
  consistently while remaining vertically centered.
- Follow-up user decision: restore centered labels, but make each option a
  rounded elevated tile. The tile should use soft shadow/elevation rather than
  a stroke outline so the panel reads as grouped controls without adding hard
  borders.
- Device screenshot finding: letting the rounded tile fill the RecyclerView's
  tall grid row makes each option look too heavy and pushes the second row to
  the bottom edge. Keep the tile height compact and center it within the grid
  row.
- Follow-up user direction: keep the card treatment closer to the first elevated
  version, but make each option a square tile. Tighten the distance between icon
  and label, use a slightly smaller label, and center the compact content group
  inside the square.
- Follow-up square-card refinement: same-color elevated cards can visually merge
  into a strip when the tiles are too wide and close together. Make the square
  smaller than the column width so the shadows break between options. Keep the
  default label size, but shrink labels whose text is longer than five code
  points.
- User rejected the shadow-only approach because it still does not show
  independent boxes clearly enough. Switch from elevation separation to an
  explicit rounded stroke box for each option.
- Follow-up user decision: abandon boxed status options. Try vertical status
  labels instead, because the compact five-column panel does not have enough
  horizontal room for long Chinese labels. Place the icon and vertical label as
  one compact group, without card backgrounds or strokes.
- Follow-up visual issue: active entries still draw the icon circle background,
  and that can be clipped in the dense vertical layout. Remove icons from the
  status option cells entirely. For active entries, emphasize the vertical text
  itself with the active background color and active foreground text color.
  Replace right arrows with down arrows in vertical labels.
- Follow-up layout direction: make the three-dot status page scroll
  horizontally with two rows. Put the fixed Android settings on the top row of
  the first page, with input method settings as the last top-row item. Fcitx
  status actions fill the bottom row, and any later extra items continue to the
  right in top/bottom pairs.
- Password-mode font check: `TemporaryFullKeyboard` does not request bold text
  for alphabet keys. It only sets a smaller password-mode alphabet size
  (`21f`) and uses the regular `AlphabetKey`/`KeyTextStyle.Normal` path, so any
  heavy appearance likely comes from the selected font face or the device's
  rasterization at that size.
- Follow-up status polish: the status-panel theme shortcut should read as
  "skin theme" in Chinese to distinguish it from generic app themes. Move
  vertical labels slightly upward so the lower row has more breathing room.
- Password-mode restore bug: entering password mode stores the previous Fcitx
  input method and activates `keyboard-us`. Opening the symbol picker keeps
  `temporaryTextKeyboard` active because picker keys are not real keyboard
  layouts. From that picker, switching to the numeric keyboard calls
  `KeyboardWindow.switchLayout(NumberKeyboard.Name)`. The old code set
  `temporaryTextKeyboard = false` for that real keyboard layout but did not call
  `restoreInputMethodBeforeTemporary()`, so `keyboard-us` remained active. A
  later return to T9 only changed the keyboard layout and no longer had a
  temporary-mode transition to restore the previous IME.
- Follow-up restore correction: the numeric keyboard is also reachable as a
  temporary password-mode sublayout through the symbol picker. Treating every
  real keyboard layout except `TemporaryFullKeyboard` as "leaving password
  mode" makes the `ABC` key return to normal T9 instead of the password QWERTY
  layout. Keep temporary mode active for `NumberKeyboard`; restore the previous
  capability flags and input method only when switching to a real non-temporary
  layout such as T9 or the normal text keyboard.
- Password-mode visual finding: the top Q row repeated the dedicated digit row
  by using `1` through `0` as swipe alternatives. Replace those alternatives
  with common symbols that are not already used on the lower alphabet rows, so
  the top digit row has a clear purpose and the Q row remains useful.
- Follow-up spacing correction: the visual gap is between the dedicated digit
  row text and the first alphabet row, not the alphabet label size. Keep the
  password alphabet sizes readable and move only the password digit-row labels
  slightly downward inside their cells.
- Follow-up popup mismatch: letter long-press popups do not read the displayed
  alt label. They use `PopupPreset` by the popup keyboard label, so `q` through
  `p` still offered `1` through `0` even after password-mode display and swipe
  symbols changed. Password-mode Q-row keys need custom popup keyboard entries
  that match their password symbols, while the regular text keyboard should keep
  the shared Latin popup preset.
- Follow-up digit jump: the symbol picker uses `PickerPageUi.Density.High`
  with `19f` text for its first-page digits, while password mode used `20f` for
  its dedicated digit row. Switching from password mode to the symbol picker
  therefore made the same `1` through `0` row visibly resize. Match only the
  password digit-row size to the symbol picker; keep password alphabet labels at
  their readable size.
- Follow-up digit alignment: after matching the text size, the password digit
  row still sits slightly higher than the symbol picker's first digit row during
  the transition. Move the password digit-row labels a little further downward
  without changing the alphabet rows.
- Top bar polish request: the user wants the KawaiiBar/top control strip to
  have rounded upper-left and upper-right corners. Apply the radius to the
  KawaiiBar container background so idle, candidate, and title/status-extension
  states share the same top-corner treatment.
- Screenshot finding: rounding only the KawaiiBar background is not visually
  sufficient because the full keyboard background sits behind it as a square
  surface. Clip the whole IME panel container to a rounded outline so the upper
  corners reveal the app behind the keyboard.
- Follow-up corner correction: clipping the whole IME panel with a normal round
  rectangle also rounds the bottom of the keyboard surface, but only the top
  edge should be rounded. Keep the same top clipping while extending the outline
  below the view so the bottom corner arcs fall outside the visible panel.
- Popup-preview visual finding: keys that show a preview bubble also keep the
  regular press foreground highlight, leaving a gray block visible under the
  bubble. Preview-capable keys should keep the bubble feedback but skip the
  underlying press tint.
- Picker finding: recently used emoji/symbol cells did not receive the shared
  popup action listener, so they lacked the same preview bubble behavior as the
  other picker pages. The recent page should use the same preview listener.
- Follow-up popup scale finding: compact preview bubbles used one fixed label
  size, so smaller function keys such as `ABC` and `符号` looked enlarged in the
  bubble. Preview labels should inherit the source key's display text size when
  it is known, with a default only for generic picker cells.
- Follow-up picker preview finding: emoji and emoticon picker windows still set
  `popupPreview = false`, so `PickerWindow` filtered out the preview actions
  emitted by their grid cells and by the emoji-page backspace key. Enable the
  same compact preview path for these picker windows now that long-press
  popups dismiss the short preview first.
- Follow-up bubble-width finding: the compact preview width was widened as a
  fixed visual compromise, but it should instead measure the pressed key
  content and clamp to compact bounds so short labels do not create overly wide
  bubbles.
- Password auto-enable retry finding: Jelly/WebView password fields can expose
  a different `EditorInfo` between `onStartInput` and `onStartInputView`. The
  system dump showed the focused password field as `inputType=0xe1`, but the
  UI still received the earlier non-password capability flags, so automatic
  password mode did not activate. Recompute capability flags at
  `onStartInputView` before broadcasting to the keyboard UI.
- Follow-up auto-enable finding: in physical T9 mode,
  `InputDeviceManager.evaluateOnStartInputView()` returns false because the IME
  is not using the Android virtual-keyboard path. That skipped
  `InputView.startInput()` entirely, leaving `KeyboardWindow` with no password
  start-input broadcast even though the compact input view stayed visible. For
  password fields, still start the input UI unless the field is dialer
  passthrough.
- Password-mode leak review finding: when automatic password mode enters a
  password field, Fcitx may already have selected `keyboard-us` before
  `KeyboardWindow` saves the previous input method. Leaving that password field
  for a normal T9 field must therefore clear the temporary layout and stale
  password capability state without losing the chance to recover from a leaked
  `keyboard-us` input method.
- WebView restart finding: switching from an automatic password field to a
  normal text field in the same WebView can still arrive as `restarting=true`.
  Restart preservation must distinguish manual password mode from automatic
  password mode: manual mode may survive non-password restarts, but automatic
  mode should survive only while the new capability flags still contain
  `Password`.
- Follow-up leak finding: `TYPE_TEXT_VARIATION_WEB_EDIT_TEXT` is correctly not
  a password field, but physical T9 mode skips `InputView.startInput()` for
  non-password fields. If the visible keyboard is still the temporary password
  layout, the UI must still receive that non-password start-input event so it
  can exit before `onImeUpdate` reasserts `keyboard-us`.
- Chinese T9 floating-candidate flicker finding: the floating `CandidatesView`
  can become `VISIBLE` before its T9 preedit/pinyin/candidate rows have
  completed measurement and before the final above-cursor position has been
  applied. On slower devices this exposes one frame at the lower anchor, then
  the pre-draw reposition moves it to the correct upper position. Deleting the
  final pinyin can similarly expose stale measured content for a frame before
  the view is hidden.
- Password-mode auto-enable retry: now that password mode is a self-contained
  keyboard layout with an explicit `T9` exit key, password fields that do expose
  `CapabilityFlag.Password` can automatically enter the same password mode.
  Keep the three-dot manual toggle as the fallback for fields and WebViews that
  still do not report password capability. If the user manually turns password
  mode off inside a password field, suppress re-auto-enabling for that input
  session so the manual decision sticks until a new field starts.
- Password-mode regression: pressing Return can leave the temporary password
  keyboard visible while Fcitx has switched away from `keyboard-us`. While the
  temporary keyboard remains active, input-method updates should reassert
  `keyboard-us` through the password capability path without overwriting the
  original input method to restore later.
- Password-mode layout finding: keeping a dedicated digit row inside the
  keyboard body forces five rows into the same height as other keyboard
  layouts, making the alphabet rows feel cramped. Since password mode now has
  an explicit T9 exit key, the digit row can occupy the KawaiiBar area instead
  of the keyboard body, preserving a stable total IME height while giving the
  alphabet rows more usable space.
- Mode-switch feedback finding: text and image keys that only switch layouts or
  picker pages still use the generic pressed foreground, so `符号`, emoji, and
  password-mode `T9` show a gray block while comparable text-entry keys use a
  preview bubble. These navigation keys should use the popup preview as their
  primary press feedback and opt out of the pressed foreground overlay.
- Follow-up digit-size correction: after moving password-mode digits into the
  KawaiiBar number-row surface, they inherited the shared toolbar number row's
  larger `21f` label size. Password mode should keep the previous dedicated
  digit-row scale, `19f`, with the same lower baseline bias.
- Follow-up corner-radius request: the top IME panel should not follow the
  device display rounded-corner value because some Android devices do not expose
  a useful radius. Default the panel top radius to the same `4dp` scale as the
  T9 pinyin/candidate chips, but keep it as a separate theme setting.
- Follow-up password-mode restart bug: pressing Return can make the editor
  restart the same input connection. `FcitxInputMethodService.onStartInput()`
  then writes the original editor capability flags back to Fcitx before
  `KeyboardWindow.onImeUpdate()` has a chance to reassert password mode, so
  Fcitx briefly switches back to the user's Rime/T9 input method and only then
  returns to `keyboard-us`. Same-editor restarts should keep the manual
  password-mode capability overlay before Fcitx refocuses.
- Switching-code review finding: `onStartInput()` did not distinguish a new
  input session from a same-editor restart, even though the user-facing policy
  is "reset password mode for new input fields, keep it for the active field."
  Passing the `restarting` flag through the input broadcast lets
  `KeyboardWindow` apply that policy explicitly.
- Follow-up leak bug: after leaving password mode, ordinary T9 can show
  `English（中）`. That means the visible layout and T9 mode returned to normal,
  but Fcitx's active input method is still the password fallback
  `keyboard-us`. The restore path was too conservative because it restored the
  previous input method only when the current method was still `keyboard-us` and
  the previous method appeared in `enabledIme()`. Rime/plugin methods may not
  match that enabled-list check exactly, and a leaked `keyboard-us` can also be
  present at a new non-password input start.
- Follow-up bottom-row hit-area issue: password mode used a real `SpacerKey`
  before the `T9` exit key to create visual leading whitespace. That spacer had
  its own key view, so pressing the blank area could draw an independent press
  highlight, while the actual `T9` key was only `0.08f` wide and felt too tight.
  The leading whitespace should be part of the `T9` key's touch/highlight area
  instead of a separate key.
- Follow-up IME fallback bug: the password-mode reassert path only checked the
  broad `temporaryTextKeyboard` flag. If that flag survived after the password
  keyboard was no longer the visible foreground layout, a user switch back to
  Zhongzhouyun could be immediately overwritten by `keyboard-us`, producing the
  visible `English（中）` flash and trapping the user in the fallback input
  method. Reassert `keyboard-us` only while the temporary password keyboard is
  actually the visible keyboard window.
- Follow-up key feedback request: all keyboard-surface keys except Space and
  Return should use popup-bubble feedback instead of the gray pressed overlay.
  The bubble should be shorter and use normal-size text so labels such as
  `符号` are visible instead of being cropped.
- Follow-up popup tuning: the first compact bubble was too short and could be
  covered by the user's finger. It also reused the same height value as the
  long-press popup keyboard offset, so shrinking the preview pulled long-press
  multi-option popups too low and made the two popup surfaces conflict. Keep
  separate preview and long-popup offset heights. When the long-popup opens,
  remove the short preview bubble entirely; do not show both at once.
- Follow-up missing-feedback finding: picker windows attach their embedded
  bottom keyboard with only `keyActionListener`, not `popupActionListener`.
  Therefore the symbol page's `ABC`, quick comma, period, and number-mode switch
  did not show the new bubble feedback even though their key definitions had
  preview popups.
- Follow-up emoji preview request: icon keys such as emoji should show the same
  vector icon inside the preview bubble, not a replacement emoji character.
- Follow-up preview alignment finding: the icon preview was centered in the
  whole taller bubble while text previews sit in the top key-height region, so
  emoji looked too low. `ABC` also appeared enlarged because the preview used
  `AutoScaleTextView`; use a normal `TextView` and put both text and icons in
  the top preview region.

## 3.0.1 Release Preparation

- Bump the release version from 3.0.0 to 3.0.1 and advance the ABI-derived base
  version code from 12 to 13.
- Keep the GitHub release asset set aligned with the previous release pattern:
  app APK plus Rime plugin APK for `arm64-v8a` and `armeabi-v7a`.
- Preserve a local installer directory for manual Baidu Netdisk upload.
- Install the new `arm64-v8a` release app and Rime plugin on the connected
  phone, then select the release IME.
- Success criteria: release APKs build, the connected device reports the
  updated formal package installed, the GitHub tag/release points to the release
  commit, and the local installer directory contains the four user-facing APKs.

## Chat Return Key Behavior Request

- User report: in Discord and QQ chat boxes, pressing the physical Enter key or
  short `#` inserts a newline instead of sending the message.
- User decision: revert the whitelist/forced-send approach and copy the TT9
  confirmation-key strategy instead.
- Original routing: short `#` in T9 mode eventually called
  `handleReturnKey()`. Physical Enter can also be deferred through the physical
  OK selection-mode path and then call `handleReturnKey()` on key-up. The
  on-screen Return key also calls the same helper through the virtual Fcitx
  Return event.
- `handleReturnKey()` currently honors `IME_FLAG_NO_ENTER_ACTION` and
  unspecified/no-action editors by sending a raw Enter key. That is correct for
  ordinary multiline editors, but it makes chat apps that expose a multiline
  message editor default to line breaks.
- User testing found the package-agnostic `IME_ACTION_SEND` fallback can simply
  hide the keyboard without sending. Do not run that fallback globally, and do
  not keep the user-editable whitelist for it.
- TT9 reference check: its OK key first performs the editor action selected
  from `EditorInfo`. It treats `IME_ACTION_UNSPECIFIED` as a real action to
  perform, matching the LatinIME/OpenBoard behavior noted in its source. If no
  action applies, its on-screen OK key falls back to
  `InputMethodService.sendDownUpKeyEvents(KEYCODE_ENTER)`.
- Follow-up reference check: TT9's action selection does not directly perform
  fields with an `actionLabel` or `actionId == IME_ACTION_DONE`; it maps those
  to its internal Enter action. When its on-screen OK key cannot perform a real
  editor action, it calls `InputMethodService.sendDownUpKeyEvents(ENTER)`.
  Mirror that action-selection shape because QQ still inserts a newline with
  the first approximation while Discord works.
- User testing after that approximation: search/newline behavior remained OK,
  but Discord regressed. Restore direct labeled/custom action handling, because
  Discord needs that path. The remaining likely QQ issue is the old early
  `IME_FLAG_NO_ENTER_ACTION` branch suppressing explicit or unspecified editor
  actions before they can run.
- User testing after restoring direct action handling and removing the
  `IME_FLAG_NO_ENTER_ACTION` early branch: QQ and Discord both hide the IME,
  while Search still works. That suggests `IME_ACTION_DONE` or an action masked
  without considering `IME_FLAG_NO_ENTER_ACTION` is being performed and treated
  as "close keyboard". Match TT9's standard-action mask with
  `IME_FLAG_NO_ENTER_ACTION` so those cases fall through to Enter instead.
- Logcat check from the device:
  - Discord chat field: `inputType=147457`, `actionId=0`,
    `actionLabel=null`, `imeOptions=1342177286` (`DONE` combined with
    `NO_ENTER_ACTION` and `NO_EXTRACT_UI`).
  - QQ chat field: `inputType=655361`, `actionId=0`, `actionLabel=null`,
    `imeOptions=1073741830` (`DONE` combined with `NO_ENTER_ACTION`).
  Therefore both apps hide the keyboard when Done is performed; they need the
  TT9-style Enter fallback for `DONE | NO_ENTER_ACTION` instead.
- The updated arm64 debug APK was built and installed on the test device after
  this fix. Package inspection reported `lastUpdateTime=2026-04-29 00:12:31`,
  so follow-up device testing should exercise the new return-key path.
- Follow-up TT9 inspection found an important distinction: TT9's active OK path
  sends the Enter down/up pair through `InputConnection.sendKeyEvent()` with a
  simple `KeyEvent`, while the earlier local fallback used
  `InputMethodService.sendDownUpKeyEvents()`. Prefer the InputConnection path
  for the TT9-style Enter route and fall back to the service helper only if the
  target connection rejects either event. The updated APK was installed with
  `lastUpdateTime=2026-04-29 00:18:41`.
- Follow-up logcat after user retest confirmed QQ enters `route=tt9Enter` and
  accepts both Enter down/up events, but still does not send. A narrower
  QQ/Discord `KeyEvent.FLAG_EDITOR_ACTION` experiment was installed with
  `lastUpdateTime=2026-04-29 00:27:01`, but user testing found it moves focus
  to another UI element. Remove that experiment and keep only the TT9-style
  all-zero Enter path. Discord is expected to send on that path; QQ treats the
  same accepted Enter event as newline by app design.
- Search boxes and other editors with explicit IME actions should keep their
  own action when short `#` is pressed.
- Use the same shared Return helper for short `#`, physical Enter, and the
  on-screen Return key. Use TT9's standard-action mask so actions combined with
  `IME_FLAG_NO_ENTER_ACTION` are not performed as Done/Send and then interpreted
  as "close keyboard". Keep custom/labeled actions out of direct `DONE`
  execution for the same reason.
- Success criteria: explicit search/send/done fields still perform their own
  action except TT9-style labeled/Done fields that use the Enter route;
  unspecified-action chat editors can send if the app handles its default
  action; no-action text fields still receive an Enter key event.

## Virtual Keyboard Settings Follow-Up

- The regular virtual keyboard portrait height default was lowered to 39% in a
  previous iteration. Restore the default to 40% while leaving landscape and T9
  keyboard defaults unchanged.
- The in-IME status/settings panel currently lays out options in four columns.
  Change it to five columns so five option cells can fit on one row.
- This is a defaults/layout-only adjustment. Avoid behavior changes to status
  actions, theme handling, or virtual keyboard launch logic.
- Follow-up: with five columns, some labels can become visually crowded because
  the label view uses its natural content width. Constrain labels to the cell
  width and allow a small number of centered wrapped lines.

## README User Documentation Update

- The README is currently written as an end-user Chinese guide. Keep that style
  for this edit so the new content fits the existing document.
- Document only features users can operate: Chinese T9 candidate shortcuts and
  segmentation, number-mode operator/result shortcuts, physical OK selection
  mode, custom fonts, and new themes.
- Avoid internal implementation details, bug-fix history, parser internals, or
  refactoring notes.

## Global Feature Review Follow-Up

- The number operator hint panel displays `*` on the physical `*` key, so the
  active panel must let a short `*` press commit `*` instead of treating it as a
  dismiss-only key. `#`, Back/Delete, and OK remain dismiss controls.
- The number operator mapping should not advertise an operator that the local
  expression parser cannot evaluate. Change the `6` long-press mapping from
  unsupported exponent `^` to parser-supported multiplication `*`.
- The physical selection action panel should ignore repeat key-down events just
  like the number operator and equals-result panels. This prevents a held
  direction/delete key from executing once, closing the panel, and then leaking
  later repeats into the normal input pipeline.
- Follow-up operator layout adjustment: keep the feature unintegrated for now,
  and change the displayed/committed number-mode long-press symbols to
  `2=+`, `4=<`, `5=/`, `6=>`, and `0=.`.
- Follow-up operator tuning: comparison symbols are not useful enough for the
  calculator-style layer. Change `4` to `π` and `6` to `≈` as direct literal
  symbols.
- Follow-up parser request: connect `π` to the equals-result calculator so
  expressions such as `π=`, `2π=`, and `π/2=` can be evaluated. Keep `≈` as a
  literal symbol because it is not an operation or equals trigger.
- Follow-up approximate-result request: connect `≈` to the same optional result
  flow as `=`, but format the offered result with at most two decimal places.
  The literal `≈` should still be committed first, and confirmation should only
  append the formatted result after it.
- Number-mode consolidation review: the operator cheat sheet and result choice
  are mutually exclusive transient panels. Model them as one numeric panel state
  instead of independent booleans so pre-IME Back, key-down priority, dismissal,
  and repeat handling stay centralized.

## Chinese T9 Cleanup Follow-Up

- The current Chinese T9 behavior is accepted by user testing and should not be
  behaviorally redesigned.
- Start cleanup with one independently testable piece: encapsulate pending local
  punctuation state. The existing four fields (`set`, `index`, `text`, and
  deferred one-key flag) always move together and can be grouped without
  changing Rime composition, pinyin filtering, or Hanzi preview logic.
- After the punctuation state cleanup is verified, the next small cleanup is to
  extract Chinese-mode digit key-down and key-up branches into dedicated helper
  functions. This should be a behavior-preserving move only: no Rime buffer,
  pinyin preview, or candidate-selection rules should change.
- User verification found the extraction preserved most behavior, but revealed
  a missed shortcut case: while Chinese T9 composition is active, long-pressing
  `0` should select the tenth visible Hanzi candidate instead of committing
  literal `0`.
- The separator glyph concern was inspected without changing behavior. The T9
  composition tracker stores ASCII apostrophe (`'`), `forwardChineseT9SeparatorShortPress`
  sends ASCII apostrophe to Rime, and the preview join paths render ASCII
  apostrophes. Curly single/double quote glyphs are present in the Chinese
  punctuation candidate list, so any observed curly quote should be localized
  by UI location before changing separator logic.
- Correction: the earlier symbol-picker quote ordering fallback came from a
  misunderstanding. Chinese T9 `1` punctuation already contains the intended
  Chinese curly quote marks. Remove only the extra picker ordering preference
  that pushed curly quotes into the ordinary punctuation page.
- Bug report: pressing short `1` for Chinese T9 segmentation can sometimes
  produce a visible Chinese quote mark (`“`). The likely cause is the separator
  fallback path: if direct Rime buffer insertion fails, `sendKey("apostrophe")`
  lets the Fcitx punctuation addon translate apostrophe as a Chinese quote.
  The segmentation path should never send a punctuation-mapped apostrophe key.
- Follow-up: the quote can still appear only immediately after the first
  separator, before the next digit is typed. That points to display ordering:
  while the raw T9 source ends with an apostrophe, candidate-comment/Rime
  display is transient and should not outrank the local raw preedit display.
- Structural review: `FcitxInputMethodService.kt` is now about 3900 lines and
  combines Android IME lifecycle, key dispatch, Chinese T9/Rime composition,
  English multi-tap, number-mode calculator flow, physical selection mode, and
  several UI panel controls. The highest-value cleanup is not a broad rewrite
  by input mode. Prefer extracting small, behavior-preserving modules at clear
  boundaries: transient UI panels from `InputView`, number-mode operator/result
  state, physical selection state/actions, and eventually Chinese T9 preview
  helpers. Keep the service as the Android/Fcitx integration owner.
- Current cleanup scope: extract only transient panel UI and number-mode
  controller logic. Leave Chinese T9 and physical selection behavior in place.
  Preserve existing method names on `InputView` where the service calls them so
  the refactor stays mechanically verifiable.

## Physical OK Selection Mode Request

- Long-pressing the physical OK key should enter a text selection mode.
- While selection mode is active, physical Left/Right should extend the editor
  selection left or right, rather than only moving the cursor.
- Follow-up bug: using Shift+Left/Shift+Right lets the existing collapsed
  selection delete workaround fire when the user reverses direction back to the
  anchor, which can randomly delete one character. Track an explicit anchor and
  moving edge instead.
- Follow-up bug: long-press OK can leak repeated OK/space events if the
  long-press repeat is not fully consumed. Once OK is deferred for long-press
  detection, all repeat events and the matching key-up must be consumed.
- Show the same transient badge style used by mode switching when entering and
  leaving selection mode. Use explicit labels: `进入选区` on entry and `退出选区`
  on exit, including OK-confirmed exits and automatic exits before normal input.
- Follow-up request: physical Up/Down should also extend the selection to the
  editor's native vertical cursor destination. Unlike Left/Right, vertical
  movement depends on the target editor's layout, so the IME should delegate
  vertical selection extension to Shift+Up/Shift+Down instead of guessing text
  offsets locally.
- Android's copy/paste floating toolbar is owned by the target editor, not the
  input method. The IME can preserve the selected range and avoid deleting it,
  but there is no reliable public IME API to force every editor to show its
  native toolbar.
- Native selection-mode follow-up: trying Android's `startSelectingText`
  context menu action did not show the native selection handles/toolbars in the
  user's test path. Remove that best-effort request and keep this as an
  IME-owned physical selection mode.
- Since native selection toolbar is unreliable, show an IME-owned selection
  action hint panel after leaving physical selection mode with OK. Reuse the
  existing mode badge visual language, keep it visible until the next physical
  action, and place labels according to physical key direction:
  Up=copy, Left=cut, Right=paste, Down=delete, and center=OK/close.
  Back/Delete should cancel the selected range and close the panel without an
  extra action badge.
- Visual follow-up: the cut/paste hints still render horizontally or too close
  to the center OK on device. Force those side hints into a narrow stacked
  vertical shape, increase the horizontal spacing, and make the center OK circle
  larger.
- Implementation finding: `AutoScaleTextView` draws text with a single
  `Canvas.drawText()` call and does not support newline layout. The side action
  hints must use a real vertical container with one `TextView` per character.
- Visual follow-up: the action hints still feel crowded around the OK center.
  Spread copy/delete vertically and cut/paste horizontally farther from the
  center while keeping the same physical-key mapping.
- Follow-up finding: increasing `leftMargin`/`rightMargin` on constraints to
  the tiny center anchor did not move the side hints on device. Constrain the
  side hints directly to the OK circle and use start/end margins instead.
- Visual follow-up: once the action panel appears, do not also show the
  `退出选区` badge. The panel itself communicates that selection mode ended.
  Side hint spacing should be closer to the copy/delete spacing instead of
  feeling overly far away.
- Visual follow-up: remove outlines from this family of transient badge/hint
  overlays, and make the center Ok label fill its circle more.
- Text follow-up: display the center action label as `Ok`, not all-caps `OK`.
- Visual follow-up: make the center Ok circle larger again while keeping the
  label proportionally readable.
- Visual experiment: add a dashed cross and dashed circle behind the physical
  selection action buttons to visually connect the directional hints with the
  center Ok key. Keep it behind the buttons and low contrast so it reads as a
  guide, not another action.
- Visual follow-up: shrink the dashed guide so the circle/cross sits around the
  middle of each action hint instead of reading as a large background graphic.
- Visual follow-up: keep the center confirmation label at the current size, but
  render it with the selected font's regular weight instead of bold.
- Recommendation: keep touch-created Android selections as touch-driven
  interactions. Re-enabling broad physical-key deletion for touch selections
  would recreate the accidental blank-tap deletion bug. The new physical action
  panel should be scoped to ranges created by the IME's physical selection mode.
- Touch-selection bug report: after selecting text by touch, tapping blank
  editor space can delete the selected text. This points at
  `deleteCollapsedSelectionIfNeeded()` being too broad: it treats any selected
  range collapsing as an opportunity to delete. That workaround should only run
  immediately after a physical text-producing key that may need to replace a
  selection, not after unrelated touch-driven selection collapse.
- Follow-up bug: after exiting selection mode, arrows, OK, or Chinese-mode
  commits can delete the selected text because the collapsed-selection delete
  workaround still treats this IME-created selection as disposable. Keep a
  separate marker for physical-selection-created ranges and suppress that
  workaround until the selection collapses or is replaced.
- Do not steal OK from active Chinese T9 composition, pending punctuation,
  pending English multi-tap, or visible T9 candidate focus confirmation.
  Selection mode is for normal editor text navigation.
- A short OK press while physical selection mode is active should exit the mode
  and consume the matching key-up so it does not insert a space or confirm a
  candidate accidentally.

## Number Mode Operator Long-Press Request

- In T9 number mode, digit long-presses should commit common calculator
  operators instead of repeating the digit.
- Initial mapping, based on the hardware hint and calculator usefulness:
  `1=-`, `2=/`, `3==`, `4=+`, `5=.`, `6=*`, `7=(`, `8=%`, `9=)`, `0=_`;
  this can be tuned after device testing. `*` is already available as the
  literal star key in number mode, so it should not duplicate a digit mapping.
- Bug report: the operator cheat sheet flashes away after long-press `*`.
  Root cause is long-press repeat events: after the first repeat opens the
  panel, later repeats are routed into the panel and interpreted as `*` close
  commands. Operator panels should ignore repeat key-downs.
- Follow-up: the cheat sheet should show `*` on the `*` key instead of
  `返回`; `*` remains the literal star in number mode.
- Follow-up: committing `=` should first insert the literal equals sign, then
  offer `=result` as an optional next commit. Returning should only close the
  result choice because the `=` is already in the editor. Confirming should
  commit only the result text after the already-inserted `=`.
- Follow-up bug: long-press `3` sometimes opens the `=` result choice and then
  closes it immediately. The result-choice panel should also ignore long-press
  repeat key-downs from the key that opened it.
- Follow-up UI: in the result choice, use `确认` instead of `Ok`, and render
  `返回` as the lower small label instead of the upper small label.
- Follow-up UI: remove the visible `返回` hint from the equals result choice;
  users can rely on Back/Delete to dismiss it.
- Long-press `*` in number mode should show an IME-owned physical-key layout
  hint panel. While this panel is visible, a short digit press should commit the
  shown operator directly. Back/Delete/OK should leave the panel.
- When committing `=`, if a calculable expression exists before the cursor,
  show a small choice panel. OK commits the calculated result, while Back/Delete
  returns without calculating and commits literal `=`.

## T9 Candidate Layout Request

- Follow-up: set the default T9 top/bottom row height ratio to 82. Existing
  user-set values should stay untouched because the stored preference key is
  unchanged.
- The pinyin filter row and Hanzi candidate row currently sit close together
  inside the second bubble. Add a small vertical gap only while the pinyin row
  is visible, so the Hanzi-only state stays compact.
- The existing setting label describes "first/second row height" as a percent
  of candidate row height. That is technically accurate for the preedit and
  pinyin rows, but it is harder to reason about from the visible T9 UI.
- Keep the stored preference key compatible with existing users, but expose and
  name the setting as a T9 top/bottom row height ratio: the top compact rows are
  measured as a percentage of the lower Hanzi candidate row.

## T9 Return Pinyin Commit Request

- User correction: tap Return already commits the predicted pinyin correctly.
  The earlier report came from long-pressing Enter, so no Return-path refactor
  is needed for this step.

## IME Font Request

- The user wants the input method itself to be able to use a preferred font
  because their phone cannot change fonts globally.
- A full custom TTF/OTF import flow would need a file picker, persistent storage
  or copying into the app directory, validation, and fallback behavior. That is
  bigger than the current small testable step.
- Follow-up: system font switching did not visibly refresh in the IME, and raw
  system font lists contain many near-duplicates for language/region, TTC index,
  weight, and italic variants. Replace the system-font picker with a simpler
  custom-font folder picker.
- Default should be the system default font already used by the device. Users
  can place `.ttf`, `.otf`, or `.ttc` files in the app data `fonts` folder, and
  the IME font preference should list those files as custom fonts.
- The app should scan app data `fonts` as the supported custom-font folder and
  public storage `Fonts` as a best-effort convenience. Do not also create an
  app data `Fonts` folder because it duplicates the app-local path and confuses
  the setting labels.
- Bug report: switching from one custom font to another does not refresh all
  visible IME text until the user switches away to another input method and
  returns. Root cause: text views apply the font only when they are created, but
  `inputUiFont` changes did not recreate the input/candidate views. Font
  preference changes should use the same full input-view replacement path as
  theme changes.
- Bug report: the T9 pinyin preview/filter chips still appear to use the
  platform font after selecting a custom IME font. Most candidate/preedit text
  is already routed through `InputUiFont`, but `T9PinyinChipAdapter` creates raw
  `TextView` chips and needs to apply the shared font helper too.
- Add Simplified and Traditional Chinese translations for the new font settings
  so the visible settings UI is localized with the rest of the keyboard options.
- Apply the selected font to the visible IME text surfaces: virtual keyboard key
  labels, candidate/preedit rows, horizontal candidates, and transient mode
  badges.
- Android font fallback can still make some Hanzi or emoji glyphs come from the
  platform CJK/emoji fonts. A later embedded or imported CJK font can reuse the
  same shared font helper.

## Theme Color Request

- The mockups show a light gray outer/background surface, a pure white keyboard
  body, white candidate bubbles and popup surfaces, black primary text, and gray
  secondary/comment text.
- Variant one should be black-only for accents and keep the focused/active
  candidate black with white text.
- Variant two should use the same base colors but make the focused/active
  candidate and accent/return key pink with white text.
- Symbol, emoji, language, and backspace controls should use black icons/text in
  both variants.
- KawaiiBar toolbar icons above the keyboard should stay gray; they should not
  share the black keyboard-control icon tint.
- Pinyin/Hanzi candidate bubble backgrounds should follow the keyboard body
  color instead of the outer background color.
- The mode/Caps indicator badge should use the same corner radius as the space
  bar so the feedback shape matches the keyboard surface.
- The mode/Caps indicator should also use a space-bar-like height ratio and a
  shorter compact width, rather than the taller large badge shape.
- The combined pinyin/Hanzi candidate bubble should have a subtle low-opacity
  blurred shadow below it to separate it from the keyboard body.
- Follow-up visual tuning: make the mode/Caps indicator narrower, and make the
  pinyin/Hanzi bubble shadow slightly more visible with a larger blur range.
- Rename the new themes to `InkBlack` and `InkPink`. Their dark variants should
  be named `InkBlackDark` and `InkPinkDark`.
- Add two corresponding dark variants for the new Ink themes. Assumption:
  `InkBlackDark` should be monochrome black/white/gray with white active
  surfaces on dark keyboard body, while `InkPinkDark` should keep the same dark
  base and use pink for active/accent surfaces.
- Dark Ink space bar follow-up: both dark Ink themes should use a white space
  bar with black label text. Space label contrast should be derived from the
  actual `spaceBarColor`, not from the theme's normal key text color.
- The return key icon/background circle should be slightly smaller as a global
  keyboard setting, not a theme-specific override.
- User verification preference update: do not run compile checks for routine
  visual tuning unless debugging is needed or the user asks for a check.
- The black space bar from the mockups needs a readable white label even though
  the rest of the light theme uses black key text.
- Candidate row clarification: the screenshots reuse the Hanzi-focused state.
  The UI also needs the opposite focus state. When the Hanzi row is focused,
  pinyin candidates should be gray; when the pinyin row is focused, Hanzi
  candidates should be gray. The focused candidate itself stays white on the
  active background.
- The exact colors are inferred from screenshots, not sampled from a source
  palette file. Use conservative hex values close to the visible swatches:
  light gray `0xffd9d9d9`, medium gray `0xff9b9b9b`, black `0xff000000`,
  white `0xffffffff`, and pink `0xffff8f9f`.

## Pending Physical-Key Requests

- Release update: bump the app release label from `2.0.0` to `3.0.0`. The
  actual Gradle `versionName` comes from `gradle.properties` `buildVersionName`,
  while `Versions.baseVersionName` is the fallback/native project version.
  Increase `baseVersionCode` as well so ABI-derived APK version codes are
  installable over the 2.0.0 build.
- Release follow-up correction: the user wants this batch labeled as `2.0.0`,
  not `0.1.3`. The earlier edit touched the fallback base version but missed
  that Gradle normally derives `versionName` from `buildVersionName` or
  `git describe`; set the explicit build version override and fallback base
  version to `2.0.0`.
- Follow-up keyboard preference request: change the default regular virtual
  keyboard portrait height from 40% to 39%. This is the non-T9
  `keyboard_height_percent` default; existing user preferences remain unchanged.
- Bug report: when typing English while in the Chinese input environment,
  letters can become full-width until the user toggles the Full width/Half width
  status action manually. The Fcitx pinyin engine adds the `fullwidth` status
  action whenever pinyin is active; in this T9-focused app, full-width Latin
  characters should not be the active state by default.
- Clarification: the user should still be able to manually press the full-width
  status button and intentionally type full-width/spaced English. The app should
  only restore the default half-width state at input start, not continuously
  override later manual status changes.
- Bug report: after entering T9 digit `4`, the first Hanzi candidate can be
  `感` and the top pinyin preview correctly shows `g`. When deleting the final
  preview letter, the `g` disappears but `gan` briefly flashes. This suggests
  the top preview falls back to the highlighted candidate's full pinyin comment
  during the transient empty-key state. Deleting the last preview letter should
  clear the preview without showing a full candidate reading fallback.
- Root cause: `buildT9CandidatePreviewReading()` returned the full normalized
  candidate comment when `getT9CompositionKeyCount()` was zero. That made stale
  candidate comments eligible as a top-row fallback after the final digit was
  deleted.
- Bug report: typing a partial sequence like `dengdengws` can display the top
  pinyin preview as `dengdengwo` when a Hanzi candidate such as `等等我是` has
  the comment `deng deng wo shi`. The preview currently crops the highlighted
  candidate comment by input length without validating that the candidate
  comment letters match the actual T9 digits. `wo` maps to `96`, but the typed
  `ws` suffix maps to `97`.
- Correct matching behavior: the preview should not reject the selected Hanzi
  reading wholesale. It should keep using the selected Hanzi candidate's pinyin
  where candidate letters match the user's typed T9 keys, skip candidate letters
  that do not match, and continue matching later candidate letters. For
  `deng deng wo shi` against typed `dengdengws`, this preserves `w`, skips `o`,
  then matches `s`, yielding a preview equivalent to `dengdengws`.
- Feature request: while composing Chinese T9 pinyin, tapping the on-screen
  Return key should commit the predicted pinyin text itself, not the Hanzi
  candidate or an editor action. The committed text should remove display
  separators, so a preview like `deng deng wo` commits as `dengdengwo`.
- Bug report: in search fields, physical backspace can require two presses
  before text is deleted across Chinese, English, and numeric modes. The likely
  culprit is the empty-editor Delete guard: some search widgets may return empty
  one-character surrounding text even when the field has content, so the first
  backspace is consumed by the exit-IME path instead of reaching normal delete.
- Additional guard: if the tracked cursor position is already greater than
  zero, the editor cannot be empty from the user's perspective, so physical
  Delete must keep its normal deletion behavior regardless of surrounding-text
  quirks.
- Follow-up clarification: the keyboard is not being hidden on the first press,
  so the empty-editor guard is probably not the active failure path. The more
  likely path is that idle physical Backspace is sent through Fcitx as a key
  event, and some search fields do not delete until the next press. When there
  is no composing text or local T9 pending state, physical Backspace should
  delete directly through `InputConnection` instead of depending on the Fcitx
  BackSpace round trip.
- Follow-up bug report: physical Backspace still needs two presses in search
  fields, especially when browser/search suggestions refresh below the field.
  The direct-delete path still trusts the local cursor tracker and
  `getTextBeforeCursor(1)` before deleting. Search UIs can temporarily report
  those as empty while `ExtractedText` still contains the real text and cursor.
- Feature correction: numeric shortcut labels and long-press shortcut selection
  should apply to the Hanzi candidate row, not the pinyin filter row. The pinyin
  filter row should remain text-only.
- Visual follow-up: the numeric shortcut labels are still too wide on small
  phones because they sit beside each Hanzi/symbol candidate. To fit the
  10-candidate budget more comfortably, make shortcut labels smaller and move
  them below the candidate text instead of before or after it.
- Visual follow-up: the second-line shortcut labels can sit too close to
  English descenders or emoji glyph bounds. Move the shortcut labels slightly
  lower with extra line spacing so they do not overlap the candidate text.
- Visual follow-up: symbols with shortcut labels can look inconsistently left
  or right aligned even when Hanzi candidates look centered. Symbol glyph
  bounds and neutral punctuation directionality vary more than Hanzi glyphs, so
  shortcut candidates need a small stable cell width and explicit centered text
  alignment.
- Punctuation follow-up: T9 Chinese punctuation already includes Chinese curly
  double and single quotes, but the symbol picker still exposes straight ASCII
  quotes early in the general punctuation page, and the full-width Chinese page
  lacks curly double quotes. Prefer Chinese quote symbols in Chinese/full-width
  symbol contexts.
- Long-pressing physical digits during active Chinese T9 composition should
  select the corresponding visible Hanzi candidate and cancel the digit-display
  path that would otherwise insert the long-pressed number. The local
  symbol/punctuation candidate list should keep the same shortcut-label and
  long-press selection behavior.
- Cleaner implementation direction: delay physical Chinese T9 `2`-`9` digit
  input until key-up. The key-down event should be consumed locally; if Android
  reports a long-press repeat first, select the numbered Hanzi candidate and do
  not send a digit to Rime. If the key is released without a long-press flag,
  synthesize the normal digit down/up pair to Rime.
- Follow-up correction: Chinese T9 `1` long press should also select the first
  visible Hanzi candidate while active pinyin composition exists, while short
  press `1` should send the pinyin segmentation separator to Rime. For the
  local symbol/punctuation list, delay `1` cycling until key-up so long-press
  `1` can directly select the first visible symbol without first running the
  short press cycle.
- Bug report: short `1` still does not segment pinyin. Investigation found the
  local tracker treats `KEYCODE_1` as an apostrophe, but forwarding Android
  `KEYCODE_1` sends `FcitxKey_1` to Rime. Fcitx/Rime pinyin accepts
  `FcitxKey_apostrophe` as the segmentation key, so short `1` must send an
  apostrophe keysym while keeping the local tracker apostrophe update.
- Follow-up correction: simulated apostrophe key input is not reliably mutating
  Rime's current input buffer. The existing pinyin-filter path already uses
  `getRimeInput()` and `replaceRimeInput()` for precise Rime edits, so
  separator input should directly insert `'` at the end of the Rime input
  buffer instead of relying on a synthetic key event.
- Additional root cause: the short-`1` key-up branch checked the editor-side
  `CHINESE_COMPOSING` state before inserting the separator. Since physical
  `2`-`9` input is now sent on key-up through queued Fcitx jobs, the local T9
  tracker can already contain digits while the editor composing range has not
  updated yet. In that window, short `1` was consumed but did nothing. Use the
  local T9 key count as the activation condition for separator insertion.
- Follow-up bug report: after separator input, the pinyin filter row can no
  longer select the segment before the separator because the tracker reports the
  empty segment after the trailing apostrophe as current. Treat a trailing
  apostrophe as "selection still targets the preceding digit segment" until the
  next digit is typed or the user explicitly selects a pinyin.
- Follow-up bug report: separator handling only works halfway. For input like
  `58'23`, the pinyin option row appears after `58'`, but after typing the next
  segment it switches to options for `23`. Manual separator input should keep
  pinyin selection on the first unresolved segment before the separator until
  that segment is explicitly selected, then continue with the following segment.
- Follow-up bug report: segmentation now affects Rime, but the local pinyin
  input preview is not visible. Two local display paths can hide it: Rime may
  emit a transient empty preedit after apostrophe and clear the local tracker,
  and candidate-comment preview can override a local raw display such as
  `gan'`. Keep the local separator display through that transient empty event
  and prefer the local raw T9 preedit whenever the raw composition contains an
  apostrophe separator.
- Follow-up correction: the top pinyin preview should still prefer the focused
  Hanzi candidate's reading when that reading can be validated against the
  user's actual T9 keys. Manual separators should constrain the matching so a
  candidate reading cannot borrow letters across a user-entered apostrophe; if a
  separated segment does not match the candidate reading, fall back only for
  that segment to the local digit-based preview.
- Follow-up bug report: after restoring candidate-based separator-aware preview,
  the visible apostrophe separator itself disappeared from the top preview.
  Candidate-derived pinyin should still render the user's manual separator
  boundaries, including a trailing separator immediately after short `1`.
- Follow-up bug report: after selecting a pinyin filter, the separator
  disappears again, and reopening/returning from the filter also loses it. The
  model currently flattens manual raw input from forms like `58'23` into
  `5823` when a segment becomes resolved, so the separator is no longer
  available for display or replay.
- Additional root cause: when reopening a selected pinyin filter, engine restore
  replaces `pinyin'` with only the source digits. For a manually separated
  composition this should restore `digits'`, preserving the user-entered
  separator in Rime as well as in the Kotlin model.
- Safety requirement: if the T9 candidate bubble is fully hidden because the
  app believes there is no active local T9 composition, any hidden Rime T9 input
  left behind is stale and should be cleared. Hidden letters must not remain
  deletable after the visible T9 UI has disappeared.
- Concrete failing case: `52'5392` with pinyin filter `ka` and Hanzi candidate
  `卡了呀` should preview `ka'leya`, not `ka'lewa`. The post-separator raw
  digit segment `5392` maps to multiple candidate comment syllables (`le ya`),
  but the current preview matcher only pairs it with the next single comment
  syllable (`le`) and falls back for the remaining digits.
- Comprehensive direction: separator-aware preview and pinyin-candidate target
  selection should use the user's raw segmented source as the canonical state.
  Resolved pinyin choices are overlays on that source, not a replacement for
  the segmented model.
- Additional discovery: `rawPreedit` was also being overwritten by Rime display
  text after engine-backed pinyin selection, mixing source strings like
  `52'5392` with display strings like `ka'5392`. That makes later raw parsing
  unreliable. Keep `rawPreedit` as source digits/apostrophes only.
- Bug report: in Chinese mode with no input, long-pressing physical `1` opens
  punctuation and commits the first symbol instead of entering digit `1`.
  Cause: idle short-`1` punctuation is still executed on key-down, so the
  repeat event sees a pending punctuation list and treats long-press `1` as
  shortcut selection.
- Bug report: while typing pinyin in Chinese T9, deleting pinyin can make the
  Hanzi candidate focus briefly jump to a stale non-first item, such as the
  fifth candidate, before returning to the first candidate. This likely means a
  transient candidate refresh is rendering with a stale `cursorIndex` before the
  local T9 cursor reset applies.
- Visual root cause: `LabeledCandidateItemUi` animated the old active Hanzi
  highlight out over 190 ms when the same candidate became inactive. During
  pinyin deletion, the local T9 cursor can reset to the first candidate while
  the previous fifth highlight is still fading, making the stale focus appear to
  flash.
- Follow-up T9 deletion behavior: when deleting the final pinyin letter, the
  pinyin option row should disappear immediately without the reveal/collapse
  animation.
- Clarification: deleting the final pinyin letter should hide both the pinyin
  options and the Hanzi candidate bubble immediately. Confirming/committing the
  final Hanzi candidate should also make the candidate UI disappear directly,
  without waiting for a stale candidate page to render once more.
- Implementation note: `CandidatesView` suppresses stale T9 candidate pages when
  T9 has no active composition keys and no pending punctuation. This preserves
  no-pinyin `1` punctuation because pending punctuation bypasses the suppress
  branch.
- Follow-up T9 punctuation behavior: while active pinyin input exists, pressing
  `1` should be consumed as a no-op instead of replacing the candidate area with
  the punctuation list. This avoids confusing accidental `1` presses; the
  original pinyin state remains intact.
- Implementation note: the no-op applies to short press `1` while T9 composition
  key count is nonzero and no punctuation is already pending. Existing pending
  punctuation cycling still uses `1`.
- Follow-up animation tuning: pinyin and Hanzi candidate display animations feel
  laggy. Shorten the pinyin row reveal and Hanzi focus highlight timings, reduce
  movement/scale, and keep the interaction feeling snappy.
- Follow-up animation clarification: restore the previous Hanzi focus timing and
  scale, because that felt better. Keep the pinyin row fast, but add a
  left-to-right reveal for pinyin candidates such as the `pqrs` row.
- Bug report: after selecting a pinyin filter, the first Hanzi candidate can
  flash white strongly. This is likely because a newly bound active Hanzi
  candidate sets its active background to full alpha immediately instead of
  entering through the normal highlight animation.
- Follow-up animation experiment: remove non-focus animations from the candidate
  area. Keep only candidate focus/highlight animations; pinyin row show/hide
  should become an immediate state change.
- Follow-up clarification: remove focus/highlight animations too. Candidate
  focus should switch immediately for both Hanzi and pinyin rows.
- Follow-up bug report: on the first pinyin input, the pinyin filter row still
  appears to fill from left to right. There are no explicit row animations left,
  so the likely cause is the row wrapper becoming visible while its synced
  candidate-row width is still `0`, then relayouting wider.
- Follow-up bug report: after confirming a pinyin filter, the Hanzi row can show
  the previous unfiltered list for one frame before the filtered list arrives.
  This is caused by reusing the previous stable bulk page while the new filter
  request is pending; for filter-context changes the old page should be cleared
  instead of kept as a placeholder.
- Additional discovery: immediately after a pinyin chip is selected, the T9
  model enters `pendingSelection` while the Rime input replacement runs
  asynchronously. During that short window the resolved filter prefix can still
  be hidden from `getT9ResolvedPinyinFilterPrefixes()`, so the Hanzi row must be
  cleared based on the pending-selection state too.
- When the target editor/input field has no text, pressing the physical Delete
  key should exit the input method using the same logic as the existing
  on-screen exit-IME button, instead of acting as a normal backspace.
- First implementation step: handle only physical Delete on key down, after T9
  pending composition/punctuation handling and before forwarding to Fcitx or the
  target editor. The action should call `requestHideSelf(0)`, matching the
  on-screen exit control.
- Switching between Chinese, English, and numeric modes needs a more obvious
  animated confirmation than the current subtle feedback.
- The mode-switch animation should not reuse the existing English Caps/Shift
  visual behavior if that behavior risks accidentally committing text.
- Next implementation step: add an input-method-owned mode badge for
  Chinese/English/numeric switches. It should be rendered inside `InputView`
  instead of using `InputConnection.setComposingText()`.
- English Caps/Shift now adopts the new animation style and removes its
  original no-pending-character composing-text feedback.
- Current implementation step: migrate English Caps/Shift's no-pending-character
  feedback from editor composing text to the same input-method-owned badge. If a
  multi-tap character is pending, keep refreshing that pending composing
  character because it represents real text the user may commit.
- Follow-up feedback: the mode badge animation should be faster so physical-key
  mode/case changes feel more responsive.
- The space key should continue showing the current Chinese/English mode label.

## Build Feedback

The user ran `:app:assembleDebug`, and Kotlin compilation failed in
`CandidatesView.kt` because `selectT9ShownHanziCandidate()` referenced the local
`t9InputModeEnabled` variable from `update()` outside its scope. The fix is to
query the service state from that helper instead of using the out-of-scope local.

## Candidate Flicker Feedback

The user reports that the earlier part of the UI is stable, but the later
pinyin/Hanzi area briefly flickers before appearing. The most likely path is
`CandidatesView.requestT9BulkFilteredCandidatesIfNeeded()`: when the signature
changes, it clears `t9BulkFilteredPaged` and related state before the async
`getCandidates()` request returns. During that pending window, `updateUi()` can
render the current-page fallback or an empty filtered page, then render the bulk
result on the next refresh. This creates a visible blink in the T9 candidate
area.

After keeping the previous page during pending requests, the flicker is gone,
but the first input still shows the later pinyin/Hanzi part being filled after
the initial UI. This is still explained by the same later-added async bulk path:
early stable builds rendered only the current Fcitx/Rime page synchronously,
while the newer path starts a bulk `getCandidates()` request even when no pinyin
filter is selected. First input has no previous stable bulk page, so it still
has a visible second render.

Skipping no-filter bulk loading was the wrong tradeoff: it avoided one delayed
render path but broke the stronger product rule that the Hanzi row should honor
the user's T9 candidate budget. With the user's current test, both unfiltered and
filtered candidate rows still show only 5 entries, because the normal paged Rime
callback is still limited to Rime's page and Android no longer asks the bulk
candidate list for enough candidates before budget slicing.

The correct behavior is: always build the shown T9 Hanzi page from a candidate
pool large enough for the user's budget. Rime `menu/page_size` should still be
raised to 24 so the normal callback is less starved, but Android must not depend
on that alone. The Android T9 view should use the bulk candidate pool for both
no-filter and filtered T9 states, then apply the local character-budget paging.

## Candidate Focus Feedback

The user reports that the active Hanzi focus bubble can briefly appear on a later
candidate while deleting input or while selecting/unselecting a pinyin filter,
then jump back to the first candidate. This is likely caused by reusing the
previous stable bulk page while a new bulk request is pending: the visible
candidate list can remain identical for one frame, so the candidate-list
signature does not reset `t9HanziCursorIndex`, even though the T9 input/filter
context changed. The cursor reset key needs to include the T9 context
(`preedit`/resolved prefixes), not only the visible candidate list.

## T9 Punctuation Candidate Feedback

The local Chinese `1` punctuation flow fixed the external `1Password` inline
suggestion path by not sending a bare `1` through the normal editor/Rime route.
However, it also removed the candidate-window style punctuation choices the user
expected from the previous Chinese input behavior. The fix should keep `1`
local, but expose the active punctuation set as a local T9 candidate page so the
existing Hanzi candidate UI still appears.

The first local candidate-page attempt still auto-committed the first
punctuation because it reused the multi-tap timeout behavior, and key-up could
also see the punctuation composing text as Chinese composition. A local
candidate page should behave like a candidate window: stay pending until explicit
selection/confirmation or until another normal input commits it.

The follow-up test still showed auto-commit on repeated `1` and `*` because the
key-down path also computed Chinese composition from the local punctuation
preview before checking pending punctuation. Pending punctuation must be handled
before regular Chinese composition checks on both key-down and key-up.

The next desired behavior is closer to normal Hanzi selection: preview the
focused symbol in the input method's top preedit row, do not use the pinyin
filter row, and show symbols in the Hanzi candidate row. This means local
punctuation preview should not use `InputConnection.setComposingText()` at all,
because editor-side composition can be committed by system/Rime transitions.

DPAD navigation was still ineffective because the generic pending-punctuation
"commit before unrelated input" guard ran before candidate focus navigation and
did not exclude DPAD arrows/OK. Candidate navigation keys must be allowed through
to `handleT9CandidateFocusNavigation()` while punctuation is pending.

The user wants a larger punctuation pool split across multiple candidate pages,
with each page sized by the same T9 candidate budget used for Hanzi candidates.
The service should expose the full local punctuation pool, while `CandidatesView`
should paginate that pool locally with `T9CandidateBudget`.

At punctuation page boundaries, DPAD Up on the first page or Down on the last
page can fall through to the editor and move the text cursor. While local
punctuation is pending, candidate-control keys should be consumed even when they
cannot move focus or change pages.

## Reported T9 Issues

- Chinese `1` punctuation can surface a `1Password` suggestion.
- After pressing `1`, `*` should switch the pending punctuation to English
  symbols.
- Pinyin candidates are missing for readings such as `jiang`, `liang`, `kuan`,
  and `kuang`.
- The pinyin display should update after Hanzi candidate selection.

## Pinyin Coverage Audit

`T9PinyinUtils` is a hand-maintained map from T9 digit groups to pinyin strings.
The source explicitly labels 5-key and 6-key coverage as a subset. Comparing the
map against `plugin/rime/src/main/assets/usr/share/rime-data/luna_pinyin.dict.yaml`
shows:

- Rime dictionary syllables found: 424.
- T9 pinyin map syllables covered: 380.
- Missing dictionary syllables: 71.

Missing syllables found in the audit:

`biang`, `cei`, `chang`, `cheng`, `chong`, `chua`, `chuai`, `chuan`, `chuang`,
`chui`, `chun`, `chuo`, `cong`, `cuan`, `din`, `duan`, `eh`, `fong`, `guai`,
`guan`, `guang`, `huai`, `huan`, `huang`, `jiong`, `juan`, `kuai`, `lvan`,
`lve`, `nia`, `niang`, `nong`, `nuan`, `nun`, `nve`, `pia`, `qiang`, `qiong`,
`quan`, `rong`, `rua`, `ruan`, `sei`, `shang`, `shei`, `sheng`, `shua`,
`shuai`, `shuan`, `shuang`, `shui`, `shun`, `shuo`, `song`, `suan`, `tuan`,
`wong`, `xiang`, `xiong`, `yai`, `zhang`, `zhei`, `zheng`, `zhong`, `zhua`,
`zhuai`, `zhuan`, `zhuang`, `zhui`, `zhun`, `zhuo`.

The examples `jiang`, `liang`, `kuan`, and `kuang` were symptoms of this
broader incomplete-map design. A robust fix should complete the map against the
Rime dictionary syllable set instead of adding only user-discovered examples.

## Pinyin Preview Feedback

The user reports that the top pinyin preview still does not track Hanzi
candidate selection. For ambiguous digit sequences such as `2`, the default
digit-to-pinyin display can stay on the first option (`a`) even when the
highlighted Hanzi candidate has a different reading. Without an explicit pinyin
filter, the top preview should prefer the highlighted candidate's pinyin comment
so users can see which reading they are about to commit.

Follow-up feedback clarifies that the top pinyin preview also communicates how
many T9 digit keys have been entered. Candidate comments can contain the full
reading for prefix matches, for example `ai` or `ba` after only pressing `2`.
The preview should therefore follow the highlighted candidate's reading, but
truncate that reading to the current composition key count: `ai` becomes `a`,
and `ba` becomes `b` for a single entered key.

## README Understanding

The app is a fork/customization of Fcitx5 for Android focused on physical
nine-key phones. It supports Chinese T9 mode, English multi-tap mode, numeric
mode, long-press digit entry, a compact persistent screen keyboard, Rime-based
Chinese input, pinyin prediction/filtering, and `#` mode switching.

## Current Behavior and Discoveries

- Repo-level `analysis.md`, `design.md`, and `plan.md` already exist and should
  be updated rather than replaced.
- No repo-level `AGENTS.md` existed before this task.
- T9 behavior is concentrated mostly in
  `FcitxInputMethodService.kt`, `input/t9/*`, `CandidatesView.kt`, and
  `T9Keyboard.kt`.
- Chinese `1` currently passes through to Rime when not long-pressed. The local
  tracker also treats `1` as an apostrophe separator. That can make the key act
  like composition input instead of a local punctuation key.
- The app already has Android inline suggestion UI. If `1Password` appears as
  an autofill/inline suggestion, that content is external to the input method.
  Still, making Chinese `1` a local punctuation key avoids feeding `1` into the
  Chinese engine for ordinary punctuation entry, and clearing transient inline
  suggestions when local punctuation starts prevents that external suggestion
  from occupying the T9 punctuation moment.
- `T9PinyinUtils` lacks several common pinyin entries needed by the reported
  readings, including `jiang`, `liang`, `kuan`, and `kuang`.

## Deep-Dive: TT9 OK Key vs Fcitx Return Key for QQ/Discord

Full comparative analysis of the complete on-screen confirmation-key path
in both codebases. Target apps: QQ (`com.tencent.mobileqq`) and Discord
(`com.discord`).

### EditorInfo Observed

- QQ: `inputType=655361` (TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_WEB_EDIT_TEXT |
  TYPE_TEXT_FLAG_MULTI_LINE), `actionId=0` (IME_ACTION_DONE),
  `actionLabel=null`, `imeOptions=1073741830` (DONE | NO_ENTER_ACTION).
- Discord: `inputType=147457` (TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_WEB_EDIT_TEXT |
  TYPE_TEXT_FLAG_MULTI_LINE | TYPE_TEXT_FLAG_AUTO_CORRECT), `actionId=0` (IME_ACTION_DONE),
  `actionLabel=null`, `imeOptions=1342177286` (DONE | NO_ENTER_ACTION | NO_EXTRACT_UI).

### TT9 Complete OK Key Path

1. **SoftKeyOk.handleRelease()** → calls `tt9.onOK()`.
2. **HotkeyHandler.onOK()**:
   - `suggestionOps.cancelDelayedAccept()`, `stopWaitingForSpaceTrimKey()`.
   - No suggestions → `textField.getAction()` → returns `IME_ACTION_ENTER`
     (because `actionId == IME_ACTION_DONE`).
   - `action == IME_ACTION_ENTER` → enters `appHacks.onEnter()`.
3. **AppHacks.onEnter()**:
   - `isTermux()` → false for QQ/Discord.
   - `isMultilineTextInNonSystemApp()` → **TRUE** for both QQ and Discord.
     This ckecks `!packageName.contains("android") && isMultilineText()`.
     QQ inputType=655361 has TYPE_TEXT_FLAG_MULTI_LINE set.
   - Calls `textField.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)` → returns true.
4. **InputField.sendDownUpKeyEvents()**:
   - Creates `new KeyEvent(0, 0, ACTION_DOWN, KEYCODE_ENTER, 0, 0)` — **all-zero**
     downTime, eventTime, deviceId=VIRTUAL_KEYBOARD, scanCode=0, flags=0, source=0.
   - Creates matching ACTION_UP event.
   - Sends both via `InputConnection.sendKeyEvent()`.
   - Returns `downSent && upSent`.
5. `appHacks.onEnter()` returns true → `onOK()` returns true →
   `SoftKeyOk.handleRelease()` **does NOT** call the framework
   `InputMethodService.sendDownUpKeyEvents()`. The all-zero KeyEvent IS
   the primary mechanism.

### Fcitx Current Return Key Path

1. **handleReturnKey()** → `commitT9PreviewPinyinFromReturn()` returns false
   (no T9 preview text pending).
2. `inputType & TYPE_MASK_CLASS != TYPE_NULL` → enters `getTt9StyleEditorAction()`.
3. **getTt9StyleEditorAction()**:
   - `actionId == IME_ACTION_DONE` → returns `tt9StyleEnterAction`.
4. **sendTt9StyleDownUpKeyEvents()**:
   - Creates all-zero Enter down/up events matching TT9's
     `InputField.sendDownUpKeyEvents()`.
   - Sends both through `InputConnection.sendKeyEvent()`.
5. The removed `FLAG_EDITOR_ACTION` experiment is not part of the current path.

### Key Differences Found

1. **Routing via `isMultilineTextInNonSystemApp()`**: TT9 detects third-party
   multiline fields before app-specific logic and sends raw Enter. Fcitx now
   reaches the same raw Enter shape for `tt9StyleEnterAction`.

2. **`sendTt9StyleDownUpKeyEvents` is structurally identical to TT9's
   `InputField.sendDownUpKeyEvents()`**: both create `new KeyEvent(0, 0, ...)`
   with all-zero parameters. However, Fcitx's version has a fallback to
   `InputMethodService.sendDownUpKeyEvents()` that ONLY triggers when
   `sendKeyEvent()` returns false. For QQ, both down and up return true,
   so the framework fallback is NEVER reached.

3. **No composing text handling before sending**: TT9's `onOK()` handles
   suggestions (accepting current suggestion before Enter), but for the
   no-suggestions case, there is no explicit `finishComposingText()` before
   `sendDownUpKeyEvents`. Fcitx's `commitT9PreviewPinyinFromReturn()` only
   handles T9 pinyin preview, not general fcitx composing text.

4. **Two different `sendDownUpKeyEvents` in TT9**:
   - `InputField.sendDownUpKeyEvents()`: all-zero KeyEvent (used for cursor
     movement, multiline app Enter, undo/redo).
   - `InputMethodService.sendDownUpKeyEvents()`: framework method with proper
     timestamps, FLAG_SOFT_KEYBOARD | FLAG_KEEP_TOUCH_MODE (used as fallback
     after `onOK()` returns false for non-multiline fields).

### Open Question

Whether TT9's on-screen OK key actually sends messages in QQ. If it does,
the mechanism is `InputField.sendDownUpKeyEvents(KEYCODE_ENTER)` with an
all-zero KeyEvent — structurally identical to Fcitx's
`sendTt9StyleDownUpKeyEvents`. If Fcitx's equivalent produces
`downSent=true, upSent=true` but QQ does not send, the difference is
likely NOT in the KeyEvent construction but in:
- InputConnection state (composing text, batch edit, selection).
- Or TT9 also does not send in QQ with the on-screen OK key.

### User Verification (2026-04-29)

- **Discord**: TT9 on-screen OK key **does send** messages. Mechanism:
  `isMultilineTextInNonSystemApp()` → all-zero ENTER KeyEvent → Discord
  interprets Enter as send.
- **QQ**: TT9 on-screen OK key **does NOT send** messages. Same all-zero
  ENTER KeyEvent is accepted by QQ (`downSent=true, upSent=true`) but QQ
  inserts a newline instead of sending. This is QQ's app design choice:
  their chat input field is multiline, Enter=newline, and sending is
  triggered only through the in-app send button. This behavior is
  identical in both TT9 and Fcitx.

### QQ Verdict

QQ's `IME_ACTION_DONE | IME_FLAG_NO_ENTER_ACTION` EditorInfo combined
with multiline input means QQ specifically designed Enter to produce
newlines, not sends. Neither `IME_ACTION_SEND` via `performEditorAction`
nor raw ENTER KeyEvents (all-zero or FLAG_SOFT_KEYBOARD) can override
the app's own handling. QQ would need an in-app setting to change this
behavior. This is NOT an IME-side fix.

### FLAG_EDITOR_ACTION Verdict

Confirmed broken: keydown focus moves to another UI element in QQ/Discord.
This path should be removed entirely.

## Dialer Takeover Bug

Report: when dialing (phone dialer app), the input method takes over and
intercepts keys instead of passing them through to the dialer.

### TT9 Behavior

TT9's `InputType.isDumbPhoneDialer()` detects the dialer by package name:
```java
field.packageName.endsWith(".dialer") && !DeviceInfo.noKeyboard(context) && !isText()
```
This feeds into `isSpecialNumeric()` → `determineInputModes()` returns only
`MODE_PASSTHROUGH` → `TraditionalT9.onStart()` calls `onStop()` to hide the
keyboard, and `shouldBeOff()` returns true so all `onKeyDown()` return false,
passing keys directly to the app.

### Fcitx5 Behavior

Fcitx5 treats `TYPE_CLASS_PHONE` identically to `TYPE_CLASS_NUMBER` in
`KeyboardWindow.onStartInput()`: both select `NumberKeyboard`. There is no
package-name-based dialer detection. The input view is started and all keys
are intercepted by the IME engine.

### Fix Applied

1. Added `isPhoneDialer(info: EditorInfo)` in `InputDeviceManager`:
   `packageName.endsWith(".dialer") && inputType_mask != TYPE_CLASS_TEXT`.
2. `evaluateOnStartInputView()` sets `isDialerField = true`, skips
   `startedInputView = true`, and returns false (no virtual keyboard).
3. `evaluateOnKeyDown()` returns false immediately when `isDialerField` is
   true, preventing `forceShowSelf()` from re-showing the keyboard.
4. `onFinishInputView()` resets `isDialerField = false`.

Result: dialer fields are treated as passthrough — the IME does not show
the keyboard and all key events reach the dialer app directly.

### Follow-Up Finding

User testing showed the first pass was incomplete. Returning `false` from
`evaluateOnKeyDown()` only prevents `forceShowSelf()`, but
`FcitxInputMethodService.onKeyDown()` still continues into `forwardKeyEvent()`,
which consumes hardware digit keys and sends them to Fcitx instead of the
dialer. Also, dialer detection in `evaluateOnStartInputView()` happens after
`onStartInput()` has already scheduled `focus(true)` for non-null fields.

Device logcat confirmed the dialer package is `com.android.dialer`, with
`inputType=0`, `inputType=3`, and later text-like `inputType=177` fields.
The `.dialer` package-name detection matches the numeric dialer fields, but the
service must respect the passthrough state before focusing Fcitx or forwarding
keys.

Second-pass fix:

1. Move dialer detection into `notifyOnStartInput()` so `onStartInput()` can
   skip `focus(true)` for passthrough fields.
2. Expose `InputDeviceManager.isPassthroughInput` and check it at the very top
   of `onKeyDown()` and `onKeyUp()`, returning `super` before any T9 panels,
   remapping, or `forwardKeyEvent()`.
3. Hide input and candidate views while passthrough is active, even when the T9
   screen control bar would normally stay visible.
4. Treat passthrough as no visible IME area in `onComputeInsets()`.
5. Keep the dialer passthrough flag across `onFinishInputView()`, because
   hiding the IME view can happen while the dialer field remains focused.
   Reset it only on `onFinishInput()` or the next `onStartInput()`.

## Constraints

- Project files, code, comments, and docs must stay in English.
- Chat change summaries must be in Chinese.
- Changes should be surgical and traceable to the reported issues.
- Do not run full Gradle builds or comprehensive tests; use basic static/syntax
  checks only.
- Functional Android Studio/device testing is left to the user.
- Implement only the empty-editor physical Delete step and the T9 mode-switch
  badge for now; leave Caps/Shift feedback migration for a later small,
  testable step.

## Edge Cases

- Chinese punctuation should not discard active composition unexpectedly.
- A pending punctuation character should be committed before unrelated input,
  mode switching, or return/space actions.
- `*` should keep its existing literal behavior in Chinese mode unless there is
  a pending `1` punctuation choice to switch.
- Pinyin filtering must not hide all useful Hanzi just because a longer pinyin
  map entry is missing.
- Pinyin preview should follow the highlighted Hanzi candidate reading when no
  explicit pinyin filter has been selected.
- Candidate-based pinyin preview should be truncated to the number of current
  T9 digit keys so the preview remains a key-count indicator.
- T9 candidate refresh should avoid swapping to an empty intermediate page while
  async bulk filtering is pending.
- T9 Hanzi candidates should honor the user-configured character budget even
  when Rime's current page contains fewer candidates.
- T9 Hanzi focus should reset to the first candidate whenever the T9
  input/filter context changes, even if a pending request is still displaying
  the previous stable candidate page.
- Chinese `1` punctuation should show a local candidate page for the current
  punctuation set instead of hiding the candidate window.
- Local punctuation preview should live in the input method preedit row, not in
  editor composing text.
- DPAD arrows/OK should navigate or commit the local punctuation candidate page
  instead of triggering the generic pending-punctuation commit guard.
- Local punctuation candidates should support multiple pages using the
  user-configured T9 candidate budget.
- Local punctuation candidate navigation should consume DPAD keys at page/focus
  boundaries so the editor cursor does not move.
- Clearing transient input state should hide inline suggestions without disabling
  Android inline suggestions for normal autofill fields.
- Adding all 71 missing pinyin strings is small in APK/code size, and the user
  confirmed continuing with the complete pinyin-map coverage step.
- After completing the map, the static audit reports 424 Rime dictionary
  syllables, 451 local T9 map strings, and 0 missing Rime syllables. The local
  count is higher because the map also contains single-letter prefix candidates
  and compatibility spellings such as `lue`/`nue`.
- Delete-on-empty must not interfere with normal deletion when the editor still
  contains text, active composition, pending punctuation, or selectable
  candidates. Only the truly empty-editor case should invoke the same exit-IME
  behavior as the existing on-screen exit button.
- The empty-editor check can be narrow: no selected text, no composing range, no
  local T9 composition state, no pending English multi-tap character, no pending
  T9 punctuation, no text before the cursor, and no text after the cursor.
- Mode-switch feedback should be visible enough for physical-key use, but
  should not create a text-commit path or consume input in a way that changes
  composition unexpectedly.
- The first mode-switch feedback step should only affect T9 mode switching.
  English Caps/Shift can keep its current behavior until the mode badge is
  tested successfully.
- Caps/Shift migration should remove the old delayed composing-text indicator
  path so a stale dismiss runnable cannot clear unrelated composing text.

## Previous Completed Work

- Removed the abandoned GitHub remote APK build workflow.
- Changed the T9 Hanzi candidate budget default from 12 to 10.
- Kept the Rime plugin on inherited shared versioning with no local override.

## Status Action Menu Follow-up

- The old README referred to the Rime status entry as the `< >` icon. That icon
  is only the Android fallback for unknown Fcitx action icons; after the status
  panel switched to text-only vertical labels, the user-facing anchor must be a
  stable label instead of a fallback icon.
- `StatusAreaEntry.fromAction()` currently uses `Action.shortText` directly.
  Rime's main action usually returns the active schema name, but a not-yet-ready
  or otherwise empty action can render as a blank cell in the text-only status
  panel.
- Status actions with submenus still use Android's platform `PopupMenu`. That
  menu does not share the IME theme colors or input UI font, so the Rime deploy
  and sync page feels inconsistent with the in-IME settings/status UI.
- Success criteria: Fcitx actions have a readable fallback label, Rime-like
  actions fall back to a localized Rime/Zhongzhouyun label, status submenus are
  displayed inside the IME with the current theme and font, and the README
  installation steps describe the current text UI instead of the removed `< >`
  icon.

## Password Auto Mode T9 Exit Bug

- User report: focusing a password field opens password mode, then tapping the
  in-layout `T9` exit key enters a strange state.
- Likely risk area: automatic password mode is driven by password capability
  flags, while `T9` is a user intent to leave password mode for the current
  focused password field. If the exit key clears the temporary layout but does
  not record a manual-off suppression, the same password-capability input
  session can immediately re-enter or keep reasserting `keyboard-us`.
- Success criteria: pressing `T9` from automatically entered password mode
  disables password mode for the current password session, restores the normal
  T9 layout/input method, and does not auto-reopen password mode until a fresh
  non-restarting input session clears that suppression.
- Device screenshot finding: before the fix, after password mode was exited and
  focus moved to the normal field, the password digit row remained in the
  KawaiiBar area while the normal T9/status controls were underneath it. This
  was the visible "strange state": stale KawaiiBar password-number-row state,
  not a missing keyboard layout attachment.
- Verification screenshot after installing the fix: pressing `T9` in the
  password field leaves only the normal toolbar/status rows, and moving focus to
  the normal field no longer leaves the digit row behind.

## Chinese T9 Candidate Position Flicker Follow-up

- User report: in Chinese T9 pinyin input, the first keypress briefly shows the
  pinyin preview and one pinyin-filter chip lower on screen, then the full
  pinyin/candidate UI jumps upward. Deleting the last pinyin still makes the
  Chinese candidate part flash lower before disappearing.
- Code audit finding: `CandidatesView.updatePosition()` chooses below or above
  the cursor based on the currently measured candidate bubble height. The first
  local T9 render is short because it may contain only the top reading and
  pinyin row before Rime/bulk Hanzi candidates arrive, so it can fit below the
  cursor. When candidates arrive, the bubble height grows and the same logic
  moves it above the cursor. Deletion can pass through the same intermediate
  height/content states before the view is hidden.
- Success criteria: while Chinese T9 composition or local punctuation candidate
  UI is active, the floating candidate bubble should prefer the above-cursor
  position from its first visible frame, so content changes do not move the
  bubble between below-cursor and above-cursor positions.

## Chinese T9 Candidate Staging Flicker Follow-up

- User retest: the candidate bubble position is now stable, but the first
  Chinese T9 keypress still shows an incomplete UI first, then the real Hanzi
  candidates arrive later, making the input feel laggy.
- Code audit finding: physical Chinese T9 key-down updates the local
  `T9CompositionTracker` and immediately calls `CandidatesView.refreshT9Ui()`
  before Fcitx/Rime has returned the matching paged candidates. That local
  refresh can render only the pinyin preview and pinyin-filter row, or can reuse
  the previous paged candidate data for the new local composition. A later
  `PagedCandidateEvent` replaces it with the real candidate row.
- Success criteria: during Chinese T9 composition, local key-down changes should
  not display a half-populated candidate bubble. The UI should either stay
  hidden or wait for the matching engine candidate page, then show the complete
  top reading, pinyin row, and Hanzi row together.
- User retest after the first staging fix: suppressing every local refresh while
  waiting for engine candidates makes continuous typing feel worse, because the
  already-visible candidate bubble disappears between pinyin updates and then
  reappears with the next page.
- Refined success criteria: suppress the incomplete frame only when there is no
  complete candidate bubble visible yet. Once a complete Chinese T9 candidate
  bubble is already visible, keep that stable frame on screen while waiting for
  the next engine candidate page, then replace it directly with the updated
  candidates.
- User retest: first input no longer shows the lower/incomplete candidate
  bubble, but the pinyin filter chips themselves appear one by one on the first
  visible frame. Later pinyin updates appear together.
- Code audit finding: `T9PinyinChipAdapter.submitList()` updates the
  RecyclerView data at once, but `setPinyinRowVisible()` can make the
  RecyclerView visible before its first child layout pass for the new adapter
  contents. On a small device this exposes child attachment as a progressive
  chip reveal.
- Success criteria: the first visible pinyin filter row should stay invisible
  through its first RecyclerView layout/pre-draw pass, then show all currently
  laid-out chips together.
- User retest: the first pinyin chip still appears immediately, then the next
  two chips appear together. This suggests the row is still being revealed in
  the same pre-draw cycle where RecyclerView has not completed a stable visible
  child set.
- Follow-up success criteria: after the first pre-draw, keep the pinyin row
  invisible for one more frame and reveal it from a posted animation callback,
  so the first visible draw sees the full row rather than the first attached
  child.
- User retest: the first chip still appears immediately. The likely missed
  case is that the pinyin adapter list changes while the whole candidate view
  is still hidden waiting for engine candidates. When the engine page arrives,
  the list is unchanged, so the reveal path thinks no delayed first layout is
  needed and shows the RecyclerView immediately.
- Follow-up success criteria: remember adapter changes that happen while the
  pinyin row is hidden, and force the next visible reveal through the delayed
  RecyclerView layout path even if the adapter list is unchanged by then.
- Code audit follow-up: `bubble2Wrapper` has an `OnGlobalLayoutListener` that
  directly calls `showPinyinRowNow()` when the pinyin row is target-visible and
  invisible. After the delayed pre-draw listener removes itself but before the
  posted animation-frame reveal runs, that global-layout callback can still
  reveal the row early and bypass the delayed path.
- Success criteria: global-layout width synchronization must not reveal the
  pinyin row while a delayed first reveal is pending.
- Rejected hypothesis: the flicker was not caused by the first-row
  `topReading`. Suppressing matching `topReading` disturbed the first-row
  display and could expose raw digit fallback text. The user's issue is
  specifically in the second-row pinyin chips.
- Deeper root-cause finding: using `RecyclerView` for the pinyin chip row makes
  first display depend on child attach/bind timing. The row contains only a
  small number of chips, so virtualization is unnecessary and creates visible
  staged rendering on the target device.
- Success criteria: render the pinyin chips with a simple horizontal view group
  that rebuilds all chip views synchronously before the row becomes visible.
- User retest after replacing RecyclerView: pinyin chips now appear together,
  but they still appear later than `topReading` and the Hanzi row. This is the
  old RecyclerView reveal delay still being applied after the row no longer
  uses RecyclerView.
- Success criteria: remove the pinyin-row pre-draw/animation-frame delay so the
  synchronous chip row is made visible in the same `updateUi()` pass as the
  first row and Hanzi row.

## Chinese T9 Punctuation Spellcheck Underline Follow-up

- User report: while Chinese input is active, after entering punctuation the
  whole sentence in the target text field can become red-underlined.
- Red underlines are usually drawn by the target editor or Android spell
  checker, but the IME can trigger them by leaving composing state inconsistent
  or by committing local text outside the same cleanup path used for ordinary
  commits.
- Code audit finding: the Chinese T9 local punctuation path and several
  punctuation-following literal commits call `currentInputConnection.commitText`
  directly. Those calls bypass the service-level `commitText()` wrapper, which
  clears IME composing state, predicts selection, and handles the existing
  composing range before committing.
- Success criteria: local Chinese T9 punctuation, punctuation-following spaces,
  and literal digits inserted from the Chinese T9 special-key paths should use
  the same service commit helper as ordinary committed text. If the red
  underline is system spellcheck, this will not disable spellcheck globally,
  but it should stop the IME from contributing stale composing state around the
  punctuation commit.
