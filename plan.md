# Plan

## Direction 1: Recoverable Rime Installation

Goal: Keep automatic provisioning convenient without making it the only way to obtain a usable Chinese input method.

- [x] Restore the detailed manual Rime import steps in the README.
- [x] Keep the Baidu Markdown and TXT instructions in sync with the manual path.

## Direction 2: Password Session Isolation

Goal: Treat password capability as a hard boundary for candidate sources and transient input UI.

- [x] Gate Chinese, prediction, and smart-English mode queries while password capability is active.
- [x] Clear stale candidate/input presentation at the start of every editor session.
- [x] Run the narrowest relevant verification (`:app:compileDebugKotlin :app:testDebugUnitTest`); the existing suite passed.
