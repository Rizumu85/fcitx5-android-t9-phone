# ADR-0001: Scheme-Aware Physical T9 Key Contract

- Status: Accepted
- Date: 2026-07-09

## Context

The original physical T9 rules treated short `1` as the punctuation entry key
for Pinyin and English, while `*` controlled English case or inserted a literal
star. That assignment does not extend to the requested Chinese mechanisms:

- Mobile Stroke input needs `1..5` for horizontal, vertical, left-falling,
  dot/right-falling, and bend strokes. `6` is needed as the unknown-stroke
  wildcard.
- Zhuyin input needs `1` for the `ㄅㄆㄇㄈ` group.
- English has no letter group on short `1`, so it can use the key for case.

This project calls the first mechanism **Stroke** or **five-stroke mobile
input**. It is not Wubi 86, which uses Latin root codes and a different input
model.

A literal global key map would either block Stroke and Zhuyin or force users to
learn unrelated punctuation keys in each mechanism. The physical key path is
also latency-sensitive: it must not gain dictionary work, Rime calls, or view
measurement as more mechanisms are added.

## Decision

### 1. Share semantic roles, not literal digit meanings

`*` is the common symbol-entry key for text schemes. `1` belongs to the active
input scheme. Number mode remains a deliberate exception because `1` and `*`
are both literal numeric/operator input.

| Scheme | Short `1` | Long `1` | Short `*` | Long `*` |
| --- | --- | --- | --- | --- |
| Pinyin | Syllable separator while composing; consumed with no action while idle | Candidate shortcut 1 while composing; literal `1` while idle | Commit the highlighted Hanzi without a space, then open Chinese punctuation; open punctuation directly while idle | Insert literal `*` and clear incompatible Chinese composition |
| Stroke | Horizontal stroke | Candidate shortcut 1; literal `1` when no candidate owns it | Commit the highlighted Hanzi, then open Chinese punctuation | Insert literal `*` |
| Zhuyin | `ㄅㄆㄇㄈ` group | Candidate shortcut 1; literal `1` when no candidate owns it | Commit the highlighted Hanzi, then open Chinese punctuation | Insert literal `*` |
| Smart English | Cycle `abc -> Abc -> ABC -> abc` | Candidate shortcut 1; literal `1` when no candidate owns it | Commit the selected word without a space or next-word prediction, then open English punctuation | Commit pending text and insert literal `*` |
| Simple English | Cycle `abc -> Abc -> ABC -> abc` | Literal `1` | Commit the pending multi-tap character, then open English punctuation | Commit pending text and insert literal `*` |
| Number | Digit `1` | Existing number-operator shortcut | Literal `*` | Open the number operator panel |

When punctuation candidates are already visible, another short `*` toggles the
Chinese or English punctuation set. Numeric candidate shortcuts remain long
presses on `1..9,0`; moving English case to short `1` must not remove shortcut
1.

### 2. Keep confirmation rules mechanism-specific

`0` cannot be a universal confirm key because Zhuyin needs it for
`ㄧㄨㄩ`. Physical OK/center remains the universal candidate confirmation.

- Pinyin: short `0` confirms the focused pinyin or Hanzi row; idle `0` inserts
  a space. Short `#` fixes/submits the current pinyin reading into composition;
  it does not commit a Hanzi or insert a newline.
- Stroke: short `0` confirms the highlighted Hanzi; idle `0` inserts a space.
  Short `#` is consumed while composing because there is no separate phonetic
  reading to submit.
- Zhuyin: `0` enters `ㄧㄨㄩ`. Short `#` fixes/submits the current Zhuyin
  reading; OK/center confirms the Hanzi candidate.
- English: short `0` confirms with the existing spacing/prediction policy.
  Short `#` commits pending text without a space, performs return, and stops
  prediction.
- Number: digits stay literal and short `#` performs return.

Long `#` clears incompatible composition and switches to the next top-level
mode. This avoids carrying hidden Pinyin, Stroke, or Zhuyin state into the next
mode.

