# BuildDeck Batch 3 — GitHub AI Bridge (v0.3.0)

## What is working in this batch

A first version of the AI-to-BuildDeck **source-code handoff**. ChatGPT can update code in a GitHub repository that is already connected to the user's ChatGPT account. BuildDeck, using the user's GitHub access, can fetch those commits, let the user inspect and select files, save a rollback snapshot, apply the changes locally, and **ask** to run the Termux builder. A build never launches solely because GitHub changed.

This is NOT direct remote control of BuildDeck or of the phone. There is no server, push notification endpoint, cloud MCP service, or background file access. ChatGPT's GitHub access and BuildDeck's optionally saved GitHub token are independent.

### Start using it

1. Install v0.3.0 by rebuilding in the **same Termux** as the currently installed v0.2.6, preserving `~/.android/debug.keystore`.
2. Open BuildDeck → Projects → **GitHub Workspace**.
3. Import `luischairez12-ui/BuildDeck` (or another authorized source repository) from GitHub. Leave branch empty to use its default. This makes a **new linked project**, distinct from any older manually imported ZIP project.
4. Open the imported project. The **Linked GitHub Repository** card now includes **Fetch changes from GitHub**.
5. Make or request a source-code change to the same GitHub repo through ChatGPT. The change should be committed and visible on the chosen branch.
6. In BuildDeck, tap **Fetch changes from GitHub**. The app pins a single Git commit and compares remote blob identifiers to its saved baseline.
7. Review incoming file paths, open code previews, and check files to apply. Files conflicting with local edits are unselectable. Remote deletions, secret files and binary files are not applied.
8. Approve. BuildDeck rechecks the branch head and local file fingerprints, saves a full source ZIP snapshot, then writes only selected files.
9. If you checked **Ask me to confirm an APK build after applying**, approve the separate build prompt. Termux compiles the project; the existing BuildDeck output collector imports its APK when possible.
10. To share compilation errors back to ChatGPT, open **Build Requests → Read build log / errors → Share log** and explicitly choose where to share it. Review logs before sharing because they may contain private paths or source data.

### GitHub authentication

- For a public repository, import and incoming fetch may work **without a BuildDeck token**.
- For private repositories or pushing local source changes to GitHub, use a fine-grained GitHub personal access token limited to the required repository and **Contents read/write**. The token is encrypted locally using Android Keystore. Do not paste it into ChatGPT.
- No developer server or GitHub OAuth app is required for this first bridge.

### Safety / limits

- No auto-accepting GitHub code or running its build scripts; user must review code and confirm builds.
- Only restricted UTF-8 text source files up to 1 MB are supported, with a 40-file incoming preview and maximum 150 upstream changed paths. More substantial changes require fresh import or later improved handling.
- Remote files outside the allowlist, large files, binary assets, dot-env files, signing credentials, and upstream deletions are not applied. They are counted as skipped, not silently merged.
- If GitHub changes after review, or a local file changes, applying stops and asks for a refresh.
- Import/push still uses the previous Batch 2.6 features; approved incoming changes do not automatically push back to GitHub.
- Individual source snapshots survive failed applies for manual restore.
- Debug APKs compiled by GitHub Actions are signed by its own debug key; do NOT install them over the Termux-signed BuildDeck app.

## Local update command

Use Termux from the **same** installation that built your currently installed BuildDeck:

```bash
cd ~/builddeck-test
git pull --ff-only &&
"$HOME/opt/gradle-8.9/bin/gradle" :app:assembleDebug --no-daemon --max-workers=2 "-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)" &&
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/BuildDeck-v0.3.0-local.apk
```

Once `BUILD SUCCESSFUL`, open `Downloads/BuildDeck-v0.3.0-local.apk` and update (do not uninstall). If Android reports a signing conflict, stop; do not delete the app.

## Still to build later

A true authenticated MCP bridge / remote ChatGPT app connection, optional secure sync of build diagnostics, multi-branch merge conflicts, GitHub OAuth sign-in, foreground build notifications, and automatic server-initiated actions. Those require separate design and testing.
