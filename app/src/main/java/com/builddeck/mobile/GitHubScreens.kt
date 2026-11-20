package com.builddeck.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** GitHub credentials are encrypted at rest, never committed into repositories. */
@Composable
fun GitHubImportCard(
    account: String?,
    working: Boolean,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onImport: (String, String) -> Unit
) {
    var repo by remember { mutableStateOf("luischairez12-ui/BuildDeck") }
    var branch by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("GITHUB WORKSPACE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Import a public repo by name. A connected fine-grained token also permits private repo access and approved source pushes.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(repo, { repo = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("owner/repository") })
            OutlinedTextField(branch, { branch = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Branch (blank = default)") })
            Button(onClick = { onImport(repo, branch) }, enabled = !working && repo.isNotBlank(),
                modifier = Modifier.fillMaxWidth()) { Text("Import GitHub source") }
            HorizontalDivider()
            if (account != null) {
                Text("Connected as @$account", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDisconnect, enabled = !working) { Text("Disconnect GitHub token") }
            } else {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "Hide token setup" else "Connect GitHub for source sync")
                }
                if (showToken) {
                    Text("Create a fine-grained GitHub token scoped to chosen repositories with Contents read/write. Never paste the token into ChatGPT or a public repository.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(token, { token = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Fine-grained access token") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                    Button(onClick = { val value = token; token = ""; onConnect(value) },
                        enabled = !working && token.isNotBlank()) { Text("Connect securely") }
                }
            }
        }
    }
}

@Composable
fun GitHubProjectCard(link: RepoLink?, working: Boolean, onReview: () -> Unit) {
    if (link == null) return
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("LINKED GITHUB REPOSITORY", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text("${link.owner}/${link.repo} • ${link.branch}", fontWeight = FontWeight.Medium)
            Text("Review locally edited text files before committing them. Remote changes are checked for conflicts. No files are pushed automatically.",
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = onReview, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                Text("Review changes for GitHub sync")
            }
        }
    }
}

@Composable
fun GitHubSyncReview(
    projectName: String,
    files: List<SyncCandidate>,
    selected: Set<String>,
    onSelected: (Set<String>) -> Unit,
    onCommit: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Approve GitHub source sync") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(projectName, fontWeight = FontWeight.Bold)
                Text("These files will be committed to the linked GitHub repository. If it's public, everyone can read them. No deletions or binary files are included.",
                    style = MaterialTheme.typography.bodySmall)
                if (files.isEmpty()) Text("No changed source files detected.")
                files.forEach { file ->
                    Row {
                        Checkbox(checked = file.path in selected, onCheckedChange = { yes ->
                            onSelected(if (yes) selected + file.path else selected - file.path)
                        })
                        Column(Modifier.padding(top = 11.dp)) {
                            Text(file.path, style = MaterialTheme.typography.bodySmall)
                            Text(if (file.oldSha == null) "New file" else "Modified file",
                                style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = {
                                expanded = if (file.path in expanded) expanded - file.path else expanded + file.path
                            }) { Text(if (file.path in expanded) "Hide source" else "Preview source") }
                            if (file.path in expanded) {
                                Text(file.text.take(600), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = selected.isNotEmpty(), onClick = onCommit) {
                Text("Commit ${selected.size} files")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
