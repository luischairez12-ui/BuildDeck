package com.builddeck.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ProjectCleanupChoice(
    val project: Project,
    val gradleReady: Boolean,
    val githubLinked: Boolean
)

@Composable
fun ProjectCleanupDialog(
    choices: List<ProjectCleanupChoice>,
    onDismiss: () -> Unit,
    onArchive: (String, Boolean) -> Unit
) {
    val recommended = choices.firstOrNull { it.githubLinked && it.gradleReady }
        ?: choices.firstOrNull { it.gradleReady }
    var keepId by remember(choices) { mutableStateOf(recommended?.project?.id) }
    var acknowledged by remember { mutableStateOf(false) }
    var rebuildAfter by remember { mutableStateOf(true) }
    val keep = choices.firstOrNull { it.project.id == keepId }
    val old = choices.filter { it.project.id != keepId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keep one BuildDeck project") },
        text = {
            Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Choose the ONE project to keep. The other copies will leave Projects, but their sources can be restored and APKs will stay in the Vault.",
                    style = MaterialTheme.typography.bodyMedium)
                choices.forEach { item ->
                    val project = item.project
                    Row {
                        RadioButton(selected = project.id == keepId,
                            enabled = item.gradleReady,
                            onClick = { keepId = project.id; acknowledged = false })
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(project.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    item.githubLinked && item.gradleReady -> "GitHub linked • Gradle source ready"
                                    item.gradleReady -> "Local Gradle source • no GitHub link"
                                    else -> "Incomplete source • cannot keep as only project"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.gradleReady) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                if (keep != null) {
                    HorizontalDivider()
                    Text("Keep: ${keep.project.name}", fontWeight = FontWeight.Bold)
                    Text("Archive ${old.size} older project${if (old.size == 1) "" else "s"}:",
                        style = MaterialTheme.typography.bodySmall)
                    old.forEach {
                        Text("• ${it.project.name}", style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        Checkbox(checked = rebuildAfter, onCheckedChange = { rebuildAfter = it })
                        Text("Review latest GitHub changes, then offer to build",
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                        Text("I reviewed the project that will be kept.",
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("This changes only BuildDeck's local project list. It does not delete GitHub repositories, delete your Vault APKs, or touch Termux.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (keep != null) onArchive(keep.project.id, rebuildAfter)
            }, enabled = keep?.gradleReady == true && old.isNotEmpty() && acknowledged) {
                Text("Keep one, archive ${old.size}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ArchivedProjectsDialog(
    archived: List<ArchivedProject>,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Archived projects") },
        text = {
            Column(Modifier.heightIn(max = 390.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("These old sources are hidden from Projects, not deleted. Restore one if you need it.",
                    style = MaterialTheme.typography.bodySmall)
                archived.forEach { entry ->
                    Column {
                        Text(entry.project.name, fontWeight = FontWeight.Medium)
                        Text("${entry.releaseIds.size} APK(s) kept safely in Vault",
                            style = MaterialTheme.typography.labelSmall)
                        OutlinedButton(onClick = { onRestore(entry.project.id) }) {
                            Text("Restore project")
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
