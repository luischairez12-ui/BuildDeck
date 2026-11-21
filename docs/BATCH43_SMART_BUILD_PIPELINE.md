# BuildDeck v0.4.3 — Smart Build Pipeline

The approved Nova Studio design (white and cobalt blue, restored layered logo and illustrated home hero) remains unchanged. This batch improves the actual on-phone development workflow in five areas.

## 1. GitHub source check and review

- Tap **GitHub / Check updates** from the Dashboard, **Fetch changes for review** inside the project GitHub tab, or **Check GitHub updates before building** inside Builds.
- BuildDeck reads the GitHub branch and compares it with the local baseline. If already current, it reports that no supported source updates were found.
- Changed source files open a review dialog with conflict warnings and selected file controls. Changes are **not** automatically accepted or pushed.
- A rollback snapshot is saved before approved changes are applied. Building after applying requires a separate user confirmation.
- GitHub import/review restrictions remain: source-only files, max 40 reviewed file previews, remote-head recheck before writes. A repository without a saved source baseline must be imported/linked first.

## 2. Real animated build console

- **Blue pulse and indeterminate activity bar** while a recent user-approved Termux request is in SUBMITTED or RUNNING state.
- **Real stage indicators:** Preparing project, Extracting source, Compiling Android app, Packaging APK, APK ready.
- **Red error** state when the official Termux completion callback reports FAILED. Build log remains accessible.
- **Green brief flash** on a live transition from building to completed (READY or compiled and needs-import). Old successful history does not continuously animate.
- Builds inactive for over 35 minutes without a callback show an **awaiting result** warning. BuildDeck never invents a percentage or claims a build succeeded merely because a command was submitted.
- The Dashboard Recent Activity and the dedicated Builds page use the same record-backed monitor.

## 3. Direct authenticated APK transfer

Old v0.4.2 tried to upload through a FileProvider URI. Android's `/system/bin/content` implementation can execute as a different identity, so the granted FileProvider URI could reject writes. Android also did not reliably enumerate Termux-created files through the system Out folder picker.

v0.4.3 has a new narrow ContentProvider at `content://com.builddeck.mobile.handoff` for **write-only** transfers initiated by the user:
- Each approved build receives a freshly generated, random, 256-bit token bound to its UUID, an existing SUBMITTED/RUNNING request and six-hour expiry.
- The provider supports only `apk`, `log`, and `stage` entries in BuildDeck's own private results directory. It cannot list files or return file contents.
- The Termux script writes real stage updates, the finished APK, and the final log via the tokenized URLs, while **also** preserving a regular backup in `Downloads/BuildDeck/Out/<project UUID>/`.
- On the official Termux PendingIntent completion callback, BuildDeck checks exit status, success marker, transferred APK size, ZIP magic, and the SHA-256 digest printed by Termux before automatically copying the APK into Vault.
- The one-time token is revoked after the result callback.
- If Android refuses the direct transfer, BuildDeck displays SUCCESS NEEDS IMPORT; the Out folder scan and File Manager → Share to BuildDeck continue to work as fallbacks.
- The direct handoff must be verified **on this specific phone**. It cannot retroactively collect an APK from an older build that did not receive a token.
- Nothing exposes the signing key or lets a remote AI send build commands.

## 4. Version-aware APK Vault

Each APK can display:
- Package ID, APK version and versionCode.
- Installed version when visible to Android.
- Public signing-certificate comparison when possible.
- Classification: compatible update, same version, older/downgrade, different signing certificate, installed-package visibility uncertain, or unreadable APK.

An APK with a clearly mismatched certificate or downgrade is blocked by BuildDeck. Otherwise the Install/Update button **opens Android's own installation confirmation**; there is no silent installation.

Android package-visibility restrictions sometimes prevent reading installed package information for third-party apps; BuildDeck labels that as **unknown**, not automatically compatible.

## 5. Clean build history

- Filter by All, Building, Ready, Needs Import, Failed.
- Recent build's status and actual stage shown in the animated panel.
- Read or import logs as before.
- Clear individual finished history entries, or bulk clear READY/FAILED entries for the selected project **with confirmation**.
- Bulk clearing does not delete APKs, sources, snapshots, in-progress requests, or SUCCESS NEEDS IMPORT requests. Logs are also retained.

## Safe update procedure

Run this in the **same Termux installation** that signed your existing BuildDeck APK:

```bash
cd ~/builddeck-test &&
git pull --ff-only &&
grep -E 'versionCode|versionName' app/build.gradle.kts &&
grep -q 'versionName = "0.4.3"' app/build.gradle.kts &&
"$HOME/opt/gradle-8.9/bin/gradle" :app:assembleDebug --no-daemon --max-workers=2 "-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)" &&
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/BuildDeck-Nova-v0.4.3-local.apk &&
echo "BUILDDECK 0.4.3 READY!"
```

Install the new APK from normal **Downloads** as an **Update**. Do not uninstall/clear app storage; this keeps your projects and debug-key compatibility. The GitHub Actions APK is fresh-install-only if it was signed by a different key.

## Phone validation checklist

1. Confirm BuildDeck Settings says **v0.4.3** and the current one-project Nova theme remains intact.
2. In Builds, tap **Check GitHub updates** to see either the review or the already-current message.
3. Start one small test APK build with approval. Observe pulsing status and actual stage labels. On completion, confirm it flashes green; for failed Gradle builds, confirm red and readable log.
4. Check Vault: the new BuildDeck debug APK should show automatically if direct transfer works. If not, read its note and log; share the APK from the normal file manager as fallback. **Do not rename the Out folder again.**
5. Review installed-vs-APK version/signature details and ensure Android asks before installing.
6. Filter the build history; clear only expendable finished entries, keeping pending imports.
