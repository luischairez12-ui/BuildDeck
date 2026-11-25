# BuildDeck 0.4.8 — Smart Studio

## Changes
- Project list groups source versions by a literal Android application ID, falling back to normalized project names when that metadata is unavailable. Existing project IDs and files are not merged. Favorites, version selection and protection are local preferences.
- Home includes source selection, Start build, Import source ZIP immediately above the live monitor, and live log/collection actions. The existing authenticated Termux bridge and foreground-service fix remain in place.
- Smart Cleaner previews source/APK usage, keeps the newest three versions per family plus protected, linked, active/pending and last saved APK versions, and reversibly archives older sources. Archiving does not reclaim source bytes. Exact duplicate APK files can be compacted while retaining every Vault record. It does not delete source trees, user-selected external folders or Termux caches.
- APK Vault offers project collections and a configurable SAF folder to collect APKs, with SHA-256 duplicate detection and package matching against known projects; unmatched APKs are skipped. Scan is bounded to 500 entries and three nested folder levels. Automatic foreground scanning is optional and off by default.
- A separate writable SAF destination copies new Vault APKs into project-named subfolders; private Vault copies and the canonical Termux Out folder remain intact. Existing Vault entries can be exported individually. Android may restrict access to some folders. No all-files permission.

## Safe phone update
Back up important project ZIPs and the existing Termux debug keystore. Build this source using the same phone and debug signing key. Do not uninstall BuildDeck or install the differently signed GitHub Actions APK over the current local installation.

## Acceptance tests
1. Check project counts and source versions before/after update; expand OtherMe and verify separate BoreLab packages do not combine.
2. Import a new ZIP from Home. Verify it is selected and the real build monitor reports all stages. Test both success and failure.
3. Star a workspace; protect an older version. Preview Cleaner; archive older unprotected versions only, restore one, verify APK ownership and GitHub links.
4. With a backup, compact identical APK files. Check saved entries remain and an APK still installs. Confirm reclaimed space.
5. Pick an accessible collection folder containing a known APK twice and an unrelated APK. Scan; only the new matching APK should be collected once. Test permission revocation.
6. Pick a writable destination, compile a new APK and confirm both private Vault and external project subfolder copies; test manual export.
7. Confirm the v0.4.7 foreground Termux hotfix, sync inbox and source review still work.

## Limits
Source/backup folder automation, automatic deletion of old source versions, failed Termux cache cleanup and full source duplicate detection are not implemented. APK compaction only touches byte-identical app-private copies after user action. No source files are permanently removed. Android compilation and physical-device testing are required before release.
