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
fun GitHubProjectCard(link: RepoLink?, working: Boolean, onReview: () -> Unit, onFetch: () -> Unit) {
    if (link == null) return
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("LINKED GITHUB REPOSITORY", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text("${link.owner}/${link.repo} • ${link.branch}", fontWeight = FontWeight.Medium)
            Text("Review locally edited text files before committing them. Remote changes are checked for conflicts. No files are pushed automatically.",
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = onFetch, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                Text("Fetch changes from GitHub")
            }
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


/** This dialog reviews code already fetched from GitHub, with no background write access. */
@Composable
fun IncomingGithubReview(
    review: IncomingReview,
    selected: Set<String>,
    onSelected: (Set<String>) -> Unit,
    buildAfter: Boolean,
    onBuildAfterChanged: (Boolean) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember(review.headSha) { mutableStateOf<Set<String>>(emptySet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review incoming GitHub changes") },
        text = {
            Column(
                Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${review.link.owner}/${review.link.repo} • ${review.link.branch}",
                    fontWeight = FontWeight.Bold)
                Text("Commit ${review.headSha.take(10)}. Only checked source files are applied. BuildDeck saves a rollback snapshot first. No changes are installed without your approval.",
                    style = MaterialTheme.typography.bodySmall)
                if (review.skipped > 0) {
                    Text("${review.skipped} changed files skipped (restricted formats or review limit). No changes to those files will be applied.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                if (review.changes.isEmpty()) Text("No supported upstream changes found. Your project may already be current.")
                review.changes.forEach { change ->
                    val allowed = change.conflict == null && change.text != null
                    Column {
                        Row {
                            Checkbox(
                                checked = change.path in selected,
                                enabled = allowed,
                                onCheckedChange = { yes ->
                                    onSelected(if (yes) selected + change.path else selected - change.path)
                                }
                            )
                            Column(Modifier.padding(top = 6.dp)) {
                                Text("${change.kind} • ${change.path}", style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold)
                                if (change.conflict != null) {
                                    Text(change.conflict, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                                if (change.text != null) {
                                    TextButton(onClick = {
                                        expanded = if (change.path in expanded) expanded - change.path
                                            else expanded + change.path
                                    }) { Text(if (change.path in expanded) "Hide incoming source" else "Preview incoming source") }
                                    if (change.path in expanded) {
                                        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                                            Text(change.text.take(1400), modifier = Modifier.padding(8.dp),
                                                style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
                if (selected.isNotEmpty()) {
                    Row {
                        Checkbox(checked = buildAfter, onCheckedChange = onBuildAfterChanged)
                        Text("Ask me to confirm an APK build after applying",
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Never approve untrusted build.gradle scripts or source. The app will stop if GitHub or your local files change before applying.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(enabled = selected.isNotEmpty(), onClick = onApply) {
                Text("Apply ${selected.size} approved files")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
