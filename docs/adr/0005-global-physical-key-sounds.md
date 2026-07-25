# ADR 0005: Opt-in Global Physical Key Sounds

## Status

Accepted

## Context

Android routes ordinary keypad keys to the active input method in text fields,
but system policy can intercept Back, Menu/App switch, Call, and End call before
the IME receives them. An application cannot observe those keys globally through
the normal input-method API. Privileged input monitoring is unavailable to a
third-party application.

The product should provide consistent selected key-pack feedback outside text
fields without consuming keys, reading screen content, or adding polling and
background work.

## Decision

- Global coverage is an optional `AccessibilityService` capability that the user
  enables explicitly in Android settings. The application never attempts to
  enable it programmatically.
- The service declares only key-event filtering. It cannot retrieve window
  content, perform gestures, or consume keys; `onKeyEvent` always returns
  `false`.
- The global path handles only Back, Menu/App switch, Call, and End call.
  Ordinary typing, dial, navigation, and volume keys keep their existing IME or
  system feedback, preventing double sounds.
- `PhysicalKeySoundDispatcher` owns sound classification for both the IME and
  accessibility sources. It rejects repeats and deduplicates the same physical
  event when Android delivers it through both paths.
- The accessibility service disables ordinary accessibility-event delivery
  after binding. Runtime work is therefore one constant-time key classification
  and, for the five covered keys only, an already-preloaded `SoundPool` play.
- System sound-setting changes are observed by URI callback rather than polling.

## Consequences

Complete system-key coverage requires a one-time, visible accessibility
authorization. Without it, the input method retains its existing text-field and
idle-key behavior.

The service remains bound while authorization is enabled, but performs no
periodic work and receives no screen-content event stream. It does not alter the
behavior, ordering, or ownership of physical keys.
