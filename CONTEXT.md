# Context

## Domain Terms

### Physical T9 Key Flow

The complete hardware-key decision flow for T9 input. It owns key-down/key-up
pairing, long-press gating, mode-specific key behavior, candidate navigation,
candidate confirmation, punctuation follow-up actions, return actions, and
short-press versus long-press outcomes for Chinese T9, simple English,
Smart English, and number mode.

`FcitxInputMethodService` should act as the platform adapter for this flow: it
executes Android/Fcitx side effects such as committing text, forwarding key
events, refreshing candidate UI, showing punctuation candidates, and handling
return keys, but it should not duplicate the user-facing key-flow rules.

The flow should return command lists rather than directly executing adapter
methods. This keeps multi-step key behavior, such as Smart English `1` and `#`
follow-up actions, testable as ordered outcomes before Android/Fcitx side
effects run.

Commands should stay at the domain level. For example, the flow may request
`CommitSmartEnglishCandidate` with spacing/prediction policy and then
`ShowEnglishPunctuationCandidates`, but it should not compute the committed
word text or reset Smart English internals itself. The session modules keep
their own locality; the key flow coordinates user-facing actions.

Each key event should be evaluated against an immutable physical-key state
snapshot supplied by the platform adapter. The flow should not keep querying
live adapter getters while it is deciding commands, because command execution
can change IME state before the key-flow decision is complete.

The flow session owns key-pairing state that exists between key-down and key-up
events, including digit long-press flags, pound long-press state, and deferred
Smart English digits. These are user-facing key-flow rules rather than Android
platform adapter details.

The refactor may be delivered in small slices, but it must not leave parallel
legacy fallback behavior behind. Each migrated key-flow branch should remove
the old branch it replaces, and the end state should have the command-based
flow as the single implementation.

The first migration slice should target Smart English physical-key behavior:
`1`, `#`, `0`, OK/select, directional candidate navigation, Backspace, and
long-press digit shortcuts. After each code slice, provide a concrete manual
test checklist and wait for user confirmation before migrating the next slice.

### T9 Candidate UI Snapshot Pipeline

The complete T9 candidate UI refresh flow. It should turn the current IME
state, candidate data, composition preview, pinyin filter row, focus state,
layout preference, and visibility rule into a stable snapshot that can be
compared before Android views are touched.

`CandidatesView` should act as the Android view adapter for this pipeline: it
may measure, render, scroll, and set view visibility, but it should not own the
rules for which T9 surface is shown, which candidate page is active, whether a
top reading row is reserved, or whether the UI should remain visible.

The snapshot pipeline should preserve the current candidate bubble visual
design while improving refresh locality. The goal is not a new visual style;
the goal is fewer broad refreshes, fewer transient wrong rows, and fewer layout
visibility changes during normal typing.

The pipeline snapshot should be render-ready. It owns the final bottom
candidate page, original candidate indices, shortcut-label visibility, top
reading content, pinyin filter row, candidate focus, visibility decision, and
anchor preference. `CandidatesView` should not rebuild or reinterpret those
domain decisions after the snapshot is produced; it should only diff and render
the snapshot into Android views.

Candidate paging, page caches, and UI focus state belong to the snapshot
pipeline. `CandidatesView` may forward user intents such as page up, page down,
or select shown index, but it should not own the pager/cache rules for Smart
English, pending punctuation, local Chinese budget pages, bulk Chinese
selection, or pinyin row focus.

The migration should be sliced by candidate source, but each completed slice
must remove the replaced `CandidatesView` fallback. Start with Smart English
and pending punctuation, then move Chinese local-budget and pinyin-row state,
then bulk Chinese selection. This keeps each user-facing UI surface testable
without leaving parallel render rules behind.

The first implementation slice should fully move Smart English and pending
punctuation page/cache/selection/page-offset state into the snapshot pipeline.
`CandidatesView` should stop owning `T9SmartEnglishPageCache`, pending
punctuation pager decisions, Smart English shown flags, pending punctuation
shown flags, and original-index mapping for those two sources once the slice is
complete.
