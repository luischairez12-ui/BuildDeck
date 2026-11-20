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
    fun reconcile(store: Store, ai: AiControlStore): Boolean {
        val uri = treeUri() ?: return false
        val root = DocumentFile.fromTreeUri(context, uri)?.takeIf { it.isDirectory } ?: return false
        val directories = root.listFiles().filter { it.isDirectory }.associateBy { it.name }
        var changed = false
        for (record in ai.buildRecords().take(80)) {
            if (record.status !in setOf("SUBMITTED", "RUNNING", "SUCCESS_NEEDS_IMPORT")) continue
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
            if (result == "SUCCESS" && apk != null && project != null) {
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
            } else if (result == "SUCCESS" && record.status != "SUCCESS_NEEDS_IMPORT") {
                ai.completeLocalBuild(record.id, "SUCCESS_NEEDS_IMPORT", "Compiled; use manual APK import", logText.orEmpty())
                changed = true
            } else if (stage.isNotBlank() && record.note != stage && result.isBlank()) {
                ai.updateBuildProgress(record.id, "RUNNING", stage)
                changed = true
            }
        }
        return changed
    }
}