### 3. Use the following Chinese code maps

Stroke uses semantic tokens so the engine Adapter, not the physical key flow,
owns backend spelling:

| Key | Token | Display |
| --- | --- | --- |
| `1` | `HORIZONTAL` | 横 |
| `2` | `VERTICAL` | 竖 |
| `3` | `LEFT_FALLING` | 撇 |
| `4` | `DOT_OR_RIGHT_FALLING` | 点/捺 |
| `5` | `BEND` | 折 |
| `6` | `UNKNOWN` | 未知笔画 |

Short `7..9` are consumed while Stroke composition is active so digits cannot
leak into the editor. Their long presses remain candidate shortcuts or literal
digit fallbacks. The Stroke Adapter translates `UNKNOWN` to a backend query
without exposing that encoding to the key flow or UI. For the Rime Adapter,
exact `1..5` codes use one native table prism. Up to two `6` tokens are expanded
only for the active query (at most 25 indexed lookups), then a bounded result
pool is deduplicated and reranked by candidate quality. Wildcard combinations
are not compiled because the twelve-position algebra prototype exceeded 1 GB
deployment RSS on the target phone.

Zhuyin uses the researched phone T9 grouping:

| Key | Zhuyin group |
| --- | --- |
| `0` | `ㄧㄨㄩ` |
| `1` | `ㄅㄆㄇㄈ` |
| `2` | `ㄉㄊㄋㄌ` |
| `3` | `ㄍㄎㄏ` |
| `4` | `ㄐㄑㄒ` |
| `5` | `ㄓㄔㄕㄖ` |
| `6` | `ㄗㄘㄙ` |
| `7` | `ㄚㄛㄜㄝ` |
| `8` | `ㄞㄟㄠㄡ` |
| `9` | `ㄢㄣㄤㄥㄦ` |

The first Zhuyin version omits dedicated tone keys and lets the engine rank
readings. Tone entry may be added later only if user testing shows that
candidate ranking cannot replace it.

### 4. Keep mode switching shallow for users

Long `#` continues to cycle only the top-level modes: Chinese, English, and
number. Pinyin, Stroke, and Zhuyin are Chinese input schemes, not extra entries
in that fast cycle.

Settings will own the enabled Chinese schemes and their order. The compact
settings panel exposes one Chinese-scheme action whose menu focuses the current
scheme. Users who need only one scheme see no additional switching step; users
who enable several can change the Chinese mechanism explicitly without making
the common Chinese/English/number cycle longer.

### 5. Preserve one command-based hot path

`PhysicalT9KeyFlow` remains the owner of key-down/key-up pairing, held-duration
long press, and mode dispatch. Scheme Modules reduce an immutable state
snapshot to semantic commands such as:

- `CycleEnglishCase`
- `ShowChinesePunctuationCandidates`
- `ShowEnglishPunctuationCandidates`
- `CommitChineseCandidateAndShowPunctuation`
- `CommitLiteralStar`
- future `AppendStrokeToken` and `AppendZhuyinGroup`

Command names must not encode the old key that happened to trigger them.
`PhysicalT9CommandExecutor` and host Adapters own side effects. Replaced `1`
punctuation deferral and key-named English star commands are deleted rather
than retained as fallback behavior.

Chinese candidate selection is asynchronous. The punctuation follow-up is
therefore chained to successful Rime selection instead of rendering the
punctuation row immediately. This prevents the later candidate event from
overwriting or flashing the punctuation surface. Selection runs on the
service's serialized Fcitx queue and carries both a composition ticket and a
shown-source ticket. New input, paging, or a scheme transition invalidates the
operation before an obsolete original index can be committed.

### 6. Reuse the candidate snapshot pipeline

Every input scheme produces three compact values:

1. A composition snapshot: raw tokens, resolved reading/code, and generation.
2. A candidate snapshot: candidates, cursor, page, original indices, and
   source generation.
