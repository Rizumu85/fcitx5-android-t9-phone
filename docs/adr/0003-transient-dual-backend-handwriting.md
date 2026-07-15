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
- Model construction, availability checks, Ink conversion, native warmup, and
  enhanced recognition are serialized on one background lane. Merely opening
  handwriting does not construct ML Kit objects on the input thread.
- A downloaded ML Kit model is prepared only after a two-second stroke-free
  quiet period and is marked ready after native initialization. A down event
  during that gate cancels preparation before ML Kit is touched; that character
  deliberately uses the bundled backend rather than letting model JIT compete
  with drawing. If preparation has already completed, the enhanced backend is
  ready before the next character begins. ML Kit output is restricted to one
  Han character; an empty or failed enhanced result falls back to the bundled
  recognizer instead of exposing punctuation or Latin strokes as candidates.
- The recognizer for one character is selected on its first down event, before
  the stroke has finished.
  A model that becomes ready halfway through that character is used only for
  the next character, preventing unexplained candidate replacement.
- Completed strokes are copied into recognition data. AndroidX Ink renders a
  separate low-latency path, so brush smoothing, motion prediction, and
  pressure response never alter the geometry sent to either recognizer.
- Users can choose `Pen` or `Calligraphy` in Input Settings. Both styles use
  stock AndroidX Ink brush families; the setting changes presentation only and
  never changes the recognition points. The style is resolved once when each
  stroke begins, because Android may hide and reuse the same IME window after a
  Settings change instead of attaching a new one.
- AndroidX Ink completion callbacks and coordinator publications are reconciled
  by a render ledger. A local completion reserves its stroke identity before
  Ink is notified, preventing a synchronous callback from deleting the fresh
  stroke and preserving finish order when callbacks arrive out of order.
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
- Users can enable tone-marked Pinyin feedback after commit. The lookup uses the
  Pinyin Helper database already bundled with Fcitx, preserves every distinct
  reading for polyphonic characters, and runs after text has been committed.
  Feedback disappears after 4.5 seconds or immediately when the next stroke
  begins, so it teaches the previous character without reserving permanent
  drawing space.
- Undo and clear belong to the existing input-window title bar, outside the
  writing tray, so controls never consume or cover drawing space.
- The tray uses equal left and right margins and a static shadow drawable with
  only a small downward offset. View elevation is deliberately avoided because
  the Ink front-buffer surface must remain in a predictable layer hierarchy.
  Recognition status never reserves a permanent row beneath the tray.

## Performance Constraints

- Pointer move handling performs no repository access, coroutine launch, or
  model call. AndroidX Ink consumes unbuffered motion events through its
  low-latency renderer and is eagerly initialized before the first stroke.
  `MotionEventPredictor` supplies display-only predicted samples while
  recognition records actual events exclusively.
- The front buffer owns only the active stroke. Finished Ink strokes move to a
  stable normal view layer immediately, avoiding both front-buffer growth and
  visible retract/reappear handoffs. Append, undo, and clear reconcile by
  stroke identity; recognition state changes do not rebuild geometry.
- The bundled repository is compact binary data and is parsed once outside the
  first-stroke render path.
- Offline matching runs on `Dispatchers.Default`, reuses dynamic-programming
  buffers, and exits superseded jobs cooperatively. ML Kit objects are lazy,
  all Google tasks resume through a direct callback executor onto the model
  lane, and cancellation is never converted into fallback recognition work.
- Immutable AndroidX Ink brushes are cached by style, color, and tray size so
  the first-down path does not repeatedly cross the brush-construction JNI seam.
- Candidate publication is generation checked and goes through the existing
  snapshot diff renderer.
- Pronunciation lookup runs on the Fcitx dispatcher, loads Pinyin Helper only on
  first use, and publishes through its own generation check. It cannot delay a
  commit or revive feedback after a new stroke or window exit.
- Recognition timing is emitted through Timber and Android trace sections so
  device testing can separate drawing, matching, candidate snapshot, and frame
  latency.

## Consequences

The app gains a reliable offline floor and a higher-quality enhanced path
without mirroring Google's model or making input network-dependent. The APK
grows by the compact repository and ML Kit runtime, but not by the downloaded
language model. Candidate behavior remains consistent with the rest of the T9
product.
