# BuildDeck — Batch 2 AI Control Center

**Native Android app source, not a prebuilt APK.** Updated from Batch 1. This source has not yet been compiled or run on a physical Android device in the authoring environment.

## What's new in Batch 2

- Permission panel for each imported project: AI read/export, source-edit, and build requests.
- Manual AI `.json` patch import, per-file diff preview, selection and approval.
- SHA-256 conflict checks, 50-file/5-MB safety limits, source snapshot before apply.
- Create/update/delete text source files through approved patch requests.
- Persistent build-request queue and importable build logs, with explicit success/fail markers.
- Optional **local** Python MCP handoff prototype for exported source folders. **ChatGPT is NOT connected automatically.**
- GitHub Actions debug APK builder, ready for a newly created public repository.

## Build a test APK from GitHub

1. Go to https://github.com/new and create a repository called `BuildDeck`, visibility **Public** (only after reviewing code and checking for secrets). You must create it in GitHub; this source package does not create a repo.
2. Upload **the contents of this `BuildDeck` folder**, preserving the `.github/workflows/android-apk.yml` path. Do not upload the enclosing ZIP as a single repo file. The top-level repo should contain `settings.gradle.kts`, `app/`, and `.github/`.
3. Open the repo's **Actions** tab, choose `BuildDeck Android Debug APK`, then **Run workflow** if no push run started.
4. Once a run **succeeds**, open the run and download `builddeck-batch2-debug-apk` under Artifacts. Unzip and install the `app-debug.apk` on your test device. It may require permission to install unknown apps.
5. If the workflow fails, share its Gradle error log for a targeted fix. Do not mistake a ZIP of source for an APK.

The included GitHub Actions job uses JDK 17, Gradle 8.9, Android API 35 and AGP 8.7.3, matching Android's compatibility requirements. GitHub Actions is the easiest reliable first-build path; **on-phone building through Termux remains experimental**.

## Run through Android Studio

Open this source directory as a Gradle project. Build `:app:assembleDebug` with compatible Gradle 8.9, Android SDK 35, and JDK 17. Application ID: `com.builddeck.mobile`; minSdk 29. This package intentionally does not contain a downloaded Gradle wrapper JAR, third-party secrets, or a release signing key.

## AI review workflow

1. Import an Android Gradle project ZIP in BuildDeck.
2. Open its **AI Developer Control** panel. Only enable the permissions you need.
3. Create a `builddeck.patch.v1` JSON bundle with `tools/create_patch_bundle.py` or the optional local MCP handoff server; see [detailed documentation](docs/BATCH2_AI_CONTROL.md).
4. Import the JSON, inspect the diff and checksums, select the files you approve, apply with automatic rollback snapshot.
5. Start an on-phone Termux build (if fully configured), import its `.log` to view result, and import the APK into your vault. Alternatively use GitHub Actions to build this or any separately published project.

**No live remote ChatGPT integration exists in this batch.** A future authenticated remote MCP bridge could enable AI interaction with the phone, subject to a safe approval model and a network endpoint. No one, including ChatGPT, automatically gets remote access from enabling the three permission switches.

## Security

- All project files, snapshots, build records, access settings, and imported logs are app-private by default.
- Only manually selected files leave the device via Android's system document picker, plus explicitly initiated Termux shared-storage build handoffs.
- Patch imports reject traversals, conflicts, risky secret file extensions, oversized inputs and overwrites without source fingerprints.
- Local project source exports deliberately skip `local.properties`, `.env`, private keys and keystores.
- **Public GitHub repos are visible to everyone.** Review every file before publishing.
- Gradle build scripts can execute code. Never build untrusted source.
- Use a safe backup/export strategy before reinstalling or resetting the app.

See [Batch 2 detailed spec](docs/BATCH2_AI_CONTROL.md), [roadmap](ROADMAP.md), and [verification notes](VERIFY_NOTES.md).
