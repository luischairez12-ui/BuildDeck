# Batch 2 — AI Control Center

## What actually works in the source

- **Per-project permissions:** Read/export, request source edits, request AI builds. All three are initially OFF. "Grant all" sets all three on; "Revoke all" disables them. This is *not* a remote API authorization grant.
- **Reviewable AI patches:** Import a `.json` file using Android's document picker, see changed file paths and abbreviated line diffs, individually select changes, enforce SHA-256 preconditions, save a rollback ZIP, then apply selected text changes.
- **Source creation, updates, and deletion:** Patch operations `write` and `delete`, limited to text files. Paths are validated and sensitive files cannot be patched.
- **Build request history:** Each successful handoff to Termux records a local build request. "Submitted" never pretends the build has completed.
- **Log import:** Import a Termux build `.log` file for a request and inspect it in the app. Only explicit `BUILDDECK_RESULT=SUCCESS` / `BUILDDECK_RESULT=FAILED` markers determine success or failure. Otherwise it says `LOG_IMPORTED`.
- **Optional local MCP prototype:** `bridge/local_mcp_bridge.py` can be used by a compatible desktop MCP client against *exported* project folders. It stages edits and exports reviewable patch JSON; it does not connect ChatGPT mobile to your phone or run builds.
- **GitHub Actions APK workflow:** A public-repo-ready `.github/workflows/android-apk.yml` builds a debug APK and makes it downloadable from Actions > Artifacts.

## Batch 2 does NOT yet provide

- A live ChatGPT-to-phone connection, remote OAuth/MCP endpoint, cloud agent, unattended AI coding or automatic builds.
- GitHub repository creation, sync, Git authentication, APK signing-key management or automatic test runs on a phone.
- An included Android SDK, Gradle wrapper JAR, or known-working native ARM Android build environment.
- Automatic import of Termux's output logs/APKs into the Android app. Logs and APKs are manually selected using Android document picker.
- Successful compilation/installation verification. The environment creating this source lacked the Android SDK and Gradle.

## Patch contract

```json
{
  "schema": "builddeck.patch.v1",
  "requestId": "example-fix-1",
  "projectName": "BoreLab Field",
  "title": "Fix calendar",
  "summary": "Repair navigation and spacing",
  "requestBuild": true,
  "operations": [
    {
      "op": "write",
      "path": "app/src/main/java/com/example/Calendar.kt",
      "expectedSha256": "absent",
      "content": "package com.example\n..."
    }
  ]
}
```

Optional `requestBuild: true` asks for a **separate build confirmation** after changes are approved, and only when AI build permission is on.

For existing files and deletes, `expectedSha256` must match SHA-256 of the original UTF-8 source. For new files, use the literal string `absent`.

Limits: <=50 operations, <=5 MB JSON, <=1 MB per text file; includes validation of each path and protection of private keys/secrets. A selected operation with any conflict cannot be applied. Builds execute Gradle scripts, so review imported changes before building.

## Prepare a patch without an MCP client

1. Export a BuildDeck project ZIP to your computer and unzip as `before/`.
2. Duplicate that directory to `after/`, make desired source edits in `after/`.
3. Run:

```sh
python tools/create_patch_bundle.py --before before --after after \
  --project-name "BoreLab Field" --title "Calendar fix" \
  --request-build --output calendar.builddeck-patch.json
```

4. Transfer the JSON to your phone. In BuildDeck open the matching project, enable "Approve AI source edits," tap "Import AI patch for review," inspect and approve.
5. You can then start a manual build, inspect its output log, and import the real APK.

## Optional desktop MCP tool bridge

In a trusted Python virtual environment, install `mcp` and set `BUILDDECK_WORKSPACE` to a folder containing your exported Android source projects. Configure `bridge/local_mcp_bridge.py` in a desktop MCP client supporting stdio. Tools: `list_projects`, `list_source_files`, `read_source`, `stage_write`, `stage_delete`, and `export_patch`. **No local server is exposed over the internet.** This is an experimental authoring handoff, not a ChatGPT connector registered in your account.

## Safety model

- Do not put API tokens, keystores, credentials, or production secrets in public repositories.
- Permission switches are local app policy, not Android OS privilege escalation.
- MCP desktop bridge operates on only the folder the user explicitly points it to; it cannot magically browse Android's private storage.
- No silent app installs or unsigned production app update claims.
- Manual approval protects against mistakes; arbitrary edited Gradle builds can still execute hostile code. Only build code you trust.
