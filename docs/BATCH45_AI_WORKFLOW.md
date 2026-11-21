# BuildDeck v0.4.5 — Phase 1 + Phase 2 AI Workflow

## Goal

Test an actual Android app change end-to-end: **original source → approved AI patch → modified source → local Termux debug APK → installed update**. This does not require buying a server.

## What's new in BuildDeck

Inside **Projects → open project → Tools**, the **AI development workspace** now includes a short change-description editor, **Copy AI request for ChatGPT**, and **Import AI patch for review**. The copy action includes the *exact local project name* and, if the project is already linked to a GitHub repository, the corresponding repository and branch. It asks for a `builddeck.patch.v1` JSON bundle with SHA-256 source preconditions and a separate confirmed APK build.

The current source must be actually available to the AI through a shared ZIP or the linked repository. Copying the request is **not** an upload. There is **no** remote ChatGPT-to-Android connection, no unattended patch apply and no automatic APK install.

The existing permission switches, reviewed per-file diffs, conflict checks, pre-apply source snapshot and build confirmation remain unchanged.

## Recommended Test Bench Phase 1+2 demonstration

Use the original `BuildDeck_TestBench_Source.zip` (v1.0.0) from your earlier chat and keep its imported project, named `BuildDeck_TestBench_Source`. The companion `BuildDeck_TestBench_AI_v1.1.patch.json` upgrades it to **v1.1.0**. Do not import the v1.1 full-source ZIP first or the SHA preconditions will not match.

1. **Projects → BuildDeck_TestBench_Source → Tools**.
2. In **AI permissions**, turn on **Approve AI source edits**. Optionally turn on **Request AI-assisted APK builds**.
3. In the **AI development workspace**, try typing “Add a new feature that proves my test APK was changed by an AI patch,” then tap **Copy AI request for ChatGPT**. You can paste the request into ChatGPT for future feature requests. We already prepared the patch for this controlled test.
4. Tap **Import AI patch for review** and choose `BuildDeck_TestBench_AI_v1.1.patch.json`.
5. Review the 3 changed files (README, Gradle app version, Java activity) with no checksum conflicts. Tap **Apply 3 files**. BuildDeck creates a local source snapshot first.
6. Approve the separate Termux APK build from the updated imported project.
7. When the build actually succeeds, install the APK from Vault. If automatic import fails, select the APK in Downloads and manually import it into Vault.
8. Verify the installed Test Bench shows **AI PATCH APPLIED · v1.1.0**, a new **RUN AI PATCH TEST** button and **3 / 3 PASSED**. Close/reopen to verify the count survives. The Android package name is unchanged and v1.1 has a higher versionCode, so a same-key update preserves existing app data.

## Update BuildDeck safely

BuildDeck v0.4.5 has `versionCode=15`. To update your existing Termux-signed BuildDeck **without losing the app's private data**, build `main` using the same Termux installation and signing key:

```bash
cd ~/builddeck-test &&
git pull --ff-only &&
"$HOME/opt/gradle-8.9/bin/gradle" :app:assembleDebug --no-daemon --max-workers=2 "-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)" &&
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/BuildDeck-v0.4.5-local.apk
```

Install `Downloads/BuildDeck-v0.4.5-local.apk` as an **update**. Do not uninstall or clear data. GitHub Actions debug artifacts can be signed with a *different* key and are appropriate for a fresh test installation only.

## Safety and limits

- AI patches are not executed just by copying a request. Applying them requires local edit permission and a per-file review.
- An existing source ZIP/repo must be provided so edits are not fabricated; the request composer doesn't automatically upload the project.
- This new UI does not fix Android-specific direct APK transfer if the device blocks it. Manual Downloads → Vault import remains a supported fallback.
- GitHub Actions compilation verifies Gradle source/build success, not on-device permission behavior, install/signature compatibility, or the Termux handoff. Phone testing is still needed.
