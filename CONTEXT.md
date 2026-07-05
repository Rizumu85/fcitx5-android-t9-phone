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
