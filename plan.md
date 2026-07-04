# Plan

## Working Agreement

Rizum Guidelines are active for this project/thread until the user says
otherwise.

## Current Input-Method Follow-up

- [x] Diagnose hardware-key passthrough in game/emulator `TYPE_NULL` contexts.
- [x] Pass through hardware keys when no real text input session is active.
- [x] Add smart English T9 as an optional status-panel toggle.
- [x] Add local smart-English dictionary lookup and learned-word storage.
- [x] Keep simple English multi-tap available for unknown words and learning.
- [x] Refresh cached Fcitx input-method/status state after config reload.
- [x] Replace raw smart-English digit display with word/letter-group feedback.
- [x] Gate physical T9 long-press actions by held duration instead of first
  repeat event.
- [x] Run Kotlin compile and debug APK build checks.
- [x] Install and select the debug IME on the connected phone.
- [x] Re-check the physical OK/confirm key repeat path.
- [x] Replace smart-English letter-group fallback with prefix word candidates
  and a compact no-match state.
- [x] Run Kotlin compile and debug APK build checks for the follow-up.
- [x] Install and select the updated debug IME on the connected phone.
- [x] Inspect TT9 English dictionary source and license notes.
- [x] Generate an adapted English T9 dictionary asset for this app.
- [x] Replace the tiny Kotlin word list with asset-backed lookup.
- [x] Run Kotlin compile and debug APK build checks for the dictionary asset.
- [x] Install and select the updated debug IME with the larger dictionary.
- [x] Add smart-English numeric shortcut labels and local candidate paging.
- [x] Route smart-English long-press `1..9,0` to visible candidate selection.
- [x] Run Kotlin compile and debug APK build checks for smart-English paging.
- [x] Install and select the updated debug IME with smart-English paging.
- [x] Consume smart-English candidate navigation at page edges and route
  English punctuation candidates through the same navigation/commit path.
- [x] Track input sessions from `onStartInput()` so early physical
  Back/Backspace cannot leak as system/app back in normal text editors.
- [x] Audit physical key rules across Chinese, smart/simple English, and number
  modes; normalize number commits, pending punctuation `*`, and forward-delete
  cancellation.
- [x] Add a tested `PhysicalT9KeyPolicy` module for shared physical-key
  classification and route service/number-mode callers through it.
- [x] Move physical T9 decision/execution routing into tested
  `PhysicalT9KeyHandler` and leave `FcitxInputMethodService` as the host
  adapter instead of a duplicate key-state machine.
- [x] Document the exact Smart English learning flow in README, release notes,
  and Baidu release readmes.
- [x] Add a top-level Dictionary Management entry for the Smart English learned
  word list.
- [x] Allow adding, editing, and deleting Smart English learned words from
  settings.
- [x] Disable Smart English learning in password/no-personalized-learning
  editor contexts.

## User Retest Checklist

- [ ] Confirm Chinese T9 pinyin candidates keep their row width after paging to
  a Hanzi page that has only one visible candidate.
- [ ] Confirm game/emulator physical-key mappings pass through without IME
  input.
- [ ] Confirm smart English T9 shows words or compact no-match state, not raw
  digits or long letter-group strings.
- [ ] Confirm `435`, `4355`, and `43556` keep showing useful candidates such
  as `hello`/`help`.
- [ ] Try less common English words to confirm the larger adapted dictionary is
  active.
- [ ] Confirm smart English respects the T9 candidate budget setting, supports
  page up/down, and commits visible candidates by long-pressing `1..9,0`.
- [ ] Confirm smart-English first-page up does not move the editor cursor, and
  `1` punctuation candidates can move, commit, and page normally.
- [ ] Confirm hot/laggy physical-key typing no longer leaks digits or triggers
  long-press choices during short presses.
- [ ] Confirm immediately pressing physical Backspace after enabling typing
  deletes text instead of returning/backing out.
- [ ] Confirm number mode short digits still input digits, long digits still
  input operators, and `*` switches punctuation sets only while punctuation
  candidates are pending.
- [ ] Confirm DPAD center, Enter, and numpad Enter act consistently for
  candidate/confirmation contexts.
- [ ] Watch for the intermittent Rime/default-only recovery issue after
  redeploy or reload.
