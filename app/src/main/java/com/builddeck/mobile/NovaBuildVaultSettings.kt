package com.builddeck.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
    githubLinkedProjects: Set<String>,
    onBuild: (Project) -> Unit,
    onCheckGitHub: (Project) -> Unit,
    onImportApk: (Project) -> Unit,
    onViewLog: (BuildRecord) -> Unit,
    onImportLog: (BuildRecord) -> Unit,
    onDismiss: (BuildRecord) -> Unit,
    onClearCompleted: (Project) -> Unit,
    onScan: () -> Unit,
    onConnectOutput: () -> Unit,
    onOpenVault: () -> Unit
) {
    val context = LocalContext.current
    var selectedId by remember(projects) { mutableStateOf(projects.firstOrNull()?.id) }
    var statusFilter by remember { mutableStateOf("All") }
    var recordToClear by remember { mutableStateOf<BuildRecord?>(null) }
    var confirmBulkClear by remember { mutableStateOf(false) }
    val selected = projects.firstOrNull { it.id == selectedId } ?: projects.firstOrNull()
    val projectRecords = records.filter { selected == null || it.projectId == selected.id }
    val active = projectRecords.count { it.status in setOf("SUBMITTED", "RUNNING") }
    val completed = projectRecords.count { it.status == "READY" }
    val failed = projectRecords.count { it.status == "FAILED" }
    val waitingImport = projectRecords.count { it.status == "SUCCESS_NEEDS_IMPORT" }
    val newest = projectRecords.firstOrNull()
    val filtered = projectRecords.filter { record ->
        when (statusFilter) {
            "Building" -> record.status in setOf("SUBMITTED", "RUNNING")
            "Ready" -> record.status == "READY"
            "Needs import" -> record.status == "SUCCESS_NEEDS_IMPORT"
            "Failed" -> record.status == "FAILED"
            else -> true
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { NovaHeader("Build console", "Real-time Android builds, without confusing terminal output.") }
        item {
            NovaCard {
                Text("SELECTED PROJECT", color = NovaBlue,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                if (selected == null) NovaHint("Import a source project first.")
                else {
                    Text(selected.name, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    if (projects.size > 1) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            projects.forEach { project ->
                                FilterChip(selected = selected.id == project.id,
                                    onClick = { selectedId = project.id; statusFilter = "All" },
                                    label = { Text(project.name, maxLines = 1) })
                                Spacer(Modifier.width(5.dp))
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NovaStatus("Termux • Debug APK", true)
                    NovaStatus("Gradle 8.9", true)
                }
                NovaPrimaryButton("ϟ   Start build",
                    onClick = { if (selected != null) onBuild(selected) },
                    enabled = selected != null)
                if (selected != null && selected.id in githubLinkedProjects) {
                    OutlinedButton(onClick = { onCheckGitHub(selected) },
                        modifier = Modifier.fillMaxWidth()) {
                        Text("⇅  Check GitHub updates before building", color = NovaBlue)
                    }
                    NovaHint("All updates require review and approval before a separate build confirmation.")
                }
            }
        }
        if (newest != null) {
            item { NovaSection("Latest build", "Read log", if (newest.logFile != null)
                { { onViewLog(newest) } } else null) }
            item {
                BuildPulseCard(
                    record = newest,
                    stage = BuildTransferProvider.stage(context, newest.id),
                    onViewLog = if (newest.logFile != null) { { onViewLog(newest) } } else null,
                    onImportApk = if (selected != null) { { onImportApk(selected) } } else null,
                    onOpenVault = onOpenVault
                )
            }
        }
        item {
            NovaSection("APK collection")
            NovaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatic APK handoff", fontWeight = FontWeight.SemiBold)
                        NovaHint("BuildDeck attempts direct verified transfer from Termux to Vault after each successful build.")
                    }
                    NovaStatus("Enabled", true)
                }
                if (outputConnected) {
                    NovaHint("Out-folder backup also connected.")
                    OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                        Text("Scan backup Out folder", color = NovaBlue)
                    }
                } else {
                    NovaHint("If the APK is in Downloads but not Vault, tap Import an APK manually below.")
                    OutlinedButton(onClick = onConnectOutput, modifier = Modifier.fillMaxWidth()) {
                        Text("Connect backup Out folder (optional)", color = NovaBlue)
                    }
                }
                if (selected != null) TextButton(onClick = { onImportApk(selected) }) {
                    Text("Import an APK manually ›", color = NovaBlue)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Build history", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                if (selected != null && completed + failed > 0) {
                    TextButton(onClick = { confirmBulkClear = true }) {
                        Text("Clear finished", color = NovaBlue)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NovaStatus("$active building", active > 0)
                NovaStatus("$completed ready", true)
                NovaStatus("$failed failed", false)
                if (waitingImport > 0) NovaStatus("$waitingImport import", false)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("All", "Building", "Ready", "Needs import", "Failed").forEach { tab ->
                    FilterChip(selected = statusFilter == tab,
                        onClick = { statusFilter = tab },
                        label = { Text(tab) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NovaBlue,
                            selectedLabelColor = androidx.compose.ui.graphics.Color.White))
                }
            }
        }
        val history = filtered.filterNot { it.id == newest?.id }
        if (history.isEmpty()) item {
            NovaCard { NovaHint(if (projectRecords.isEmpty()) "No builds yet. Start a build above."
                else if (newest != null && filtered.size == 1 && statusFilter == "All")
                    "Your newest build is displayed above."
                else "No other builds match this status filter.") }
        }
        items(history.take(40), key = { it.id }) { record ->
            BuildPulseCard(
                record = record,
                stage = BuildTransferProvider.stage(context, record.id),
                onViewLog = if (record.logFile != null) { { onViewLog(record) } } else null,
                onImportApk = if (selected != null) { { onImportApk(selected) } } else null,
                onOpenVault = onOpenVault,
                compact = true
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onImportLog(record) }) {
                    Text("Import log", color = NovaBlue)
                }
                if (record.status !in setOf("SUBMITTED", "RUNNING")) {
                    TextButton(onClick = { recordToClear = record }) {
                        Text("Clear entry", color = NovaMuted)
                    }
                }
            }
        }
        item {
            NovaHint("Clearing history never deletes APK Vault releases or source projects. Incomplete imports are not bulk-cleared.")
        }
    }
    if (recordToClear != null) {
        val record = recordToClear!!
        AlertDialog(
            onDismissRequest = { recordToClear = null },
            title = { Text("Clear build history entry?") },
            text = { Text("The request will be hidden, but saved APKs and project sources are preserved.") },
            confirmButton = {
                Button(onClick = { recordToClear = null; onDismiss(record) }) {
                    Text("Clear entry")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToClear = null }) { Text("Cancel") }
            }
        )
    }
    if (confirmBulkClear && selected != null) {
        AlertDialog(
            onDismissRequest = { confirmBulkClear = false },
            title = { Text("Clear finished build history?") },
            text = { Text("Remove $completed completed and $failed failed entries for " +
                selected.name + "? Your APK Vault, source code, logs, and unfinished imports stay saved.") },
            confirmButton = {
                Button(onClick = { confirmBulkClear = false; onClearCompleted(selected) }) {
                    Text("Clear finished")
                }
            },
            dismissButton = { TextButton(onClick = { confirmBulkClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
internal fun NovaVault(
    releases: List<Release>,
    projects: List<Project>,
    onSelectProject: (String) -> Unit,
    onInstall: (Release) -> Unit,
    onImport: (Project) -> Unit,
    onSaveToDownloads: (Release) -> Unit,
    onSaveToFolder: (Release) -> Unit,
    groupNames: Map<String, String>
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var detailsFor by remember { mutableStateOf<Release?>(null) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    val grouped = releases.groupBy { groupNames[it.projectId] ?: "Archived / other" }
    val visible = releases.filter {
        (selectedGroup == null || (groupNames[it.projectId] ?: "Archived / other") == selectedGroup) &&
            (it.name.contains(search, true) || groupNames[it.projectId]?.contains(search, true) == true)
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { NovaHeader("APK Vault", "Your finished APKs, with version and update checks.") }
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
        item {
            NovaSection("Project collections")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = selectedGroup == null, onClick = { selectedGroup = null }, label = { Text("All") })
                grouped.entries.sortedBy { it.key.lowercase() }.forEach { entry ->
                    FilterChip(selected = selectedGroup == entry.key, onClick = { selectedGroup = entry.key },
                        label = { Text(entry.key + " (" + entry.value.size + ")") })
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
                        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
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
                if (details != null) {
                    Text(details.packageId, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(details.verdict,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (details.warning) NovaRed else
                            if (details.updateState == "UPDATE") NovaGreen
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (details.installedVersion != null)
                        NovaHint("Installed v" + details.installedVersion +
                            " (" + details.installedVersionCode + ") • " + details.signatureState)
                } else {
                    Text("APK metadata unavailable — verify the file before installing.",
                        color = NovaRed, style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(groupNames[release.projectId] ?: "Archived project",
                        modifier = Modifier.weight(1f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { detailsFor = release }) {
                        Text("Details", color = NovaBlue)
                    }
                    Button(
                        onClick = { onInstall(release) },
                        enabled = details != null && !details.warning,
                        colors = ButtonDefaults.buttonColors(containerColor = NovaBlue)
                    ) {
                        Text(when (details?.updateState) {
                            "UPDATE" -> "Update"
                            "SAME_VERSION" -> "Reinstall"
                            "DOWNGRADE", "WRONG_SIGNATURE" -> "Blocked"
                            else -> "Install"
                        })
                    }
                }
                TextButton(onClick = { onSaveToFolder(release) }) {
                    Text("↓  Save to selected Vault folder", color = NovaBlue)
                }
                TextButton(onClick = { onSaveToDownloads(release) }) {
                    Text("↓  Save APK to Downloads", color = NovaBlue)
                }
                if (projects.any { it.id == release.projectId }) {
                    TextButton(onClick = { onSelectProject(release.projectId) }) {
                        Text("Open source project ›", color = NovaBlue)
                    }
                }
            }
        }
        item { NovaHint("Android always shows its own installer confirmation. BuildDeck never silently installs an APK.") }
    }
    if (detailsFor != null) {
        val selected = detailsFor!!
        val details = remember(selected.path) { ApkInspector.inspect(context, selected.path) }
        AlertDialog(
            onDismissRequest = { detailsFor = null },
            title = { Text("APK details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(selected.name, fontWeight = FontWeight.Bold)
                    if (details == null) {
                        Text("Could not read APK metadata. Check that this is a valid APK.")
                    } else {
                        Text("Package: " + details.packageId)
                        Text("APK: v" + details.version + " (" + details.versionCode + ")")
                        Text("Installed: " + if (details.installedVersion == null)
                            "No installed match visible" else
                            "v" + details.installedVersion + " (" + details.installedVersionCode + ")")
                        Text("Signature: " + details.signatureState)
                        Text(details.verdict,
                            color = if (details.warning) NovaRed else NovaGreen)
                    }
                    NovaHint("Installation is handled by Android. Unknown installed packages cannot be certified as compatible.")
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsFor = null }) { Text("Close") }
            }
        )
    }
}

@Composable
internal fun NovaSettings(
    outputConnected: Boolean,
    githubAccount: String?,
    darkMode: Boolean,
    onDarkMode: (Boolean) -> Unit,
    scanFolder: String, destinationFolder: String, autoScan: Boolean,
    onSmartFolder: (String) -> Unit, onDisconnectSmart: (String) -> Unit,
    onAutoScan: (Boolean) -> Unit, onScanSmart: () -> Unit,
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
            NovaSection("Storage manager")
            NovaCard {
                Text("APK collection folder", fontWeight = FontWeight.SemiBold)
                NovaHint(scanFolder)
                OutlinedButton(onClick = { onSmartFolder("scan") }, modifier = Modifier.fillMaxWidth()) { Text("Choose APK scan folder") }
                if (scanFolder != "Not selected") {
                    TextButton(onClick = { onDisconnectSmart("scan") }) { Text("Disconnect") }
                    NovaPrimaryButton("Scan selected folder", onScanSmart)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Scan on app return")
                        NovaHint("Match APK packages and skip exact duplicates")
                    }
                    Switch(checked = autoScan, onCheckedChange = onAutoScan)
                }
            }
            NovaCard {
                Text("APK Vault destination", fontWeight = FontWeight.SemiBold)
                NovaHint(destinationFolder)
                OutlinedButton(onClick = { onSmartFolder("destination") }, modifier = Modifier.fillMaxWidth()) { Text("Choose Vault folder") }
                if (destinationFolder != "Not selected") TextButton(onClick = { onDisconnectSmart("destination") }) { Text("Disconnect") }
                NovaHint("New APKs are copied into project subfolders. The private Vault and installer access stay intact.")
            }
        }
        item {
            NovaSection("Termux output fallback")
            NovaCard {
                Text("Verified build handoff folder", fontWeight = FontWeight.SemiBold)
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
                Text("BuildDeck v0.4.8 • Smart Studio",
                    fontWeight = FontWeight.Bold)
                NovaHint("Restored the original blue-layer brand mark and top hero artwork. Nova Studio retains your projects, GitHub connection, APK Vault and local builder.")
                NovaHint("Remote ChatGPT phone access is not enabled.")
            }
        }
    }
}