3. A presentation snapshot: top preview, optional filter row, focus, and
   visibility.

New schemes join `T9CandidateUiSnapshotPipeline`; they do not add RecyclerView
branches, independent paging state, or direct view mutation in
`FcitxInputMethodService`. Pinyin, Stroke, and Zhuyin may use different Rime
schemas, but their Adapters publish the same render-ready candidate contract.

Candidate freshness is interpreted at the scheme boundary. Pinyin validates
Latin readings, Stroke validates its stroke preedit, and Zhuyin validates
Bopomofo comments. The Pinyin bulk-filter session is not a generic Chinese
stage: Stroke and Zhuyin bypass it and use the shared local page budget. This
prevents a Pinyin-specific readiness heuristic from hiding or delaying valid
non-Pinyin candidates.

Bulk-filter state and local-pager state have independent reset operations.
Disabling the Pinyin bulk source for Stroke or Zhuyin must not erase an
unchanged local page, width budget, or source cache. While a replacement frame
is pending or hidden, the pipeline invalidates its shown-interaction ticket so
OK and numeric shortcuts cannot select candidates that are no longer visible.

Scheme transitions are identified by the concrete Rime sub-mode, not only by
the broad `Pinyin`, `Stroke`, or `Zhuyin` classification. One transition
operation clears composition, loading, source, focus, and presentation state;
service reconnect also initializes from the cached current input method in
case the original change event was missed. Zhuyin reading boundaries inserted
by short `#` are stored in the local raw-code session as well as sent to Rime,
which keeps local Backspace and engine state aligned.

### 7. Keep key latency independent of dictionary size

The physical-key reducer performs O(1) classification and compact session
mutation. It performs no file access, dictionary scan, Rime call, coroutine
wait, or Android view measurement.

Each scheme caches candidate results by `(scheme, raw sequence, page,
dictionary generation)`. Dictionaries and Rime schemas warm when the IME or
input session starts, not on the first key. Slow engine results carry a
composition ticket containing scheme, raw input, and session revision; stale
results are discarded instead of briefly replacing a newer UI snapshot.
Unchanged presentation snapshots do not rebuild or rerender candidate rows.

## Consequences

- Existing users must relearn that `1` controls English case and `*` opens
  punctuation. Release notes and physical-key test documentation must call out
  the change.
- Pinyin retains its necessary `1` separator without blocking Stroke or
  Zhuyin.
- Long-press candidate selection remains uniform across schemes.
- Number mode intentionally differs from text modes because literal arithmetic
  input is more important than punctuation-set consistency.
- Adding Stroke or Zhuyin requires a composition/engine Adapter and snapshots,
  but no new candidate renderer or service-owned key state machine.
- External schemas, dictionaries, or algorithms require a license audit.
  GPL-licensed TT9 code or dictionary data must not be copied into this
  LGPL-licensed project without an explicit compatibility decision.

## Rejected Alternatives

### Keep `1` as punctuation everywhere

Rejected because it consumes required Stroke and Zhuyin input groups.

### Literally swap every `1` and `*` branch

Rejected because Pinyin, Stroke, Zhuyin, English, and number mode assign
different data roles to `1`; number mode also needs a literal `*` operator.

### Put every future rule in `FcitxInputMethodService`

Rejected because it would duplicate long-press and mode rules outside Physical
T9 Key Flow, reduce locality, and make key latency depend on Android side
effects.

### Give each mechanism its own candidate UI

Rejected because paging, focus, width, and floating-window timing bugs would
multiply. Mechanisms should differ before the candidate snapshot seam, not
after it.

## References

- [Rime Stroke schema](https://github.com/rime/rime-stroke/blob/master/stroke.schema.yaml)
- [Rime Bopomofo schema](https://github.com/rime/rime-bopomofo/blob/master/bopomofo.schema.yaml)
- [TT9 Bopomofo/Zhuyin proof of concept](https://github.com/taitungsun/tt9-bopomofo-zhuyin)
