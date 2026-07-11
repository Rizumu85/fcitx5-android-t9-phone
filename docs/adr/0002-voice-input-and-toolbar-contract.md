# ADR-0002: Voice Input and Always-Available Toolbar Contract

- Status: Accepted
- Date: 2026-07-11

## Context

The idle KawaiiBar previously reserved its leading button for expanding and
collapsing the toolbar. Most T9 users keep the toolbar expanded, so the collapsed
state spent a scarce physical-phone slot without providing a useful default
workflow. At the same time, the existing voice setting reused the hide-keyboard
button and only recognized IMEs that declared an explicit `voice` subtype.
Standalone voice IMEs can omit subtype metadata, which made the enabled setting
silently produce no button or action.

Toolbar actions also have different value for different users. Undo and redo
are useful in some editors, while cursor movement or clipboard access may be
more important on another device. Future actions should not require adding an
ever-wider fixed row.

Finally, Quick Settings created an empty container and waited for a serialized
engine query before rendering both local and Fcitx actions. When the engine queue
was busy, users saw a blank window for several seconds even though the local
actions were already available.

## Decision

### 1. Keep the idle toolbar expanded

KawaiiBar has no ordinary collapsed/empty state. Its leading slot shows voice
input when configured. Clipboard and inline-suggestion surfaces may temporarily
replace the center row; in those states the leading slot becomes a Back action
that returns to the toolbar. This preserves access that the old expand control
provided without allowing the toolbar itself to be collapsed.

The privacy indicator takes the leading slot and disables voice in editors that
request no personalized learning. Password editors also hide voice.

### 2. Make optional toolbar membership explicit

Settings exposes one **Toolbar buttons** manager. Users can independently show
or hide voice, undo, redo, text editing, clipboard, and hide-keyboard actions.
Quick Settings is always shown and therefore has no mutable preference.

Each optional action uses an independent boolean preference. The settings UI
presents them as one product-level choice, while the live IME can update only the
affected view visibility without parsing a collection or recreating InputView.
Button order remains stable; reordering is not introduced until a real workflow
requires it.

### 3. Treat voice input as an IME target

`InputMethodUtil` resolves a typed voice target containing an enabled input
method id and an optional subtype:

1. An explicitly preferred enabled IME wins, including a standalone IME with no
   subtype metadata.
2. Automatic selection prefers an explicit `voice` subtype.
3. A conservative voice/speech/dictation identity hint recognizes standalone
   voice services such as Google's `VoiceInputMethodService`.

Switching uses the subtype overload when present and the IME-only overload when
absent. Failure is reported to the user instead of silently doing nothing. The
target is resolved at activation time so enabling or disabling an IME does not
leave a stale toolbar target.

### 4. Keep long-0 ownership in Physical T9 Key Flow

Idle long `0` is configurable between literal `0` and voice input. Literal `0`
remains the default. The flow emits `SwitchToVoiceInput`; Android target lookup
and UI feedback remain platform effects.

Candidate ownership always wins:

- punctuation and visible Smart English candidates keep their numeric shortcut;
- Chinese composition keeps its Hanzi shortcut;
- pending English multi-tap input keeps the literal-zero behavior;
- voice is available only when Pinyin/Stroke or English is completely idle;
- Zhuyin keeps `0` as its `ㄧㄨㄩ` composition group;
- number mode keeps its existing digit/operator behavior;
- password editors keep literal behavior and never start voice.

This avoids delaying the first Zhuyin digit merely to distinguish a possible
long press and keeps dictionary or package-manager work out of the reducer.

### 5. Render Quick Settings from the cached snapshot

Fcitx already maintains `cachedState.statusAreaActions` from engine events.
Quick Settings renders local actions plus that snapshot before its view is
attached. Future broadcasts update the attached window normally. It does not
issue a second `statusArea()` query on open.

## Consequences

- The primary idle toolbar is immediately useful and no longer changes between
  expanded and collapsed layouts.
- Optional actions can be removed without hiding Quick Settings or changing
  stable button order.
- Voice works with both subtype-based and standalone voice IMEs once Android has
  enabled them, and unavailable targets produce actionable feedback.
- The physical key reducer remains deterministic and O(1).
- Quick Settings has useful first-frame content even while Fcitx is busy.
