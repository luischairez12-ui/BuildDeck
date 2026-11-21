# BuildDeck v0.3.4 — One Project Workspace

The Projects screen now includes **Clean up duplicates • Keep one project** when two or more projects are active.

## Intended use for the user's existing four projects

- **Keep:** `BuildDeck` — select the row marked **GitHub linked • Gradle source ready**, which contains the BuildDeck GitHub repository and its working build request.
- **Archive:** `BuildDeck-v0.3.0-local`, `BuildDeck-v0.2.6-local`, and `BuildDeck-main` (provided none of these is the only copy containing work the user wants to keep).
- **Rebuild:** Leave **Review latest GitHub changes, then offer to build** checked. After cleanup, BuildDeck checks the kept GitHub-linked project for newer upstream source changes. If changes exist, the user reviews and approves them before a separate "Run this project's build?" confirmation. If the source is already current, BuildDeck shows the build confirmation directly. No source update or build starts without review/approval.

## Safety

The word "archive" is deliberate. Source directories of the removed projects move into `filesDir/archived_projects/<id>` and are hidden from the active list, not erased. The cleanup UI requires the user to check **I reviewed the project that will be kept** before it enables the Archive action.

- Only a project containing `settings.gradle` or `settings.gradle.kts` may be kept.
- The real GitHub-linked project is recommended if one exists. The user decides which project is kept.
- The APK Vault does **not** lose its existing APKs. APK records previously attached to archived projects are reassigned to the kept project, so the Vault's View Project links work.
- Existing build logs, source snapshots, and GitHub metadata are preserved for archived projects.
- The Projects screen shows **Archived projects (N) • Restore**. Restoration moves the old source back into Projects and returns associated APK records to their original project IDs.
- No GitHub repositories, remote branches, APK signing keys, Termux SDK data, or downloads are deleted or renamed.
- This is an on-device, user-confirmed operation. Updating source on GitHub does NOT automatically remove anything from the user's installed BuildDeck.

## Update with the same Termux signing key

```bash
cd ~/builddeck-test &&
git pull --ff-only &&
grep -E 'versionCode|versionName' app/build.gradle.kts &&
grep -q 'versionName = "0.3.4"' app/build.gradle.kts &&
"$HOME/opt/gradle-8.9/bin/gradle" :app:assembleDebug --no-daemon --max-workers=2 "-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)" &&
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/BuildDeck-v0.3.4-local.apk
```

Tap the finished file in Downloads to update BuildDeck in place. Do not uninstall or install the differently signed GitHub Actions APK over it.

## What is not included

Permanent secure erasure of archived projects or automated cleanup of old downloaded APK files. Those operations are intentionally separated from simplifying the visible workspace.
