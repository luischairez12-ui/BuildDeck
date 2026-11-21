package com.builddeck.mobile

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
internal fun NovaBuildConsole(
    projects: List<Project>,
    records: List<BuildRecord>,
    outputConnected: Boolean,
    onBuild: (Project) -> Unit,
    onImportApk: (Project) -> Unit,
    onViewLog: (BuildRecord) -> Unit,
    onImportLog: (BuildRecord) -> Unit,
    onDismiss: (BuildRecord) -> Unit,
    onScan: () -> Unit,
    onConnectOutput: () -> Unit
) {
    var selectedId by remember(projects) { mutableStateOf(projects.firstOrNull()?.id) }
    var recordToClear by remember { mutableStateOf<BuildRecord?>(null) }
    val selected = projects.firstOrNull { it.id == selectedId } ?: projects.firstOrNull()
    val filtered = records.filter { selected == null || it.projectId == selected.id }
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { NovaHeader("Build console", "Compile APKs and follow real build results.") }
        item {
            NovaCard {
                Text("SELECTED PROJECT", color = NovaBlue,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                if (selected == null) NovaHint("Import an Android project before building.")
                else {
                    Text(selected.name, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    if (projects.size > 1) {
                        projects.forEach { project ->
                            FilterChip(selected = selected.id == project.id,
                                onClick = { selectedId = project.id },
                                label = { Text(project.name, maxLines = 1) })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NovaStatus("Debug APK", true)
                    NovaStatus("SDK 35 • Gradle 8.9", true)
                }
                NovaHint("The local Termux builder compiles debug APKs. Production signing and automatic install are not implemented.")
                NovaPrimaryButton("ϟ   Start build",
                    onClick = { if (selected != null) onBuild(selected) },
                    enabled = selected != null)
            }
        }
        item {
            NovaSection("Build output")
            NovaCard {
                Text(if (outputConnected) "Output folder connected" else "Output folder not connected",
                    fontWeight = FontWeight.SemiBold)
                NovaHint(if (outputConnected)
                    "Scan the approved Out folder for finished APKs."
                else "Choose Downloads/BuildDeck/Out for APK collection.")
                if (outputConnected) OutlinedButton(onClick = onScan,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Scan APK output", color = NovaBlue)
                } else OutlinedButton(onClick = onConnectOutput,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Connect output folder", color = NovaBlue)
                }
                if (selected != null) TextButton(onClick = { onImportApk(selected) }) {
                    Text("Import finished APK manually ›", color = NovaBlue)
                }
            }
        }
        item { NovaSection("Recent builds") }
        if (filtered.isEmpty()) item { NovaCard { NovaHint("No build requests for this project yet.") } }
        items(filtered.take(30), key = { it.id }) { record ->
            NovaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Build " + record.id.take(8), fontWeight = FontWeight.Bold)
                        NovaHint(novaDate(record.submittedAt))
                    }
                    NovaStatus(record.status.replace('_', ' '), record.status == "READY")
                }
                if (record.note.isNotBlank()) NovaHint(record.note.take(200))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (record.logFile != null) TextButton(onClick = { onViewLog(record) }) {
                        Text("View log", color = NovaBlue)
                    }
                    TextButton(onClick = { onImportLog(record) }) {
                        Text("Import log", color = NovaBlue)
                    }
                    TextButton(onClick = { recordToClear = record }) {
                        Text("Clear", color = NovaMuted)
                    }
                }
            }
        }
    }
    if (recordToClear != null) {
        val record = recordToClear!!
        AlertDialog(
            onDismissRequest = { recordToClear = null },
            title = { Text("Clear build record?") },
            text = { Text("This removes the history entry only. It does not stop Termux or delete any APK.") },
            confirmButton = {
                Button(onClick = { recordToClear = null; onDismiss(record) }) {
                    Text("Clear record")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToClear = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
internal fun NovaVault(
    releases: List<Release>,
    projects: List<Project>,
    onSelectProject: (String) -> Unit,
    onInstall: (Release) -> Unit,
    onImport: (Project) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val visible = releases.filter { it.name.contains(search, ignoreCase = true) }
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { NovaHeader("APK Vault", "Your private collection of finished Android apps.") }
        item {
            OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, placeholder = { Text("⌕  Search APKs") },
                shape = RoundedCornerShape(16.dp))
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NovaStatus(releases.size.toString() + " saved APKs", true)
                Spacer(Modifier.weight(1f))
                if (projects.isNotEmpty()) TextButton(onClick = { onImport(projects.first()) }) {
                    Text("+ Import APK", color = NovaBlue)
                }
            }
        }
        if (visible.isEmpty()) item {
            NovaCard {
                NovaHint(if (releases.isEmpty()) "No APKs saved. Build a project or import an APK."
                    else "No APK matches your search.")
            }
        }
        items(visible, key = { it.id }) { release ->
            val details = remember(release.path) { ApkInspector.inspect(context, release.path) }
            NovaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = NovaBlue.copy(alpha = .09f),
                        shape = RoundedCornerShape(13.dp)) {
                        Box(Modifier.size(45.dp), contentAlignment = Alignment.Center) {
                            Text("APK", color = NovaBlue, fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(release.name, fontWeight = FontWeight.SemiBold, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        NovaHint(if (details == null) novaDate(release.added)
                            else "v" + details.version + " (" + details.versionCode + ") • " +
                                novaDate(release.added))
                    }
                }
                if (details != null) Text(details.verdict,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (details.warning) NovaRed else NovaGreen)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(projects.firstOrNull { it.id == release.projectId }?.name ?: "Archived project",
                        modifier = Modifier.weight(1f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (projects.any { it.id == release.projectId })
                        TextButton(onClick = { onSelectProject(release.projectId) }) {
                            Text("Project", color = NovaBlue)
                        }
                    Button(onClick = { onInstall(release) },
                        colors = ButtonDefaults.buttonColors(containerColor = NovaBlue)) {
                        Text("Install")
                    }
                }
            }
        }
    }
}

@Composable
internal fun NovaSettings(
    outputConnected: Boolean,
    githubAccount: String?,
    darkMode: Boolean,
    onDarkMode: (Boolean) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    var showRequirements by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { NovaHeader("Settings", "Appearance, integrations, and device tools.") }
        item {
            NovaSection("Appearance")
            NovaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Dark appearance", fontWeight = FontWeight.SemiBold)
                        NovaHint("Default is the white-and-cobalt-blue Nova Studio theme.")
                    }
                    Switch(checked = darkMode, onCheckedChange = onDarkMode)
                }
            }
        }
        item {
            NovaSection("Build output")
            NovaCard {
                Text("APK collection folder", fontWeight = FontWeight.SemiBold)
                NovaHint(if (outputConnected) "Linked • Downloads/BuildDeck/Out"
                    else "Not connected • Select Downloads/BuildDeck/Out")
                OutlinedButton(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                    Text(if (outputConnected) "Change output folder" else "Connect output folder",
                        color = NovaBlue)
                }
                if (outputConnected) TextButton(onClick = onDisconnect) {
                    Text("Disconnect folder", color = NovaRed)
                }
            }
        }
        item {
            NovaSection("GitHub")
            NovaCard {
                Text(if (githubAccount == null) "No token connected"
                    else "Connected as @" + githubAccount, fontWeight = FontWeight.SemiBold)
                NovaHint("Public repository import can work without a token. Connect a token through Projects → GitHub workspace for approved source sync.")
            }
        }
        item {
            NovaSection("Developer tools")
            NovaCard {
                OutlinedButton(onClick = { showRequirements = !showRequirements },
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (showRequirements) "Hide builder requirements"
                        else "Show builder requirements", color = NovaBlue)
                }
                if (showRequirements) {
                    NovaHint("Termux requires Java 17, Gradle 8.9, SDK 35, ARM AAPT2, storage and RUN_COMMAND access.")
                    NovaHint("Keep a private backup of your Termux debug keystore for compatible updates. Never publish signing keys.")
                    NovaHint("Only compile trusted code. Production APK signing is not implemented.")
                }
            }
        }
        item {
            NovaCard {
                Text("BuildDeck v0.4.0 • Nova Studio",
                    fontWeight = FontWeight.Bold)
                NovaHint("New dashboard, workspace, build console, Vault, and a light/dark appearance toggle. GitHub review, local builds, and archived projects remain available.")
                NovaHint("Remote ChatGPT phone access is not enabled.")
            }
        }
    }
}
