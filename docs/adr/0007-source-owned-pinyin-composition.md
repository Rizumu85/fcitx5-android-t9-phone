# ADR-0007: Source-Owned Pinyin Composition

- Status: Accepted and implemented
- Date: 2026-09-22
- Audited revision: `6dd0b3f8`

## Context

Reading selection can leave the preview and Hanzi candidates describing
different input. This is not just a rendering problem. The Pinyin session keeps
raw input in both `T9CompositionTracker` and `T9CompositionModel`, but different
operations disagree about whether the tracker contains all input or only its
unresolved suffix. The Rime bridge independently infers replacement positions
from input length and text. Candidate comments then provide another reading
interpretation.

The existing decomposition has useful resolvers, a serialized engine lane, and
a shared candidate renderer. More wrapper classes, frame delays, or geometry
changes would not resolve these ownership conflicts.

## Evidence

On the connected physical phone, both debug 4.6.5 and release 4.6.6 reproduced
this sequence in the isolated benchmark editor:

1. Type `64'426'62`, using short `1` for each apostrophe.
2. Select `ni` in the reading row, then select `hao`.
3. The preview becomes `ni hao` and the remaining reading row disappears,
   while the Hanzi page still contains `ni hao ma` candidates.

The debug build also repeatedly reproduced an undo-and-append failure: type
`64426`, select `ni`, select `hao`, press Delete to reopen `hao`, and append
`3`. The previous preview and candidates remain instead of reflecting the
edited input. A recording captured this sequence with 200 ms between physical
keyboard events. Release reproduction of the separator case rules out an
outdated debug installation as its cause.

Eight temporary JVM diagnostics against the audited source failed their
expected invariants. These are targeted diagnostic failures, not a claim that
the full existing test suite fails. The queue diagnostic reproduces the service
guards with a controllable queue; the delayed-ack diagnostic uses a mutable
`RimeIo` test implementation, not the native engine.

| Operation | Expected | Observed | Implementation responsible |
| --- | --- | --- | --- |
| Select `ni`, then `hao` from `64'426'62` | Preserve all seven digits and the remaining `62` | Raw input becomes `64426` | `ChineseT9CompositionSession.selectPinyin` selects the first unresolved segment but edits the last tracker segment after the first choice |
| Select `ni`, `hao` from `64426`; reopen `hao`; append `3` | `644263` | `64644263` | `popLastResolvedSegment` restores full raw input into the tracker; `updateFromTracker` prepends resolved digits again |
| Choose `n` from the first segment of `64'426` | Engine projection `n'4'426` | Engine projection `n''426` | `ChineseT9RimeBridge.mirrorPinyinSelection` adds separator length even when the separator is not adjacent to the selected source span |
| Choose `ge`; test a candidate with reading `he` | Reject the conflicting reading | Accepted because both map to `43` | `ChineseT9PresentationSource.commentSegmentMatchesResolvedSegment` treats equal numeric codes as equal selected spellings |
| Choose `ge`; build a snapshot with the previous `he` comment | Keep the explicit `ge` choice | Preview becomes `he` | The non-separator candidate-preview path ignores resolved segments |
| Choose `ni`; append a digit before its queued mirror executes | Apply both operations in order | Mirror rejected; local selection remains | `FcitxInputMethodService.selectT9Pinyin` invalidates a semantic command using the latest composition revision |
| Pause a mirror, clear and retype the same selection, then finish the old mirror | Ignore the old acknowledgement | The new selection is marked engine-backed | The bridge mutates the live session and `markSelectionEngineBacked` matches text instead of operation identity |
| Select `ni`, `hao`; partially commit the `ni` candidate | Preserve the remaining `hao` choice | Remaining raw `426` has no selected reading | `consumeSelectedCandidateReading` rebuilds a raw-only model |

The audited display path also obtained a ticket from the live local snapshot
when an engine event arrived. That was not evidence of which operation produced
the event. Numeric
freshness checks cannot distinguish different choices with the same T9 code.
This is a source-identity risk identified in code, not an independently captured
native event-order reproduction.

## Decision

Deepen the existing `ChineseT9CompositionCoordinator` Module. Its Interface
should accept input intents and expose immutable presentation state; callers
must not coordinate tracker repair, reading-prefix fallback, and bridge
acknowledgements themselves.

### One Authoritative Input

Store raw keys and explicit separators once. Selected readings annotate source
spans and impose an exact/initial constraint. Immutable engine projections carry
the document epoch and revision, so the bridge needs no mutable selection
acknowledgement or text-based lookup.
Choosing or reopening a reading does not insert or duplicate raw keys. A partial
commit removes only its consumed source prefix, shifts remaining spans, and
preserves unconsumed choices and separators.

