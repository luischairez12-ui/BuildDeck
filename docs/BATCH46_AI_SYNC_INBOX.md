# BuildDeck v0.4.6 — AI Sync Inbox

## What this actually does

When BuildDeck opens or returns to the foreground, the Home screen's **AI Sync Inbox** checks the GitHub branches associated with all **active locally linked projects**. A foreground throttle (3 minutes) avoids repeated API requests when users switch apps. **Refresh** triggers another immediate check. This is foreground read-only discovery, not a background monitoring service, a remote phone connector, or a push notification service.

The Inbox uses the same `GitHubIncoming.check()` algorithm as the existing manual **Fetch changes for review** action:
- Identifies remote GitHub commits and changed file paths relative to the project's saved GitHub baseline.
- Displays each linked project's changed-file count, the first few filenames, and conflicts/unsupported files.
- Surfaces errors per repository so a failure for one project does not hide other projects' updates.
- Shows a clean state only after a completed check returns no pending files, and offers a **Refresh** button.
- Handles linked public repositories and private repositories accessible with the app's scoped GitHub token.

The Inbox never imports code without approval. **Review changes** explicitly fetches a fresh review with the current branch state, then uses the existing per-file source preview and checkbox dialog. `GitHubIncoming.apply()` rechecks the branch head, SHA-256 local fingerprints, incoming blob IDs, and source permissions; a local rollback snapshot is saved before writing changed files.

**Ask me to confirm an APK build after applying** is OFF by default. Even if turned ON, BuildDeck shows a second confirmation before starting Termux. Installing the produced APK remains an Android user-confirmed action. No app is automatically installed.

The term **AI** is the feature's name: GitHub changes could come from ChatGPT or any repository collaborator. BuildDeck does not certify who authored each commit.

## Typical flow with ChatGPT

1. Once per Android project, import its **GitHub source** in BuildDeck's Projects tab. The imported workspace carries the repo and branch binding.
2. In ChatGPT, say **“Fix [specific issue] in [repository]”**. With connected repository access, ChatGPT can inspect the real GitHub code, make reviewed code changes, and commit the resulting changes to that repository.
3. Open BuildDeck. The **AI Sync Inbox** on Home checks linked projects and shows their pending GitHub changes, with warnings about conflicts or unsupported files.
4. Tap **Review changes**, read and individually approve each proposed source file.
5. Choose whether to request a separate APK build confirmation. Applying changes alone doesn't compile or install.
6. Build using the existing Termux flow. If automatic Vault import doesn't work on the phone, import the APK from Downloads manually. Install only with Android's installer confirmation.
7. If you edit files locally in BuildDeck, sync those changes back to GitHub before asking ChatGPT to work from the remote source; otherwise remote state won't include your unpublished local edits.

## Important limitations

- Only active **GitHub-linked** projects are checked. ZIP-only imported projects have no remote source to monitor until they're linked via GitHub import.
- Checking is on app open/foreground, not continuous background polling. Internet access is required; errors remain visible in the Inbox.
- Existing safe review limits remain in force (at most 40 previewed supported file changes per check; other changed files are reported as skipped). Unsupported files and deletions are never silently applied.
- This is **not** direct ChatGPT access to private files stored on the phone; GitHub acts as the source synchronization bridge.
- BuildDeck's native source was compiled via GitHub Actions before the change was merged; the precise phone-facing behavior and Android permissions require an on-device check.
- The signed GitHub Actions debug APK may not update a BuildDeck app built with your Termux signing key. Update with the **same Termux debug key**.

## Safe local update

For the existing Termux checkout and signing environment:

```bash
cd ~/builddeck-test &&
git pull --ff-only &&
"$HOME/opt/gradle-8.9/bin/gradle" :app:assembleDebug --no-daemon --max-workers=2 "-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)" &&
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/BuildDeck-v0.4.6-local.apk
```

Install **Downloads/BuildDeck-v0.4.6-local.apk** as an update; do not uninstall or wipe data. The update uses `versionCode=16`.

## On-device acceptance checks

1. Update BuildDeck v0.4.6; verify Settings shows the correct version.
2. If no project is GitHub-linked, the Inbox shows **Connect GitHub project** rather than falsely claiming the repository is current.
3. Import or open your linked BuildDeck project. Open Home; the Inbox performs a read-only scan and displays a check time.
4. Have ChatGPT make a small GitHub source change in that repository. Return to BuildDeck or tap Refresh; the Inbox should display the pending files.
5. Tap Review changes; check the source and choose the changes you approve. Confirm a source snapshot is saved.
6. Leave **Ask me to confirm an APK build after applying** unchecked; applying should update the source without automatically building.
7. Build manually if wanted; verify the APK has the expected change. Leave manual Downloads → Vault import as an acceptable fallback.
8. Confirm network errors and repository conflicts are identified without overwriting local source.
