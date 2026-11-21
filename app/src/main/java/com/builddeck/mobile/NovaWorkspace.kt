package com.builddeck.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
