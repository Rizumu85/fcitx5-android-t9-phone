# T9 Engine-Backed Pinyin Selection Design

## Overview

T9 pinyin selection is a composition transition, not a final text commit. When a
user selects a pinyin chip, the service records the selected syllable in the
Kotlin presentation model and attempts to edit the current Rime input through
`FcitxAPI.replaceRimeInput`.

Example:

```text
2496 -> select ai -> Rime input ai'96
```

The top pinyin row and pinyin chip row still use `T9CompositionModel`, but the
Hanzi row should come from Rime's narrowed engine candidates whenever the
replacement succeeds.

## State Model

- `T9ResolvedSegment.pinyin`: selected pinyin text.
- `T9ResolvedSegment.sourceDigits`: original T9 digits for undo/reopen.
- `T9ResolvedSegment.engineBacked`: true only after Rime replacement succeeds.
- `T9CompositionModel.unresolvedDigits`: remaining raw T9 suffix for the pinyin
  chip row.
- `T9CompositionModel.pendingSelection`: suppresses client filtering while the
  queued bridge replacement is still unresolved.

## Selection Flow

1. Compute the selected digit span with `T9PinyinUtils.matchedPrefixLength`.
2. Optimistically update `T9CompositionModel` so the UI advances immediately.
3. Queue a fcitx job:
   - read current Rime input with `getRimeInput()`;
   - replace the selected digit span with `pinyin'`;
   - mark the segment `engineBacked` on success;
   - clear pending state and allow fallback filtering on failure.

## Delete/Reopen Flow

If the last resolved segment is engine-backed, delete replaces the matching
`pinyin'` span in Rime input with its original `sourceDigits`. If that reverse
replacement fails, the service resets Rime and replays raw T9 digits, then marks
remaining resolved segments as fallback-only so client filtering can recover.

## Candidate Policy

- Engine-backed selections disable selected-pinyin client filtering.
- Pending bridge replacements also disable client filtering to avoid empty rows
  caused by stale comment matching.
- Failed bridge replacements fall back to the previous client-side filter path.
- Candidate budget consistency is intentionally left as a follow-up cleanup.
