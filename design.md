# Design

## Remote Build Removal

Remove `.github/workflows/f22_build.yml` entirely. This is the narrowest way to disable the
abandoned GitHub remote APK build without changing the Android project, local Gradle tasks,
or upstream-style CI workflows.

## Implementation Choice

Use a file deletion rather than leaving a disabled workflow. A deleted workflow cannot be
accidentally triggered from the GitHub Actions UI, and it avoids maintaining a known-broken
remote build path.

## Non-Goals

- No changes to `app/org.fcitx.fcitx5.android.yml`.
- No changes to local Android Studio or Gradle build documentation.
- No changes to `.github/workflows/pull_request.yml`, `nix.yml`, `fdroid.yml`, or `publish.yml`.

## Previous Completed Design

The T9 Hanzi candidate character budget remains an integer managed preference with default `10`
and range `4..24`.
The Rime plugin remains on inherited shared versioning.
