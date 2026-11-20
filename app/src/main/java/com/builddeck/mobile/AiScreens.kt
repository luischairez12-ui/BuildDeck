package com.builddeck.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Permissions are enforced on imported AI change bundles, not normal manual project editing. */
@Composable
fun AiProjectControls(
    access: AiAccess,
    onAccessChanged: (AiAccess) -> Unit,
    onImportPatch: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .38f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI DEVELOPER CONTROL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("Choose what an AI-generated request may ask BuildDeck to do. This version uses private, manually imported patch files; it is not connected to ChatGPT yet.",
                style = MaterialTheme.typography.bodySmall)
            PermissionSwitch("Share/export project sources", access.read) { onAccessChanged(access.copy(read = it)) }
            PermissionSwitch("Approve AI source edits", access.write) { onAccessChanged(access.copy(write = it)) }
            PermissionSwitch("Request AI-assisted APK builds", access.build) { onAccessChanged(access.copy(build = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onAccessChanged(AiAccess(true, true, true)) }, modifier = Modifier.weight(1f)) {
                    Text("Grant all")
                }
                OutlinedButton(onClick = { onAccessChanged(AiAccess()) }, modifier = Modifier.weight(1f)) {
                    Text("Revoke all")
                }
            }
            Button(onClick = onImportPatch, enabled = access.write, modifier = Modifier.fillMaxWidth()) {
                Text("Import AI patch for review")
            }
            Text("Every patch requires a separate on-device approval. No network service, background AI access or unattended builds are included.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionSwitch(name: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun BuildRequestHistory(records: List<BuildRecord>, onImportLog: (BuildRecord) -> Unit, onViewLog: (BuildRecord) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("BUILD REQUESTS (${records.size})", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        if (records.isEmpty()) Text("Build requests and their logs will appear here after a Termux build is submitted.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        records.take(12).forEach { record ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Build ${record.id.take(8)}", fontWeight = FontWeight.SemiBold)
                            Text(record.status.replace('_', ' '), style = MaterialTheme.typography.labelMedium,
                                color = if (record.status == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = { onImportLog(record) }) { Text("Import log") }
                    }
                    if (record.logFile != null) TextButton(onClick = { onViewLog(record) }) { Text("Read build log / errors") }
                    else if (record.status == "SUBMITTED") Text("Termux is still running or its completion callback has not arrived.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (record.note.isNotBlank()) Text(record.note, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (record.status == "SUCCESS_NEEDS_IMPORT") Text("Open Releases below and select the APK from Downloads/BuildDeck/Out.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun PatchReviewDialog(
    bundle: PatchBundle,
    previews: List<PatchPreview>,
    selected: Set<Int>,
    onSelected: (Set<Int>) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember(bundle.requestId) { mutableIntStateOf(-1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review AI changes") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(bundle.title, fontWeight = FontWeight.Bold)
                if (bundle.summary.isNotBlank()) Text(bundle.summary, style = MaterialTheme.typography.bodySmall)
                Text("You choose which source files change. A local snapshot is saved first.", style = MaterialTheme.typography.bodySmall)
                if (bundle.requestBuild) Text("This request asks for an APK build after applying changes. A separate confirmation will be shown if AI build permission is enabled.",
                    style = MaterialTheme.typography.bodySmall)
                previews.forEach { preview ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = preview.index in selected, enabled = preview.conflict == null,
                                onCheckedChange = { checked ->
                                    onSelected(if (checked) selected + preview.index else selected - preview.index)
                                })
                            Column(Modifier.weight(1f).clickable { expanded = if (expanded == preview.index) -1 else preview.index }) {
                                Text("${preview.kind.uppercase()} · ${preview.path}", style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold)
                                if (preview.conflict != null) Text(preview.conflict, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error)
                                else Text("Tap to inspect diff", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (expanded == preview.index) {
                            Surface(color = Color(0xFF0B1525), shape = MaterialTheme.shapes.small) {
                                Text(preview.diff, modifier = Modifier.padding(9.dp), color = Color(0xFFE1EAF5),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Text("Gradle scripts and source files can execute arbitrary code when built. Only approve changes you trust.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Button(onClick = onApply, enabled = selected.isNotEmpty()) { Text("Apply ${selected.size} files") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
