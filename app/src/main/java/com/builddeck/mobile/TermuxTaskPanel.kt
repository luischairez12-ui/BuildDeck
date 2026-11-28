package com.builddeck.mobile

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun TermuxTaskPanel(projects: List<Project>, records: List<BuildRecord>, onRecover: () -> Unit,
    onDismissStale: (Set<String>) -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(TermuxTaskBridge.PREF, android.content.Context.MODE_PRIVATE) }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var localError by remember { mutableStateOf("") }
    var cancelFor by remember { mutableStateOf<ManagedTermuxJob?>(null) }
    var showLegacy by remember { mutableStateOf(false) }
    var dismissIds by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(prefs) { while (true) { tick = System.currentTimeMillis(); delay(1000) } }
    val privateReport = remember(tick) { TermuxTaskBridge.privateSnapshot(context) }
    val snapshot = privateReport?.first ?: prefs.getString("snapshot", "").orEmpty()
    val sent = prefs.getLong("sent", 0)
    val received = maxOf(privateReport?.second ?: 0L, prefs.getLong("received", 0))
    val waiting = sent > received && tick - sent < 25000
    val jobs = remember(snapshot) { managedJobs(snapshot) }
    val active = jobs.count { it.process == "ACTIVE" }
    val error = if (privateReport != null) "" else prefs.getString("error", "").orEmpty()
    fun request(action: String, id: String = "") {
        localError = ""
        try { TermuxTaskBridge.request(context, action, id) } catch (e: Exception) {
            localError = "Open Termux and grant RUN_COMMAND permission: " + e.message.orEmpty()
        }
        tick = System.currentTimeMillis()
    }
    LaunchedEffect(Unit) { if (!waiting) request("list") }
    NovaSection("Termux task manager")
    NovaCard {
        Text(if (received == 0L) "Task status not checked" else "$active active at last check • " + jobs.size + " tracked", fontWeight = FontWeight.Bold)
        NovaHint(if (received == 0L) "Tap Refresh to query Termux." else "Last response: " + novaDate(received))
        if (waiting) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (sent > received && !waiting) NovaHint("No response from Termux. Existing snapshot may be stale. Open Termux and retry.")
        if (error.isNotBlank() || localError.isNotBlank()) NovaHint(localError.ifBlank { error })
        snapshot.lineSequence().filter { it.startsWith("NOTICE|") }.take(3).forEach { NovaHint(it.substringAfter('|')) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { request("list") }, enabled = !waiting, modifier = Modifier.weight(1f)) { Text("Refresh") }
            OutlinedButton(onClick = onRecover, modifier = Modifier.weight(1f)) { Text("Recover APKs") }
        }
        OutlinedButton(onClick = { request("clean") }, enabled = !waiting, modifier = Modifier.fillMaxWidth()) {
            Text("Clean finished task records")
        }
        NovaHint("Cleanup only removes finished private task metadata, not projects, logs, APKs or caches.")
        TextButton(onClick = {
            try {
                val launch = context.packageManager.getLaunchIntentForPackage("com.termux") ?: error("Termux not installed")
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) { localError = e.message.orEmpty() }
        }) { Text("Open Termux") }
        jobs.sortedWith(compareByDescending<ManagedTermuxJob> { it.process == "ACTIVE" }).take(40).forEach { job ->
            HorizontalDivider()
            Text(projects.firstOrNull { it.id == job.projectId }?.name ?: job.projectId.take(8),
                fontWeight = FontWeight.SemiBold)
            NovaHint(job.id.take(8) + " • " + job.phase.replace('_', ' ') + " • " + job.process)
            val canCancel = job.phase in setOf("STARTING", "PREPARING", "EXTRACTING", "COMPILING") && job.process == "ACTIVE"
            if (canCancel) OutlinedButton(onClick = { cancelFor = job }, enabled = !waiting && sent <= received) { Text("Cancel this build") }
        }
        val stale = records.filter { record -> record.status in setOf("SUBMITTED", "RUNNING") &&
            tick - record.submittedAt >= 5 * 60_000L && jobs.none { it.id == record.id && it.process != "STOPPED" } }
        if (stale.isNotEmpty()) {
            HorizontalDivider()
            Text("Stale requests (" + stale.size + ")", fontWeight = FontWeight.Bold)
            NovaHint("Dismiss old requests from the live monitor after stopping any remaining Termux jobs. Output can still be recovered.")
            OutlinedButton(onClick = { dismissIds = stale.map { it.id }.toSet() }, modifier = Modifier.fillMaxWidth()) {
                Text("Dismiss all stale requests")
            }
            stale.take(20).forEach { record ->
                TextButton(onClick = { dismissIds = setOf(record.id) }) {
                    Text("Dismiss " + (projects.firstOrNull { it.id == record.projectId }?.name ?: record.id.take(8)), maxLines = 2)
                }
            }
        }
        val legacy = records.filter { it.status in setOf("SUBMITTED", "RUNNING") && jobs.none { job -> job.id == it.id } }
        if (legacy.isNotEmpty()) {
            TextButton(onClick = { showLegacy = !showLegacy }) { Text("Unverified requests (" + legacy.size + ")") }
            if (showLegacy) {
                NovaHint("Older or unregistered builds cannot be safely cancelled individually here. Recover output first; inspect them in Termux.")
                legacy.take(20).forEach { record ->
                    NovaHint((projects.firstOrNull { it.id == record.projectId }?.name ?: "Project") + " • " + record.id.take(8) + " • " + record.status)
                }
            }
        }
    }
    dismissIds?.let { ids ->
        AlertDialog(onDismissRequest = { dismissIds = null }, title = { Text("Dismiss stale requests?") },
            text = { Text("Confirm you have stopped or no longer need these Termux jobs. This hides requests, not processes. BuildDeck checks for recoverable APKs first and keeps the original records for later recovery. No project or APK is deleted.") },
            confirmButton = { TextButton(onClick = { dismissIds = null; onDismissStale(ids) }) { Text("Dismiss " + ids.size) } },
            dismissButton = { TextButton(onClick = { dismissIds = null }) { Text("Keep") } })
    }
    cancelFor?.let { job ->
        AlertDialog(onDismissRequest = { cancelFor = null }, title = { Text("Cancel this build?") },
            text = { Text("Only this managed build will receive a cancellation request. If packaging has started, BuildDeck will finish saving the APK instead.") },
            confirmButton = { TextButton(onClick = { cancelFor = null; request("cancel", job.id) }) { Text("Cancel build") } },
            dismissButton = { TextButton(onClick = { cancelFor = null }) { Text("Keep running") } })
    }
}
