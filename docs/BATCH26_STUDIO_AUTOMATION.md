# BuildDeck Batch 2.6 — Studio Automation

## Changes from 0.2.5

### 1. Automatic APK collection with explicit folder permission

Android prevents ordinary apps from silently browsing other apps' Download files. BuildDeck now uses the official Storage Access Framework, asking you to select **Downloads/BuildDeck/Out** just once (not the root of Downloads or Inbox). This grants read-only access only to that folder. The existing Termux script continues putting finished APKs and logs in Out.

- Open **BuildDeck → Settings → Connect output folder** and pick **Downloads → BuildDeck → Out → Use this folder**.
- The app checks build ID-specific log and APK filenames, matches them to the original project, and imports finished APKs into its private Vault. Each log is inspected for an explicit `BUILDDECK_RESULT=SUCCESS` or `FAILED` marker.
- Build Requests shows stages such as Preparing, Extracting, Compiling and Packaging. A stage is not proof of success.
- The Termux result PendingIntent remains supported; the Output folder connection provides a reliable, user-approved fallback when its FileProvider content-write handoff fails.
- On Android versions where the picker will not allow selecting this subfolder, use **+ Import built APK**. No all-files permission is requested.

### 2. Build history and cleanup

Per-request files: `Out/<project-id>/<build-record-id>.log` and `Out/<project-id>/<build-record-id>_app-debug.apk`. BuildDeck associates logs and APKs by record ID rather than guessing which APK is newest. The project Build Requests panel can clear obsolete records; clearing does not delete already imported APKs.

### 3. GitHub source import and explicitly reviewed sync

- Open **Projects → GitHub Workspace** and enter `owner/repo` (default example is the user's BuildDeck repository); leave branch blank to use its default. Import fetches a full source ZIP through GitHub's HTTPS API (100 MB compressed limit), then records original Git blob SHAs and local source fingerprints.
- Importing public repos does **not** require a GitHub login. For private repos or syncing changes, connect a **fine-grained personal access token** scoped to just the needed repositories with **Contents read/write** permission. Store it on the phone using the Android Keystore (AES-GCM). Never send the token in a chat, embed it in code, or put it in a public repo. Disconnect to erase the encrypted token.
- After editing a linked project, tap **Review changes for GitHub sync**. Select exact changed text paths, then approve. BuildDeck re-checks each remote GitHub SHA; if a remote file changed, it stops rather than overwriting the new version.
- A successful push is a set of ordinary per-file GitHub commits to the chosen branch, *not* a force push. Partial success is possible if a later file fails; relaunch review to continue.
- Current limits: text file additions/updates only (up to 30 selected per push); no file deletions, binary uploads, secret files, merges, pull requests, OAuth login, or background polling. Reimport a branch to reset its baseline; GitHub collaboration is not a full Git client.

### 4. APK signing and version inspection

BuildDeck now reads versionName/versionCode/package ID from the APK and checks installed signing certificates where Android exposes them. A different certificate warns and blocks its **Install** action instead of recommending uninstalling an app. Package visibility can limit detection of existing installs; that case is labeled unverified.

For locally signed BuildDeck updates, keep the same Termux `~/.android/debug.keystore`. Make an encrypted backup outside your device and **never** upload this file, any keystore password, or GitHub tokens to public GitHub. Production release signing and automated key management are not part of this batch.

## Local installation using your established signing identity

The earlier BuildDeck v0.2.5 on the OnePlus was compiled with Termux. Build v0.2.6 using **the same** Termux installation:

```bash
cd ~/builddeck-test
git pull --ff-only &&
"$HOME/opt/gradle-8.9/bin/gradle" :app:assembleDebug --no-daemon --max-workers=2 "-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)" &&
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/BuildDeck-v0.2.6-local.apk
```

Open Downloads and **update** the installed app. Don't uninstall; keep your projects and releases. A GitHub Actions debug APK is signed with a *different* key and should be used only for a fresh test installation, not as an update.

## Verified vs. requires device testing

- Android code compilation: GitHub Actions build validation when the workflow turns green.
- **Needs live phone testing**: Storage Access Framework folder selection, APK collection, live stage refresh, private GitHub access token, GitHub import, and GitHub source pushes.
- **Not included**: direct remote ChatGPT-to-phone connection. Per-project AI patch review remains manual.
