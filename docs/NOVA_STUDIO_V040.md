# BuildDeck v0.4.0 — Nova Studio

Nova Studio turns BuildDeck from a long dark prototype screen into a white-and-cobalt-blue Android development studio, based on the approved concept mockup.

## Navigation

- **Home / Dashboard:** active source project, large Build APK button, Projects, GitHub, APK Vault, Builds shortcuts and live recent-build/release information.
- **Projects:** searchable source projects, active/archived counts, reversible duplicate cleanup, source ZIP import, expandable GitHub workspace.
- **Project Workspace:** an actual tabbed interface: Overview, Files, GitHub, Tools. Includes file search/edit, GitHub fetch/push review, local builds, snapshots, export, AI permissions, scan output, APK import, release access, and log history.
- **Builds:** dedicated on-phone build console for Termux debug builds, real build statuses and logs, output-folder diagnostics, import finished APK and build history.
- **Vault:** searchable private APK library, version/signature compatibility assessments and the existing protected installer. **Install** opens the system APK installer; Nova Studio never silently installs.
- **Settings:** output folder, GitHub token status, expandable builder information, and persistent light/dark theme toggle. The default design is bright pearl white and cobalt blue.

## What we preserved

- The same app ID, local Termux debug-signing identity, local project storage, archive/restore, GitHub repository link and credentials, APK Vault data and build requests.
- GitHub-to-phone source changes require review/approval. Builds require a separate confirmation.
- Existing Termux bridge, exact APK handoff fallback and file-manager share-to-Vault support.
- A full native Android UI built with Kotlin and Jetpack Compose.
- No server, direct ChatGPT phone control, paid services, or unrestricted file permissions added.

## Feature honesty

- Build type is **debug**. Production signing, release-mode optimization toggles, automatic install and accurate percent build progress are **not** implemented. The concept mockup included aspirational examples; the app only shows and executes currently supported features.
- Build statuses are actual persisted Termux/BuildDeck reports, not artificially simulated.
- The mockup is a design reference, not a pixel-identical screenshot of v0.4.0. The native UI follows the approved colors, hierarchy, card styling and navigation.

## Update without losing data

Open the same Termux installation that signed your previously installed BuildDeck:

```bash
cd ~/builddeck-test &&
git pull --ff-only &&
grep -E 'versionCode|versionName' app/build.gradle.kts &&
grep -q 'versionName = "0.4.0"' app/build.gradle.kts &&
"$HOME/opt/gradle-8.9/bin/gradle" :app:assembleDebug --no-daemon --max-workers=2 "-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)" &&
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/BuildDeck-Nova-v0.4.0-local.apk &&
echo "NOVA STUDIO APK READY"
```

Then open `Downloads/BuildDeck-Nova-v0.4.0-local.apk` and choose **Update**, not uninstall. The GitHub Actions APK uses a different debug key and is fresh-install-only for this user.

## First-device smoke test

1. Launch app: white/cobalt Home dashboard, your archived older projects should remain archived.
2. Projects: one surviving BuildDeck project, with GitHub source accessible.
3. Project Workspace: try Overview, Files search, GitHub tab and Tools.
4. Builds: latest build request and log; scan Out. No new APK build required to test navigation.
5. Vault: old APKs and compatibility warnings still appear.
6. Settings: switch dark theme on and off; return to white by default.
7. Run **Build APK** from Home or Builds; separately confirm Termux. Use the existing signing key to install.
