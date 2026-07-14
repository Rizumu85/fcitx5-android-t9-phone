# ADR 0003: Transient Dual-Backend Handwriting

## Status

Accepted

## Context

Handwriting is an auxiliary input mechanism for users who cannot conveniently
produce a character through Pinyin, Stroke, or Zhuyin. It must remain useful on
mainland networks and on a newly installed device before any network model is
available. It must also reuse the established candidate bubble and physical
selection rules instead of introducing a second candidate system.

The drawing surface has different latency requirements from recognition. A
finger or stylus stroke must render synchronously, while repository loading and
matching must never block the UI thread.

## Decision

- Handwriting is a transient `InputWindow`, entered from a configurable toolbar
  button. It is not another member of the long-`#` input-mode cycle or long-`*`
  Chinese-scheme cycle.
- Leaving the handwriting window discards all uncommitted strokes immediately.
  There is no confirmation dialog and no restoration when the user returns.
- The bundled offline recognizer is always available. Its 9,507-character
  substroke repository is loaded on a background dispatcher when handwriting
  opens and then retained as an immutable process cache.
- Google ML Kit Digital Ink Recognition is an optional enhanced backend. ML Kit
  owns its model download. Opening handwriting never starts a network request;
  model download and retry belong to Input Settings. The first missing-model
  session shows one transient action that deep-links to that setting, while the
  drawing surface never displays model lifecycle status.
- A downloaded ML Kit model is warmed after a short idle delay and is marked
  ready only after initialization. This keeps native model startup from
  competing with the first visible stroke. ML Kit output is restricted to one
  Han character; an empty or failed enhanced result falls back to the bundled
  recognizer instead of exposing punctuation or Latin strokes as candidates.
- The recognizer for one character is selected when its first stroke begins.
  A model that becomes ready halfway through that character is used only for
  the next character, preventing unexplained candidate replacement.
- Completed strokes are copied into recognition data. AndroidX Ink renders a
  separate low-latency `pressurePen` path, so brush smoothing and pressure
  response never alter the geometry sent to either recognizer.
- AndroidX Ink completion callbacks and coordinator publications are reconciled
  by a render ledger. A local completion reserves its desired slot before Ink is
  notified, preventing a synchronous callback from deleting the fresh stroke.
- Recognition starts 420 ms after the most recently completed stroke. Every
  stroke resets the same quiet-period timer; there is no first-stroke special
  case that can repeatedly interrupt a multi-stroke character. New strokes,
  undo, clear, commit, and window exit invalidate the previous generation so
  stale results cannot publish.
- A stroke-set mutation immediately invalidates and hides published candidates.
  A visible candidate must always be committable; the UI never leaves an old
  bubble on screen while rejecting its tap during newer recognition.
- Handwriting joins `T9CandidateUiSnapshotPipeline` as the explicit
  `HANDWRITING` source. Paging, original indices, focus, shortcut labels,
  preview, and commit use the existing candidate bubble and interaction
  controller.
- Touching a candidate, OK, or short `#` with strokes commits the selected
  character and clears the canvas while keeping handwriting open. Long number
  keys select visible shortcuts. Delete undoes one stroke; when the canvas is
  empty, delete continues to the editor deletion pipeline.
- Undo and clear are overlay controls at the tray's upper-right corner. The
  rounded, visually raised theme-colored tray occupies the remaining input
  window; recognition status never reserves a permanent row beneath it.

## Performance Constraints

- Pointer move handling performs no repository access, coroutine launch, or
  model call. AndroidX Ink consumes unbuffered motion events through its
  low-latency renderer and is eagerly initialized before the first stroke.
- The renderer retains finished stroke geometry and reconciles append, undo,
  and clear by stroke identity. Recognition state changes do not rebuild paths.
- The bundled repository is compact binary data and is parsed once outside the
  first-stroke render path.
- Offline matching runs on `Dispatchers.Default`, reuses dynamic-programming
  buffers, and exits superseded jobs cooperatively; ML Kit tasks are awaited
  asynchronously.
- Candidate publication is generation checked and goes through the existing
  snapshot diff renderer.
- Recognition timing is emitted through Timber and Android trace sections so
  device testing can separate drawing, matching, candidate snapshot, and frame
  latency.

## Consequences

The app gains a reliable offline floor and a higher-quality enhanced path
without mirroring Google's model or making input network-dependent. The APK
grows by the compact repository and ML Kit runtime, but not by the downloaded
language model. Candidate behavior remains consistent with the rest of the T9
product.
