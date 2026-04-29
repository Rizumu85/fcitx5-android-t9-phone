# Analysis

## Current Task

Revert the whitelist/send-fallback experiment and apply the TT9-style
confirmation-key behavior.

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