Derive the engine projection and local reading state from this document.
Replacement commands carry their source identity and full projection, not
positions guessed from tail length or `lastIndexOf`. Use the existing native
Rime replacement capability; the serialized replacement reads the current range
and publishes its success result. Any required resynchronization restores the full authoritative
projection, including reading choices, rather than silently weakening filters
or replaying digits without their choices.

### Lossless Commands, Versioned Results

Own local state on the main dispatcher. Engine work consumes immutable commands
and returns success without changing the document; it never mutates the live composition
session after a suspension. Reading getters and snapshot builders remain pure.

Separate the editor/session epoch from the input revision and command identity.
Typing another digit in the same session must not discard an earlier reading
selection. Clear, editor change, or scheme change invalidates obsolete session
work. Results carry the producing command identity, so an old `ni` cannot
acknowledge a new `ni` merely because their text matches.

Keep the existing ordered engine lane. Presentation may skip superseded frames;
accepted semantic input must remain lossless and ordered. Do not solve the race
by removing all stale-result checks or introducing a debounce delay.

### One Accepted Presentation Frame

Carry engine-origin identity through dispatch and presentation publication.
The preview, reading choices, Hanzi page, source indices, and focus must belong
to one accepted source frame. A ticket inferred from current local state when
an event arrives is insufficient; retain the producing operation's identity.

Preserve candidate-comment preview as intentional product behavior. Comments
may explain unresolved input, but cannot overwrite an explicitly selected
reading. Exact readings compare normalized spelling; initial selections match
the intended letters, not any spelling with the same number sequence. Native
comment normalization and source alignment belong behind the composition
Interface rather than in the renderer.

## Implementation And Regression Contract

1. Replace the inconsistent Pinyin source bookkeeping inside the existing
   session. Cover consecutive choices, separators, initial-only choices,
   reopening, and partial commits through the same coordinator Interface used
   by input. Delete the replaced full-input/suffix reconstruction paths.
2. Move selection acknowledgements to the state owner and give semantic commands
   stable identities. Test append, undo, clear/retype, and mode changes while
   engine work is deliberately suspended. Remove the old text-matching bridge
   mutations and per-revision semantic-command cancellation.
3. Connect engine-origin frame identity and preview composition. Verify equal-code
   alternatives such as `ge/he` and `mi/ni`, partial commits, paging, repeated
   syllables, and delayed/reordered callbacks. Remove comment-based recovery
   paths that contradict explicit choices.

The corresponding diagnostics are permanent regression tests. Use a mutable fake
engine with controllable suspension; an always-successful replacement stub
cannot detect lost ranges or stale acknowledgements. Include invariants that
selection/reopening conserve raw input and that a prefix commit preserves the
unconsumed suffix. Exercise the shared engine lane with Stroke and Zhuyin, but
do not migrate their composition models without evidence of a related defect.

Keep the accepted bubble geometry, focus styling, reading-row folding, physical
key contract, and Rime dictionaries unchanged. Measure dispatch, queue wait,
source callback, and first complete frame at paced input rates before claiming
a performance improvement. Removing avoidable replay and re-query work is a
design benefit to verify, not a measured result of this audit.

The replacement deletes `T9CompositionTracker`, pending-selection flags,
engine-backed acknowledgements, suffix-length replacement guesses, and
per-revision cancellation of accepted reading commands. Pinyin no longer has a
digit-only replay fallback. `FcitxPresentationSequencer` stamps the existing
native InputPanel/Paged callback pair and caches complete frames;
`ChineseT9SourceRegistry` validates those origins against the current ticket.
Numeric freshness remains a completeness check for shared scheme replay, not
proof of source identity. Custom phrase candidates obey the same explicit
spelling constraints as engine candidates.

The focused JVM selection (42 suites, 262 tests) passes. Physical debug checks
cover the original separator and reopen/append reproductions, `ge/he` reselection,
continuous initials with a partial commit, a long sentence selected after `zui`,
and committing followed immediately by a new composition at 70 ms key spacing.
Shared-path smoke checks cover Stroke `1234` selection and Zhuyin `20`, reading
filtering, and center-key selection; temporary scheme preferences are restored.
Screen recordings were inspected around reading selection and partial commit;
these checks are not a claim that every animation or performance percentile has
been validated.

## Alternatives Rejected

- Fixing only a particular word or separator sequence leaves conflicting raw
  input ownership intact.
- Adding renderer delays or more freshness heuristics cannot recover lost
  source digits or an engine command that never executed.
- Removing candidate-comment preview loses the user's intended assistance.
- Creating a new renderer or replacing all Chinese schemes expands the change
  without addressing the demonstrated Pinyin defects.
- Keeping the old tracker/bridge flow as a compatibility fallback recreates
  parallel owners and makes the regression tests unable to define one contract.
