package com.builddeck.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun NovaWorkspace(
    project: Project,
    filePaths: List<String>,
    releases: List<Release>,
    snapshots: List<Snapshot>,
    access: AiAccess,
    buildRecords: List<BuildRecord>,
    repoLink: RepoLink?,
    sourceReady: Boolean,
    outputConnected: Boolean,
    onBuild: () -> Unit,
    onFile: (String) -> Unit,
    onFetch: () -> Unit,
    onSync: () -> Unit,
    onImportApk: () -> Unit,
    onScan: () -> Unit,
    onConnectOutput: () -> Unit,
    onOpenGitHubImport: () -> Unit,
    onSaveSnapshot: () -> Unit,
    onRestore: (Snapshot) -> Unit,
    onExport: () -> Unit,
    onInstall: (Release) -> Unit,
    onViewLog: (BuildRecord) -> Unit,
    onImportLog: (BuildRecord) -> Unit,
    onDismissBuild: (BuildRecord) -> Unit,
    onAccessChanged: (AiAccess) -> Unit,
    onImportPatch: () -> Unit
) {
    var tab by remember(project.id) { mutableStateOf("Overview") }
    var fileSearch by remember(project.id) { mutableStateOf("") }
    var aiChangeRequest by remember(project.id) { mutableStateOf("") }
    val context = LocalContext.current
    val filteredFiles = remember(filePaths, fileSearch) {
        filePaths.filter { it.contains(fileSearch, ignoreCase = true) }
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { NovaBanner(project, repoLink) { tab = "Overview" } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("Overview", "Files", "GitHub", "Tools").forEach { label ->
                    FilterChip(selected = tab == label,
                        onClick = { tab = label },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NovaBlue,
                            selectedLabelColor = androidx.compose.ui.graphics.Color.White))
                }
            }
        }
        when (tab) {
            "Overview" -> {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        NovaTile("⌘", "Source", filePaths.size.toString() + " files",
                            Modifier.weight(1f)) { tab = "Files" }
                        NovaTile("⇅", "GitHub", repoLink?.branch ?: "Not linked",
                            Modifier.weight(1f)) { tab = "GitHub" }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        NovaTile("◈", "Releases", releases.size.toString() + " APKs",
                            Modifier.weight(1f), onImportApk)
                        NovaTile("⚙", "Tools", "Backups & AI", Modifier.weight(1f)) {
                            tab = "Tools"
                        }
                    }
                }
                item { NovaPrimaryButton("ϟ   Build APK", onBuild, enabled = sourceReady) }
                if (!sourceReady) item {
                    NovaCard {
                        Text("Source import needs repair", color = NovaRed,
                            fontWeight = FontWeight.Bold)
                        NovaHint("This project has no Gradle settings file. Import the real GitHub source instead of a compiled APK.")
                        OutlinedButton(onClick = onOpenGitHubImport) {
                            Text("Go to source import", color = NovaBlue)
                        }
                    }
                }
                item {
                    NovaSection("Latest build")
                    NovaCard {
                        val last = buildRecords.firstOrNull()
                        if (last == null) NovaHint("No builds have been submitted.")
                        else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Build " + last.id.take(8),
                                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                NovaStatus(last.status.replace('_', ' '), last.status == "READY")
                            }
                            NovaHint(last.note.ifBlank { novaDate(last.submittedAt) })
                            if (last.logFile != null) TextButton(onClick = { onViewLog(last) }) {
                                Text("View build log ›", color = NovaBlue)
                            }
                        }
                    }
                }
            }
            "Files" -> {
                item {
                    NovaSection("Source files")
                    NovaHint("Tap a file to edit its source. Changes are included in your next build.")
                    OutlinedTextField(value = fileSearch, onValueChange = { fileSearch = it },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("⌕  Find a file or folder") },
                        shape = RoundedCornerShape(16.dp))
                    NovaHint(filteredFiles.size.toString() + " matching files")
                }
                if (filteredFiles.isEmpty()) item { NovaCard { NovaHint("No matching source files.") } }
                items(filteredFiles.take(300), key = { it }) { path ->
                    NovaCard(Modifier.fillMaxWidth().clickable { onFile(path) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("‹/›", color = NovaBlue, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(11.dp))
                            Text(path, modifier = Modifier.weight(1f), maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall)
                            Text("Edit ›", color = NovaBlue,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (filteredFiles.size > 300) item {
                    NovaHint("Showing 300 files. Narrow your search for more.")
                }
            }
            "GitHub" -> {
                item {
                    NovaSection("GitHub workspace")
                    if (repoLink != null) {
                        NovaCard {
                            Text(repoLink.owner + "/" + repoLink.repo,
                                fontWeight = FontWeight.Bold)
                            NovaHint("Branch: " + repoLink.branch)
                            NovaPrimaryButton("⇩   Fetch changes for review", onFetch)
                            OutlinedButton(onClick = onSync,
                                modifier = Modifier.fillMaxWidth()) {
                                Text("Review local changes to push", color = NovaBlue)
                            }
                            NovaHint("Remote changes and uploads always require your approval. This is not unattended ChatGPT access.")
                        }
                    } else {
                        NovaCard {
                            NovaHint("This local project is not linked to GitHub.")
                            NovaPrimaryButton("Import from GitHub", onOpenGitHubImport)
                        }
                    }
                }
            }
            "Tools" -> {
                item {
                    NovaSection("Source backups")
                    NovaCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onSaveSnapshot,
                                modifier = Modifier.weight(1f)) {
                                Text("Save version", color = NovaBlue)
                            }
                            OutlinedButton(onClick = onExport,
                                modifier = Modifier.weight(1f)) {
                                Text("Export ZIP", color = NovaBlue)
                            }
                        }
                        if (snapshots.isEmpty()) NovaHint("No saved versions yet.")
                        snapshots.take(12).forEach { snapshot ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(novaDate(snapshot.created), modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { onRestore(snapshot) }) {
                                    Text("Restore", color = NovaBlue)
                                }
                            }
                        }
                    }
                }
                item {
                    NovaSection("AI development workspace")
                    NovaCard {
                        Text("Describe it. Review it. Build it.",
                            fontWeight = FontWeight.Bold)
                        NovaHint("1. Describe your change below, then copy the request into ChatGPT. " +
                            "2. Share your current source ZIP or use your connected GitHub project. " +
                            "3. Import the AI-generated JSON patch below and approve each file before building.")
                        OutlinedTextField(
                            value = aiChangeRequest,
                            onValueChange = { aiChangeRequest = it.take(1500) },
                            label = { Text("What should AI change?") },
                            placeholder = { Text("Example: Add a new button that tracks a saved counter") },
                            minLines = 3, maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Button(
                            onClick = {
                                val change = aiChangeRequest.trim()
                                if (change.isNotEmpty()) {
                                    val reference = if (repoLink == null) {
                                        "GitHub: not linked. I will attach a source ZIP exported from BuildDeck."
                                    } else {
                                        "GitHub source: https://github.com/${repoLink.owner}/${repoLink.repo} " +
                                            "(branch ${repoLink.branch}). Use the current source on that branch."
                                    }
                                    val message = "Please make this Android project change for me.\\n\\n" +
                                        "BuildDeck project name (exact): ${project.name}\\n" +
                                        reference + "\\n\\n" +
                                        "Requested change: \\n" + change + "\\n\\n" +
                                        "Use my actual current source, not a guessed project. " +
                                        "Prepare a downloadable BuildDeck builddeck.patch.v1 JSON with " +
                                        "projectName exactly equal to ${project.name}, per-file SHA-256 " +
                                        "preconditions (or absent for new files), a clear summary, and " +
                                        "requestBuild true. Explain any limitations. I will review and " +
                                        "approve changes in BuildDeck and build the APK on my phone."
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as? ClipboardManager
                                    if (clipboard != null) {
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText("BuildDeck AI change request", message))
                                        Toast.makeText(context, "AI request copied — paste it into ChatGPT",
                                            Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Clipboard unavailable",
                                            Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = aiChangeRequest.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Copy AI request for ChatGPT") }
                        OutlinedButton(onClick = onImportPatch,
                            enabled = access.write,
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Import AI patch for review", color = NovaBlue)
                        }
                        if (!access.write) {
                            NovaHint("Enable Approve AI source edits in the permissions below to review a patch.")
                        }
                        NovaHint("No remote phone access or automatic AI edits. You control the ZIP, " +
                            "the patch, and the final build approval.")
                    }
                }
                item { NovaSection("AI permissions") }
                item { AiProjectControls(access, onAccessChanged, onImportPatch) }
                item {
                    NovaSection("APK tools")
                    NovaCard {
                        OutlinedButton(onClick = onImportApk,
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Import finished APK", color = NovaBlue)
                        }
                        if (outputConnected) OutlinedButton(onClick = onScan,
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Scan Out folder", color = NovaBlue)
                        } else OutlinedButton(onClick = onConnectOutput,
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Connect output folder", color = NovaBlue)
                        }
                    }
                }
                item {
                    BuildRequestHistory(buildRecords, onImportLog, onViewLog, onDismissBuild)
                }
                if (releases.isNotEmpty()) item {
                    NovaSection("Project releases")
                    NovaCard {
                        releases.take(10).forEach { release ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(release.name, modifier = Modifier.weight(1f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { onInstall(release) }) {
                                    Text("Install", color = NovaBlue)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
