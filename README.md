# BuildDeck — Nova Studio

## BuildDeck v0.4.6 — AI Sync Inbox

The **AI Sync Inbox** on Home checks all locally GitHub-linked source projects when BuildDeck opens or returns to the foreground (with a short throttle), lists pending changed files and repository errors, and offers **Refresh** and **Review changes**. Review is explicit: the existing GitHub source-review dialog reloads the current code and requires per-file approval, conflict validation and a rollback snapshot. **Ask for APK build** is OFF by default. APK installs never happen automatically.

This is a **GitHub-based** ChatGPT workflow, not direct ChatGPT access to files on the phone. Changes must be committed to a linked GitHub repository to appear in the Inbox. ZIP-only projects must be imported from GitHub to participate. [See the v0.4.6 feature and phone test guide](docs/BATCH46_AI_SYNC_INBOX.md).


## BuildDeck v0.4.5 — Guided AI Development Workflow

**Phase 1 + Phase 2 combined:** Start with a real Android app source, generate a `builddeck.patch.v1` file grounded in that source, inspect changes inside BuildDeck, approve the edits, then build and install an updated APK. In each project's **Tools → AI development workspace**, type a request and tap **Copy AI request for ChatGPT**. The request includes your exact project name and optional linked GitHub repository, and the editor has a direct **Import AI patch for review** action. All permissions, source snapshots and build confirmations remain user-controlled.

This is an **assisted clipboard-and-patch workflow**, not a direct remote ChatGPT phone connection. You must share the source ZIP or provide an accessible linked repository; manual APK import from Downloads remains an acceptable fallback.

[Read the v0.4.5 phase 1 + 2 test instructions](docs/BATCH45_AI_WORKFLOW.md).
## BuildDeck v0.4.4 — APK Delivery & Downloads

After a successful **BuildDeck-controlled** Termux build, the app now considers the authenticated, delivered build log when verifying completion, not only callback stdout. The bundled builder still preserves `Downloads/BuildDeck/Out/` and also tries to save a readable APK in the phone's main `Downloads` folder. Every APK in the Vault now has **Save APK to Downloads**, and completed build cards can open the Vault directly.

**Manual Termux Gradle builds don't automatically populate the Vault.** Import those APKs with **Vault → + Import APK**. The direct handoff still requires a phone test and can fall back to manual import if Android blocks it.

[Read the v0.4.4 APK delivery changes and safe same-key update steps](docs/BATCH44_APK_DELIVERY.md).

**Native Android app source.** Batch 2 was built by GitHub Actions and then compiled and installed on the user's OnePlus using Termux. Batch 2.5 changes are currently tested by CI; the one-tap Termux callback and automatic APK transfer still require phone testing.


## BuildDeck v0.4.3 — Smart Build Pipeline

Nova Studio now includes **Check GitHub updates** from Home and Builds, explicit source review and approval before building, a real animated build-stage monitor (**blue while building, red on failure, brief green flash when done**), an authenticated write-only Termux APK/log/stage handoff with SHA-256 verification and Out-folder fallback, richer APK version/signing compatibility details, and filterable build history with safe bulk cleanup. Existing local builds, GitHub links, backups, saved APKs and project archives stay intact.

The new direct APK handoff is **not yet verified on the user's OnePlus** and cannot recover an older build retroactively. The APK Vault still supports manual import and File Manager → Share. See [Batch 4.3 validation and update instructions](docs/BATCH43_SMART_BUILD_PIPELINE.md).

## BuildDeck v0.4.0 — Nova Studio

The approved white-and-cobalt-blue **Nova Studio** redesign is now a native Kotlin/Jetpack Compose UI. It replaces the old oversized dark dashboard with five dedicated screens: **Dashboard, Projects, Builds, Vault, Settings**. A project opens a four-tab workspace with **Overview, Files, GitHub, Tools**. Source editing, local Termux debug builds, GitHub review, saved APKs, archives and app update safeguards remain functional. Optional dark appearance is available in Settings.

The interface only displays real build history and actual connected source information; options from the concept mockups such as production release signing, per-build percent completion, and automatic APK installation are not implemented.

