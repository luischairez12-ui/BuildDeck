package com.builddeck.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** User-granted access to Downloads/BuildDeck/Out, never all-files permission. */
class OutputCollector(private val context: Context) {
    private val prefs = context.getSharedPreferences("builddeck_output_folder", Context.MODE_PRIVATE)

    fun isConnected(): Boolean = treeUri() != null

    fun connect(uri: Uri) {
        val folder = DocumentFile.fromTreeUri(context, uri) ?: error("Folder unavailable")
        require(folder.isDirectory && folder.name.equals("Out", ignoreCase = true)) {
            "Select Downloads / BuildDeck / Out, not Inbox or Downloads"
        }
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit().putString("treeUri", uri.toString()).apply()
    }

    fun disconnect() {
        treeUri()?.let { uri ->
            runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        prefs.edit().remove("treeUri").apply()
    }

    private fun treeUri(): Uri? = prefs.getString("treeUri", null)?.let(Uri::parse)?.takeIf { uri ->
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    }

    /** Retry auto-import when Termux's direct FileProvider handoff did not work. */

    /** Diagnose stale Android folder picks and retry after Termux reports success. */
    fun scanSummary(store: Store, ai: AiControlStore): String {
        val uri = treeUri() ?: return "Out folder not connected. Select Downloads/BuildDeck/Out in Settings."
        val root = try { DocumentFile.fromTreeUri(context, uri) } catch (e: Exception) {
            return "Out folder permission failed: " + e.message
        }
        if (root?.isDirectory != true) return "Selected Out folder is unavailable. Reconnect it in Settings."
        val folders = try { root.listFiles().filter { it.isDirectory } } catch (e: Exception) {
            return "Cannot list Out folder: " + e.message
        }
        val pending = ai.buildRecords().filter { it.status == "SUCCESS_NEEDS_IMPORT" }
        val matching = folders.filter { dir -> pending.any { it.projectId == dir.name } }
        var found = 0
        for (dir in matching) {
            try { found += dir.listFiles().count { file ->
                file.isFile && file.name?.endsWith(".apk", true) == true
            } } catch (_: Exception) { /* continue */ }
        }
        val readyBefore = ai.buildRecords().count { it.status == "READY" }
        val updated = reconcile(store, ai)
        val readyAfter = ai.buildRecords().count { it.status == "READY" }
        val stillPending = ai.buildRecords().count { it.status == "SUCCESS_NEEDS_IMPORT" }
        return when {
            readyAfter > readyBefore -> "Success! Imported ${readyAfter - readyBefore} APK(s) into Vault. Open Releases."
            updated -> "Out scan updated build status. $stillPending builds still need import; see Build Requests."
            folders.isEmpty() -> "Selected Out folder is empty. Reconnect the Out folder that contains Termux builds."
            matching.isEmpty() -> "Out is accessible with ${folders.size} project folders, but none match pending builds. Reconnect the correct Out folder."
            found == 0 -> "Project folder found but no matching APK. Reconnect the correct Out folder."
            else -> "Found $found APK(s), but import is blocked. Check the Build Request for the import error."
        }
    }

    fun reconcile(store: Store, ai: AiControlStore): Boolean {
        val uri = treeUri() ?: return false
        val root = DocumentFile.fromTreeUri(context, uri)?.takeIf { it.isDirectory } ?: return false
        val directories = root.listFiles().filter { it.isDirectory }.associateBy { it.name }
        var changed = false
        for (record in ai.buildRecords().take(80)) {
            if (record.status !in setOf("SUBMITTED", "RUNNING", "SUCCESS_NEEDS_IMPORT", "FAILED")) continue
            val directory = directories[record.projectId] ?: continue
            val files = directory.listFiles()
            val log = files.firstOrNull { it.isFile && it.name == "${record.id}.log" }
            val apk = files.firstOrNull { it.isFile && it.name?.startsWith("${record.id}_") == true &&
                it.name?.endsWith(".apk", ignoreCase = true) == true }
            val logText = if (log != null && log.length() in 1L..4_000_000L) {
                runCatching { context.contentResolver.openInputStream(log.uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            } else null
            val result = when {
                logText?.contains("BUILDDECK_RESULT=SUCCESS") == true -> "SUCCESS"
                logText?.contains("BUILDDECK_RESULT=FAILED") == true -> "FAILED"
                else -> ""
            }
            val stage = logText?.lineSequence()
                ?.filter { it.startsWith("BUILDDECK_STAGE=") }?.lastOrNull()?.substringAfter('=').orEmpty()
            val project = store.projects().firstOrNull { it.id == record.projectId }
            if ((result == "SUCCESS" || record.status == "SUCCESS_NEEDS_IMPORT") && apk != null && project != null) {
                try {
                    store.importRelease(project, apk.uri)
                    ai.completeLocalBuild(record.id, "READY", "APK saved automatically to the Vault", logText.orEmpty())
                    changed = true
                } catch (error: Exception) {
                    val note = "APK compiled; manual import needed: " + error.message.orEmpty().take(75)
                    if (record.note != note) {
                        ai.updateBuildProgress(record.id, "SUCCESS_NEEDS_IMPORT", note)
                        changed = true
                    }
                }
            } else if (result == "FAILED" && record.status != "FAILED") {
                ai.completeLocalBuild(record.id, "FAILED", "Build failed — check the log", logText.orEmpty())
                changed = true
            } else if (result == "SUCCESS" && record.status !in setOf("SUCCESS_NEEDS_IMPORT", "FAILED")) {
                ai.completeLocalBuild(record.id, "SUCCESS_NEEDS_IMPORT", "Compiled; use manual APK import", logText.orEmpty())
                changed = true
            } else if (stage.isNotBlank() && record.note != stage && result.isBlank() &&
                record.status in setOf("SUBMITTED", "RUNNING")) {
                ai.updateBuildProgress(record.id, "RUNNING", stage)
                changed = true
            }
        }
        return changed
    }
}
