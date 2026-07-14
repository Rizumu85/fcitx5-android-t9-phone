# Release Runbook

This runbook is the reusable checklist for preparing a signed public release.
It keeps user-facing feature docs, version-specific release notes, Baidu Netdisk
staging, version numbers, and signed APK builds in their correct lanes.

## Release Lanes

Use these files for different purposes:

| Surface | Purpose | What belongs here | What does not belong here |
| :--- | :--- | :--- | :--- |
| `README.md` | Stable user manual and project overview | Permanent user-facing features, install steps, key behavior tables, supported modes | Version-specific changelog sections such as `4.1.0 fixes` |
| `release-notes-vX.Y.Z.md` | GitHub Release text for one version | New features, fixes, install-package explanation for that version | Full README copy, long setup tutorial |
| `release-baidu/不懂如何安装请读.{md,txt}` | Current Baidu Netdisk install/readme text | Current install instructions and stable feature highlights | Old-version changelog history |
| `release-baidu/vX.Y.Z/` | Local staging folder for the version uploaded to Baidu | The four distributed APKs and matching readme text files | Debug APKs, unsigned APKs, x86/x86_64 APKs unless explicitly requested |
| GitHub Release assets | Public release downloads | Signed release APKs and release notes | Debug or unsigned builds |

## Documentation Rules

1. Keep `README.md` evergreen.
   - Add new modes and behavior into the existing sections, such as
     `Keyboard usage`, `Supported modes`, or feature bullets.
   - Do not create a `X.Y.Z update` section in `README.md`.
   - Do not list bug fixes in `README.md` unless they describe a permanent user
     behavior that should be documented.

2. Keep `release-notes-vX.Y.Z.md` version-specific.
   - Put user-visible additions under `New Features`.
   - Put regressions, behavior corrections, and stability work under `Fixes`.
   - Keep the install-package explanation versioned and concrete, including the
     exact APK names for `armeabi-v7a` and `arm64-v8a`.
   - Always include a `Rime Version Mapping` section with the exact Rime plugin
     version and the exact `rime-ice-t9-phone` release tag. State clearly whether
     users must update and redeploy the configuration package.

3. Keep Baidu text practical.
   - Update both Markdown and plain-text files.
   - Keep the root Baidu readme as the current public instructions.
   - Keep only the current release's versioned readme in Git. Delete the
     previous version's duplicate readmes when preparing a new release; Git
     history and `release-notes-vX.Y.Z.md` are the archive.
   - Baidu upload may need to be manual. The agent can help remove old remote
     APK/readme files through the browser when logged in, but the user may need
     to upload the new APKs manually.

## Version Bump

Update all version-controlled version surfaces before building:

1. In `build-logic/convention/src/main/kotlin/Versions.kt`:
   - Increment `baseVersionCode`.
   - Set `baseVersionName` to the new version.

2. In `gradle.properties`:
   - Set `buildVersionName=X.Y.Z`.
   - This prevents release APK names and runtime version strings from becoming
     `git describe` strings such as `vX.Y.Z-0-gabcdef`.

3. In `app/build.gradle.kts`:
   - Set `RIME_CONFIG_BASELINE_VERSION` to the exact `rime-ice-t9-phone` release
     distributed with this app version. The updater uses this only to recognize
     configurations installed before version tracking existed.

4. In docs and release files:
   - Create or update `release-notes-vX.Y.Z.md`.
   - Update the exact APK examples in Baidu readme files.
   - Update any versioned local staging folder path.

4. Check the result:

```shell
rg -n "X\\.Y\\.Z|old-version" README.md release-notes-vX.Y.Z.md release-baidu gradle.properties build-logic/convention/src/main/kotlin/Versions.kt
```

## Pre-Build Verification

Run focused checks before creating signed APKs:

```shell
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest
git diff --check
git status --short
```

If there is a known unrelated test failure, record it before release work
continues. Do not treat an unrelated failing suite as a signed-release problem.

## Signed Release Build

Build signed release APKs with explicit version and signing properties. Do not
commit the signing password to the repository.

```shell
VERSION="X.Y.Z"
KEYSTORE="/absolute/path/to/release.keystore"
read -rsp "Signing password: " SIGN_KEY_PWD
echo

BUILD_VERSION_NAME="$VERSION" \
SIGN_KEY_FILE="$KEYSTORE" \
SIGN_KEY_ALIAS="key0" \
SIGN_KEY_PWD="$SIGN_KEY_PWD" \
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew clean :app:assembleRelease :plugin:rime:assembleRelease
```

Expected release outputs:

