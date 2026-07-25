# Debug Rime Clean-Install Validation

Use this destructive test before declaring Rime installation, provisioning, or
readiness work complete. It verifies the actual first-install path rather than
an already repaired developer workspace.

## Scope

The user has authorized deletion of these debug-only packages and their data:

- `org.fcitx.fcitx5.android.debug`
- `org.fcitx.fcitx5.android.plugin.rime.debug`

Never clear or uninstall the release packages. Always pass the explicit physical
device serial to every ADB command.

## Reset

1. Build matching debug app and Rime plugin APKs from the same source revision.
2. Record the physical device serial and confirm it with `adb devices -l`.
3. Clear both debug packages before uninstalling them:

   ```bash
   adb -s "$SERIAL" shell pm clear org.fcitx.fcitx5.android.debug
   adb -s "$SERIAL" shell pm clear org.fcitx.fcitx5.android.plugin.rime.debug
   adb -s "$SERIAL" uninstall org.fcitx.fcitx5.android.debug
   adb -s "$SERIAL" uninstall org.fcitx.fcitx5.android.plugin.rime.debug
   ```

4. Remove scoped-storage remnants only for the two debug package names:

   ```bash
   adb -s "$SERIAL" shell rm -rf \
     /sdcard/Android/data/org.fcitx.fcitx5.android.debug \
     /sdcard/Android/data/org.fcitx.fcitx5.android.plugin.rime.debug
   ```

5. Verify that `pm path` returns no path for either debug package and that both
   scoped-storage directories are absent.

## Fresh Installation

1. Clear logcat.
2. Install the debug app APK, then the matching debug Rime plugin APK.
3. Enable and select the debug IME through Android settings.
4. Do not copy Rime files, deploy manually, synchronize, or switch to another
   system IME to repair state.
5. Open a new text field and allow the automatic first-install download,
   overlay, and native compilation to finish.

## Required Rime Scenarios

1. While the first initialization is still preparing, request Stroke mode and
   press a T9 digit. The UI may show a temporary preparing state, but it must
   recover automatically into Stroke without losing the requested scheme.
2. Switch back to Pinyin and enter a known phrase. Candidates and composition
   must work without manual deployment or an IME toggle.
3. Restart the debug host process and reopen a fresh text field. The compiled
   workspace must remain usable; startup may perform a short source check but
   must not enter permanent deployment.
4. Repeat Pinyin, Stroke, and Zhuyin switching. No scheme request may be lost
   while Rime is transitioning.
5. Inspect logcat for uncaught exceptions, repeated provisioning, stale
   `Deploying` state after native `Ready`, LevelDB lock failures, or retry
   exhaustion.

## Global Physical Key Sound Scenario

1. Open **Full physical key sounds** in key-feedback settings and enable its
   Android accessibility service.
2. Leave every text field and test Back, Menu/App switch, Call, and End call.
   Each key should play one selected key-pack sound while retaining its original
   action.
3. Test digit and volume keys outside a text field. Their existing feedback
   should remain single, with no duplicate sound.

Keep the clean-install log and exact APK commit with the final verification
record. A test performed against retained debug Rime data does not satisfy this
runbook.
