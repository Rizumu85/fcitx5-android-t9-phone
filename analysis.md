# Technical Analysis

## Rime provisioning fallback

The application normally downloads and deploys the matching `rime-ice-t9-phone` archive. A GitHub connection is not guaranteed on every network, so the user-facing installation instructions must retain a complete manual path. The manual path needs both APK installation and the Android app-data destination; copying only the archive or only pressing `同步` is insufficient.

## Password editor isolation

`FcitxInputMethodService.isChineseT9InputModeActive()` previously depended only on `currentT9Mode`. Password capability is tracked separately in `capabilityFlags`, and the temporary password keyboard is managed by `KeyboardWindow`. This allowed a stale Chinese candidate surface and Rime readiness status to survive an editor transition into password mode. The fix belongs at the input-session mode boundary: password capability must suppress all T9 candidate sources, and a new input session must clear the old candidate surface before the new keyboard is shown.