```text
app/build/outputs/apk/release/org.fcitx.fcitx5.android-X.Y.Z-arm64-v8a-release.apk
app/build/outputs/apk/release/org.fcitx.fcitx5.android-X.Y.Z-armeabi-v7a-release.apk
plugin/rime/build/outputs/apk/release/org.fcitx.fcitx5.android.plugin.rime-X.Y.Z-arm64-v8a-release.apk
plugin/rime/build/outputs/apk/release/org.fcitx.fcitx5.android.plugin.rime-X.Y.Z-armeabi-v7a-release.apk
```

The build may also produce x86/x86_64 APKs. Do not upload those unless the
release explicitly supports them.

## Signed APK Checks

Verify every distributed APK before upload:

```shell
SDK_ROOT="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
APKSIGNER="$SDK_ROOT/build-tools/36.1.0/apksigner"

"$APKSIGNER" verify --print-certs "app/build/outputs/apk/release/org.fcitx.fcitx5.android-X.Y.Z-arm64-v8a-release.apk"
"$APKSIGNER" verify --print-certs "app/build/outputs/apk/release/org.fcitx.fcitx5.android-X.Y.Z-armeabi-v7a-release.apk"
"$APKSIGNER" verify --print-certs "plugin/rime/build/outputs/apk/release/org.fcitx.fcitx5.android.plugin.rime-X.Y.Z-arm64-v8a-release.apk"
"$APKSIGNER" verify --print-certs "plugin/rime/build/outputs/apk/release/org.fcitx.fcitx5.android.plugin.rime-X.Y.Z-armeabi-v7a-release.apk"
```

Also check:

```shell
find app/build/outputs/apk plugin/rime/build/outputs/apk -name "*X.Y.Z*release.apk" | sort
find app/build/outputs/apk plugin/rime/build/outputs/apk \( -name "*debug*.apk" -o -name "*unsigned*.apk" \) -print
```

Only files ending in `-release.apk` and passing `apksigner verify` belong in the
release.

## Local Baidu Staging

Create a clean versioned staging folder:

```shell
VERSION="X.Y.Z"
mkdir -p "release-baidu/v$VERSION"

cp "app/build/outputs/apk/release/org.fcitx.fcitx5.android-$VERSION-arm64-v8a-release.apk" "release-baidu/v$VERSION/"
cp "app/build/outputs/apk/release/org.fcitx.fcitx5.android-$VERSION-armeabi-v7a-release.apk" "release-baidu/v$VERSION/"
cp "plugin/rime/build/outputs/apk/release/org.fcitx.fcitx5.android.plugin.rime-$VERSION-arm64-v8a-release.apk" "release-baidu/v$VERSION/"
cp "plugin/rime/build/outputs/apk/release/org.fcitx.fcitx5.android.plugin.rime-$VERSION-armeabi-v7a-release.apk" "release-baidu/v$VERSION/"
cp "release-baidu/不懂如何安装请读.md" "release-baidu/v$VERSION/"
cp "release-baidu/不懂如何安装请读.txt" "release-baidu/v$VERSION/"
```

Before asking the user to upload, verify the staging folder contains exactly the
four release APKs and the two readme files:

```shell
find "release-baidu/v$VERSION" -maxdepth 1 -type f | sort
```

For the remote Baidu folder, delete old remote APKs and old remote readme files
before the user uploads the new staging files. Keep local old version folders
unless the user explicitly asks to remove local archives.

## GitHub Release

1. Commit the release changes.
2. Tag the release:

```shell
git tag "vX.Y.Z"
git push origin master --tags
```

3. Create the GitHub Release from `release-notes-vX.Y.Z.md`.
4. Attach only the signed release APKs:
   - App `arm64-v8a`
   - App `armeabi-v7a`
   - Rime plugin `arm64-v8a`
   - Rime plugin `armeabi-v7a`

## Final Release Checklist

- [ ] `README.md` contains only stable feature/user-manual updates.
- [ ] `release-notes-vX.Y.Z.md` contains version-specific new features and fixes.
- [ ] Release notes state the matching Rime plugin version and `rime-ice-t9-phone` tag.
- [ ] Baidu root readme Markdown and text are updated.
- [ ] `release-baidu/vX.Y.Z/` readme Markdown and text match the release.
- [ ] `Versions.kt` and `gradle.properties` use the new version.
- [ ] `RIME_CONFIG_BASELINE_VERSION` matches the documented Rime configuration tag.
- [ ] Unit tests and `git diff --check` pass or known unrelated failures are documented.
- [ ] Release APKs were built with signing properties, not debug tasks.
- [ ] `apksigner verify` passes for all four distributed APKs.
- [ ] Local Baidu staging contains exactly four APKs plus Markdown/text readmes.
- [ ] Old remote Baidu APK/readme files were deleted before manual upload.
- [ ] GitHub Release uses `release-notes-vX.Y.Z.md` and attaches only signed APKs.
