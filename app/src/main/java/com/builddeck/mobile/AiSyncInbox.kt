package com.builddeck.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Read-only overview of the existing GitHubIncoming review engine.
 * A pending item is a GitHub change, not an assertion about who authored it.
 * No source is modified until the user opens the existing approval dialog.
 */
internal data class AiSyncInboxEntry(
    val projectId: String,
    val projectName: String,
    val repository: String,
    val branch: String,
    val changedPaths: List<String> = emptyList(),
    val skipped: Int = 0,
    val conflicts: Int = 0,
    val error: String? = null
) {
    val pendingCount: Int get() = changedPaths.size + skipped
}

@Composable
internal fun NovaAiSyncInbox(
    entries: List<AiSyncInboxEntry>,
    linkedProjects: Int,
    scanning: Boolean,
    checkedAt: Long,
    onRefresh: () -> Unit,
    onReview: (String) -> Unit,
    onLinkProject: () -> Unit
) {
    val attention = entries.filter { it.pendingCount > 0 || it.error != null }
        .sortedWith(compareByDescending<AiSyncInboxEntry> { it.pendingCount }
            .thenBy { it.projectName })
    NovaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(13.dp),
                color = NovaBlue.copy(alpha = .10f)
            ) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Text("⇅", color = NovaBlue, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("AI Sync Inbox", fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium)
                NovaHint("GitHub source updates for your linked projects")
            }
            TextButton(onClick = onRefresh, enabled = !scanning && linkedProjects > 0) {
                Text("Refresh", color = NovaBlue)
            }
        }
        if (linkedProjects == 0) {
            NovaHint("Connect an Android project to GitHub to receive source changes here. " +
                "Nothing is downloaded into the project or built automatically.")
            OutlinedButton(onClick = onLinkProject, modifier = Modifier.fillMaxWidth()) {
                Text("Connect GitHub project", color = NovaBlue)
            }
        } else {
            if (scanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = NovaBlue)
                NovaHint("Checking $linkedProjects linked project(s). No source files are being changed.")
            } else {
                if (checkedAt != 0L) NovaHint("Last checked " + novaDate(checkedAt))
                if (attention.isEmpty() && checkedAt != 0L) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✓", color = NovaGreen, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(7.dp))
                        Text("No pending source changes", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            attention.take(12).forEach { entry ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.projectName, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        NovaStatus(
                            if (entry.error != null) "CHECK FAILED"
                            else "${entry.pendingCount} CHANGED",
                            entry.error == null && entry.conflicts == 0 && entry.skipped == 0
                        )
                    }
                    NovaHint("${entry.repository} • ${entry.branch}")
                    if (entry.error != null) {
                        Text(entry.error.take(200), color = NovaRed,
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        if (entry.changedPaths.isNotEmpty()) {
                            NovaHint(entry.changedPaths.take(3).joinToString(" · ") {
                                it.substringAfterLast('/')
                            } + if (entry.changedPaths.size > 3) " +${entry.changedPaths.size - 3} more" else "")
                        }
                        if (entry.conflicts > 0 || entry.skipped > 0) {
                            Text(
                                "${entry.conflicts} conflict(s) • ${entry.skipped} restricted/unreviewed",
                                style = MaterialTheme.typography.labelSmall,
                                color = NovaRed
                            )
                        }
                        OutlinedButton(onClick = { onReview(entry.projectId) },
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Review changes", color = NovaBlue)
                        }
                    }
                }
            }
            NovaHint("Changes come from GitHub and may be authored by anyone with repository access. " +
                "Applying files requires your review; APK builds and installs always need approval.")
        }
    }
}
