# ADR 0004: Idempotent Rime Configuration Provisioning

## Status

Accepted

## Context

The T9 product needs two Android packages, the main application and the Rime
plugin, plus the independently versioned `rime-ice-t9-phone` configuration.
Requiring users to copy an archive, deploy Rime, synchronize it, switch input
methods, or repeat those steps after an upgrade makes a valid installation look
unreliable.

Rime deployment and user-data synchronization are different operations.
Deployment compiles configuration into runtime data. Synchronization exports or
imports learned user data. Synchronization must never be a prerequisite for
making a fresh installation usable.

Android does not generally allow an ordinary application to install another APK
without system-mediated user confirmation. The configuration archive is not an
APK and belongs to this application's data directory, so it can be downloaded,
verified, installed, and activated without another confirmation.

## Decision

- The release build declares one required Rime configuration version, immutable
  archive URL, and SHA-256 digest. That tuple is the compatibility contract
  between the application release and `rime-ice-t9-phone`.
- `RimeConfigProvisioner` is the single Interface for satisfying that contract.
  Application startup and Rime plugin package changes may request provisioning;
  neither caller decides whether to download or deploy.
- Provisioning is idempotent. A healthy installation at the required version or
  a newer version performs no network request, file copy, Rime restart, or
  deployment. Missing required schemas override a stale version receipt and
  trigger repair.
- Android `DownloadManager` owns the persistent required-archive transfer. An
  app-owned single-lane observer validates the destination independently and
  adopts it into a verified cache checkpoint. This is necessary because some
  vendor builds can leave redirected downloads indefinitely pending after
  writing the complete file. A bounded direct transfer is the fallback when the
  system destination never becomes valid. Repeated IME starts are deduplicated.
- Every automatic archive is verified against its release SHA-256 before Rime
  is stopped. The archive must contain the Pinyin, Stroke, and Zhuyin T9
  schemas. Invalid or unavailable downloads leave the last working
  installation and receipt untouched.
- Installation is serialized with Fcitx lifecycle changes. The daemon cannot
  start halfway through a configuration overlay, and a previously running
  daemon is restarted exactly once after a successful or failed transaction.
- After archive validation, a durable in-progress marker is written before the
  first managed file is replaced. The version receipt is written and the marker
  cleared only after the complete overlay. A process death at any intermediate
  point therefore leaves provisioning eligible to repair the tree rather than
  treating a partial overlay as complete.
- Automatic provisioning is quiet. Network failures do not open dialogs or
  repeatedly notify the user. Manual update checks continue to report errors
  and offer application or plugin APK updates.
- Installing or upgrading the Rime plugin invalidates the native plugin data
  snapshot and restarts an active daemon. The next native start discovers the
  new plugin without requiring the user to switch system input methods.
- Rime user-data synchronization remains an explicit maintenance action. It is
  not called by installation or application upgrades.

## Upgrade Flow

1. Installing or launching a new application version compares its required
   configuration with the durable receipt and required schema files.
2. If the current configuration is healthy and compatible, startup continues
   without work.
3. Otherwise, the immutable matching archive is downloaded in the background.
   Existing healthy configuration remains usable while an upgrade downloads.
4. The completed archive is verified, overlaid while Fcitx is stopped, and
   recorded.
5. If the daemon was active, it restarts once. Librime observes changed source
   files and performs its normal maintenance automatically.

## Future Distribution

The provisioning Interface may later consume a signed compatibility manifest
with multiple mainland-accessible mirrors. Each endpoint must carry its own
digest, and the release-declared archive remains the offline-safe compatibility
floor. A remote "latest" release must never replace the required version unless
the manifest declares compatibility with the installed app and Rime plugin.

Bundling the full configuration in the APK is deliberately rejected for now:
the package would grow by tens of megabytes and duplicate an independently
released dictionary. If first-run network reliability proves insufficient, a
small bundled bootstrap dictionary can be added behind the same provisioner
without changing input or UI code.

## Consequences

Users still approve installation of the main and Rime APKs through Android.
After those package installs, configuration download, repair, activation, and
future app-matched upgrades require no manual copying, deploy action, sync
action, or input-method toggle.

GitHub remains the initial archive host, so a completely offline first install
is not guaranteed. The design keeps that transport detail behind one Module and
does not make a failed download damage an existing working configuration.
