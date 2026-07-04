# T9 Candidate UI Architecture

This app is optimized for phone-style T9 input. The T9 candidate strip should be treated as the primary candidate UI for T9 surfaces, not as a temporary special case.

## Fixed Candidate Strip

`T9FixedCandidatesUi` is the fixed-row candidate strip used by T9 candidate surfaces. It is designed for small, page-sized candidate sets where one physical number key can select one visible candidate.

Use this strip for:

- Chinese T9 Hanzi candidates.
- Smart English T9 word candidates.
- T9 punctuation candidates.
- Future T9 candidate rows that use number-key shortcuts and page navigation.

The strip has these invariants:

- One page contains at most ten selectable candidates.
- Shortcut labels map to `1 2 3 4 5 6 7 8 9 0`.
- The row height is stable while T9 shortcut labels are shown.
- Candidate focus is rendered inside the strip, while pinyin focus is rendered by the pinyin row.
- Page buttons are part of the strip, but the pager and original-index mapping are produced by the T9 candidate pipeline.

The current class name still says `T9FixedCandidatesUi`. If this module grows beyond the current T9 call site, prefer renaming it to `FixedCandidateStripUi` and keeping the same fixed-strip invariants explicit in its interface.

## What Should Not Move To The Fixed Strip

Do not replace every candidate-like UI with the fixed strip just because it renders selectable text.

Keep these separate:

- The horizontal keyboard candidate bar. It uses `CandidateListEvent`, integrates with the expand button, and has fill-width behavior that is not a T9 fixed-strip concern.
- The expanded candidate window. It is a large paging/grid surface, not a ten-item shortcut row.
- Emoji, symbol, and picker windows. They have tabs, pages, and recent-use behavior that are separate from text input candidates.
- Settings, status menus, and dictionary switching UI. These are menus or settings surfaces, not candidate rows.

## Migration Direction

The long-term direction is:

1. Route all T9 bottom candidate surfaces through the fixed strip.
2. Keep non-T9 floating candidates on the existing paged/flex implementation until there is a concrete reason to delete it.
3. If non-T9 floating candidates are removed from the product, delete the old floating candidate adapter rather than keeping two active implementations.
4. Keep the T9 pinyin row as a separate row above the fixed strip, synchronized by explicit render state instead of RecyclerView layout side effects.

This preserves the current visual design while making the T9 interaction model more predictable and responsive.
