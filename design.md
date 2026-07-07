# Design

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
