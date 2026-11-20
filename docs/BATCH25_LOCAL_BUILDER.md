# BuildDeck Batch 2.5 — One-Tap Local Builder

BuildDeck can **request** a build on the user's Android phone, via Termux RunCommandService. The app never gives ChatGPT control of Termux and does not expose a network server.

## Tested on the user's OnePlus / Termux setup (manually verified)

- Termux Java 17, Android SDK API 35, Android build-tools 35.0.0
- `~/opt/gradle-8.9/bin/gradle` (verified Gradle 8.9)
- native ARM `aapt2` from Termux packages
- `termux-setup-storage` completed
- `allow-external-apps=true` in `~/.termux/termux.properties`
- Android granted BuildDeck `com.termux.permission.RUN_COMMAND`
- `BuildDeck` source compiled successfully in Termux (manual build)

## Batch 2.5 workflow

1. In BuildDeck, import a complete **source project ZIP** (not an APK artifact).
2. Open the project and tap **BUILD APK ON THIS PHONE**, then approve the warning.
3. BuildDeck exports the source into `Downloads/BuildDeck/Inbox`.
4. Termux receives the **bundled** shell builder via stdin and runs Gradle 8.9 when the source has no wrapper; if it includes a wrapper, that project controls the Gradle version.
5. Termux builds with `-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)`.
6. Termux writes a debug APK and log to `Downloads/BuildDeck/Out/<project id>/`.
7. An explicit, one-shot PendingIntent returns the build's exit status and command output to a private BuildDeck receiver. BuildDeck attempts to import the APK and full log into its private Vault through temporary, write-granted FileProvider URIs. **The FileProvider transfer requires real-device validation.**
8. If automatic transfer fails, the build status becomes `SUCCESS_NEEDS_IMPORT`; use the manual APK import from Downloads.
9. Failure logs can be inspected under the project's **Build Requests**.

The RUN_COMMAND result callback requires a recent Termux build (v0.109+). Termux's command result streams may be truncated. Avoid killing Termux during active builds. Don't execute untrusted Gradle projects.

## Updating BuildDeck without uninstalling

The user's installed version was built **inside the same Termux installation**, so future Termux debug builds normally reuse `~/.android/debug.keystore` (same signing identity). On the phone:

```bash
cd ~/builddeck-test
git pull --ff-only
"$HOME/opt/gradle-8.9/bin/gradle" :app:assembleDebug --no-daemon --max-workers=2 "-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)"
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/BuildDeck-v0.2.5-local.apk
```

Tap the downloaded APK to **update** your existing installed BuildDeck. Confirm that the version code is newer. Do not install a GitHub Actions debug artifact on top of a locally signed version; CI uses a different debug key. Never upload keystores or credentials to GitHub.

### Preserve signing identity

`~/.android/debug.keystore` is in Termux's private app storage. Reinstalling Termux can destroy it. Keep a secured, preferably encrypted off-device backup, and do not publish this key. Production APK signing (using a long-lived release key) is a future feature.

## Limitations

- Not a direct ChatGPT-to-phone connection; AI source changes still require manual review.
- BuildDeck has no GitHub sign-in or automatic GitHub upload in this batch.
- APK delivery via Android FileProvider + `/system/bin/content write` is best-effort, and is unverified on the actual phone.
- Builds using incompatible Gradle/AGP/Kotlin versions or non-ARM executables can fail.
- A build status is only successful if Termux's callback reports exit code 0 and the script emitted `BUILDDECK_RESULT=SUCCESS`.
