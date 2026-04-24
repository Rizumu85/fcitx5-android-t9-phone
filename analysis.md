# Analysis

## Current Task

Remove the abandoned GitHub remote build workflow that was added for APK artifact builds.

## Current Behavior

The repository has several GitHub Actions workflows under `.github/workflows/`.
The custom remote build workflow is `.github/workflows/f22_build.yml`, named
`Build F22 Pro APK`. It runs on pushes to `master`/`main` and on manual dispatch,
builds `./gradlew assembleDebug`, and uploads the debug APK as an artifact.

The other workflows are broader upstream-style automation:

- `pull_request.yml`: cross-platform release build checks for pull requests.
- `nix.yml`: Nix-based release APK/plugin build checks.
- `fdroid.yml`: F-Droid metadata/build validation.
- `publish.yml`: publication for build convention and libraries.

Only the custom F22 remote build workflow matches the abandoned GitHub remote build function.

## Constraints

- Keep the deletion scoped to the abandoned remote build workflow.
- Do not remove issue templates or unrelated CI workflows.
- Limit verification to static checks; full app/plugin compilation and functional testing are left to the user.

## Edge Cases

- Removing the workflow disables that GitHub Actions remote APK build path.
- Local Android Studio/Gradle build paths are unaffected.
- Other GitHub Actions workflows may still run for PRs, Nix, F-Droid, and publishing.

## Previous Completed Task

The T9 Hanzi candidate character budget default was changed from 12 to 10 in `AppPrefs.kt`.
The Rime plugin was left on inherited shared versioning; no Rime-specific override remains.