[See the Nova Studio redesign and safe update instructions](docs/NOVA_STUDIO_V040.md).

## BuildDeck 0.3.4: One-project cleanup

Use **Projects → Clean up duplicates • Keep one project** to select the single real Gradle source project you want to maintain (the GitHub-linked BuildDeck is recommended). BuildDeck archives the other local project copies, hides them from Projects, preserves existing APKs in the Vault, and offers a separate confirmation to rebuild the kept project. **Archived projects (N) • Restore** reverses cleanup; no GitHub repos or external Downloads are deleted.

[Safe cleanup instructions](docs/BATCH34_ONE_PROJECT_CLEANUP.md)

## Batch 3.1: Guard against APK-as-source imports (v0.3.1)

If Build Requests says **Missing Gradle settings file (exit 6)** and the project shows `res/color/...` files rather than `settings.gradle.kts`, a compiled APK or incomplete archive was likely imported as source. **Projects → GitHub Workspace → Import GitHub source** and select the real source repository, such as `luischairez12-ui/BuildDeck`. A complete Gradle project must include its settings file and build scripts.

BuildDeck now refuses source archives without Gradle settings, disables rebuilding legacy invalid projects, and explains how to recover. This does **not** decompile APKs. [Read the v0.3.1 troubleshooting guide](docs/BATCH31_SOURCE_IMPORT_FIX.md).

## Batch 3: Reviewed GitHub-to-BuildDeck change handoff (v0.3.0)

- Update code in a GitHub repository through this ChatGPT conversation using the connected GitHub app.
- In BuildDeck, **import that same repository via GitHub Workspace**; manual ZIP-imported projects are not automatically linked.
- Open the linked project and select **Fetch changes from GitHub** after a new GitHub commit. BuildDeck compares a pinned commit against the imported baseline and offers a file-by-file review.
- Changes conflicting with locally edited files cannot be approved; remote deletions and restricted files are skipped. No unreviewed changes are silently applied.
- Approved files are applied after a rollback snapshot. BuildDeck can then show a **separate APK build confirmation** using the working Termux setup.
- You can manually share a build log from the Build Requests viewer to help diagnose issues.
- **No remote ChatGPT phone access yet.** This uses GitHub as a user-controlled source bridge with explicit approvals.
- [Full setup, limits, and safe on-phone update instructions](docs/BATCH3_GITHUB_AI_BRIDGE.md)

## Batch 2.6: multi-feature studio update

- **Automatic APK collection:** Select only `Downloads/BuildDeck/Out` in the system folder picker; BuildDeck imports APKs/logs associated with a specific build request. Manual import stays available.
- **Build dashboard:** Per-build stage markers, result callbacks, and the ability to clear obsolete requests.
- **GitHub workspace:** Import public source repositories; optionally connect a fine-grained token encrypted by Android Keystore to review and push *approved* changed text files, with remote-conflict checks. No silent synchronization.
- **Signing/version warnings:** Inspect APK package version and signing certificates when visible before installing. Retain Termux's existing debug key.
- **Batch 2.6 update:** [Read setup and local signing instructions](docs/BATCH26_STUDIO_AUTOMATION.md). The CI APK is a **fresh-install test artifact**, not an update for a Termux-signed app.
- **Still planned:** Direct authenticated AI control of a phone via an MCP service. The local AI patch-review workflow continues to require approval.

## Batch 2.5: One-tap local builds

- Uses your verified Termux paths for Java 17, SDK 35, native ARM AAPT2, and Gradle 8.9.
- Sends a bundled script to Termux only after you approve building a selected project.
- Uses a one-shot Termux result callback to show actual success/failure and command logs.
- Attempts direct APK import into the app's APK Vault; manual import remains available if Android blocks the transfer.
- Reuses Termux's debug keystore for updates compiled on the same phone; **do not replace a Termux-signed app with a GitHub Actions-signed APK**.
- No remote ChatGPT connector or GitHub sync is activated by this update.

**How to update the existing, locally signed app:** See [Batch 2.5 local builder setup and update commands](docs/BATCH25_LOCAL_BUILDER.md). Building with the same Termux debug keystore avoids uninstall/data loss. GitHub Actions APKs are for fresh test installs only.

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
