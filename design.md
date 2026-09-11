# Design

## Project Goal

Make first-use Rime setup recoverable on restricted networks while keeping password input visually and behaviorally isolated from Chinese T9 state.

## User-facing behavior

- The README and Baidu-distribution instructions explain automatic setup first and provide the full manual import procedure as a reliable fallback.
- A password editor never shows Chinese Rime status, candidate bubbles, or stale T9 overlays.
- Entering a new editor session clears transient candidate UI before the new input surface is displayed.
