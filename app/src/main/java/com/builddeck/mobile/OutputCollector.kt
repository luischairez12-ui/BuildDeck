package com.builddeck.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.security.MessageDigest
import java.io.File
import java.io.RandomAccessFile

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

    /**
     * Android's DocumentsProvider can return an empty children list for shared
     * folders created by Termux even though the files exist. This scoped fallback
     * opens one exact build file within the user-granted tree instead of enumerating.
     * Never accepts arbitrary paths: both IDs are UUIDs and the name comes from a
     * successful, user-requested build log.
     */
    private fun directApkUri(tree: Uri, record: BuildRecord, ai: AiControlStore): Uri? {
        if (!record.id.matches(Regex("[a-f0-9-]{36}")) ||
            !record.projectId.matches(Regex("[a-f0-9-]{36}"))) return null
        val log = runCatching { ai.buildLog(record) }.getOrDefault("")
        val fromLog = log.lineSequence().mapNotNull { line ->
            if (!line.startsWith("BUILDDECK_APK=")) return@mapNotNull null
            val name = line.substringAfter("BUILDDECK_APK=").substringAfterLast('/')
            name.takeIf { it.startsWith("${record.id}_") &&
                it.matches(Regex("[a-f0-9-]{36}_[a-zA-Z0-9_.-]+\\.apk")) }
        }.lastOrNull()
        val candidates = listOfNotNull(fromLog, "${record.id}_app-debug.apk").distinct()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            ?: return null
        for (name in candidates) {
            val fileId = "$rootId/${record.projectId}/$name"
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, fileId)
            val canRead = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.read() == 'P'.code && input.read() == 'K'.code
                } == true
            }.getOrDefault(false)
            if (canRead) return uri
        }
        return null
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
            folders.isEmpty() -> "Android reports no visible folders inside Out, even though Termux may have saved an APK there. Direct-file access was attempted. Use + Import a built APK for now; do not rename folders again."
            matching.isEmpty() -> "The folder picker cannot see a matching project folder. Direct-file access was attempted. Use the project APK import picker instead of reconnecting repeatedly."
            found == 0 -> "No indexed APK was found for the pending build. Try + Import a built APK from Downloads."
            else -> "Found $found APK(s), but import is blocked. Check the Build Request for the import error."
        }
    }

    companion object { internal val recoveryLock = Any() }

    /** Recover a terminal result directly from the authenticated private handoff.
     *  The Termux PendingIntent and a user-granted shared folder are optional here.
     *  A success marker alone is insufficient: require the exact APK digest.
     */
    private fun reconcilePrivate(store: Store, ai: AiControlStore): Boolean {
        val dir = File(context.filesDir, "builddeck_results")
        var changed = false
        for (record in ai.buildRecords().filter { it.status in setOf("SUBMITTED", "RUNNING", "SUCCESS", "SUCCESS_NEEDS_IMPORT", "FAILED") }.take(80)) {
            if (!record.id.matches(Regex("[a-f0-9-]{36}"))) continue
            val log = File(dir, "${record.id}.log")
            val apk = File(dir, "${record.id}.apk")
            val console = runCatching {
                if (!log.isFile) "" else RandomAccessFile(log, "r").use { file ->
                    val size = minOf(file.length(), 131072L).toInt()
                    val bytes = ByteArray(size)
                    file.seek(file.length() - size)
                    file.readFully(bytes)
                    String(bytes, Charsets.UTF_8)
                }
            }.getOrDefault("")
            val marker = console.lineSequence().lastOrNull { it.startsWith("BUILDDECK_RESULT=") }
                ?.substringAfter('=')?.substringBefore(' ').orEmpty()
            val expected = console.lineSequence().lastOrNull { it.startsWith("BUILDDECK_APK_SHA256=") }
                ?.substringAfter('=')?.trim()?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
            if (marker == "SUCCESS") {
                val project = store.projects().firstOrNull { it.id == record.projectId }
                val valid = expected != null && project != null && apk.isFile &&
                    apk.length() in 101L..(500L * 1024 * 1024) && runCatching {
                        val sha = MessageDigest.getInstance("SHA-256")
                        apk.inputStream().buffered().use { input ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                sha.update(buffer, 0, n)
                            }
                        }
                        sha.digest().joinToString("") { "%02x".format(it) } == expected
                    }.getOrDefault(false)
                if (valid && project != null) {
                    try {
                        // Deterministic name lets a recovery retry avoid duplicate Vault entries.
                        val name = "BuildDeck-${record.id}.apk"
                        if (store.releases().none { it.projectId == project.id && it.name == name && File(it.path).isFile }) {
                            store.importLocalRelease(project, apk, name)
                        }
                        ai.completeLocalBuild(record.id, "READY", "Recovered verified APK from private handoff", console)
                        BuildTransferProvider.revoke(context, record.id)
                        changed = true
                    } catch (error: Exception) {
                        val note = "Compiled; Vault import needs attention: " + error.message.orEmpty().take(85)
                        if (record.status != "SUCCESS_NEEDS_IMPORT" || record.note != note) {
                            ai.completeLocalBuild(record.id, "SUCCESS_NEEDS_IMPORT", note, console)
                            changed = true
                        }
                    }
                } else if (record.status != "SUCCESS_NEEDS_IMPORT") {
                    ai.completeLocalBuild(record.id, "SUCCESS_NEEDS_IMPORT", "Compiled; awaiting verified APK or backup recovery", console)
                    changed = true
                }
            } else if (marker == "FAILED" && record.status in setOf("SUBMITTED", "RUNNING")) {
                ai.completeLocalBuild(record.id, "FAILED", if (console.contains("BUILDDECK_CANCELLED=YES")) "Cancelled by user" else "Build failed — check the log", console)
                changed = true
            } else if (record.status in setOf("SUBMITTED", "RUNNING")) {
                val stage = BuildTransferProvider.stage(context, record.id)
                if (!stage.isNullOrBlank() && (record.note != stage || record.status == "SUBMITTED")) {
                    ai.updateBuildProgress(record.id, "RUNNING", stage)
                    changed = true
                }
            }
        }
        return changed
    }

    fun reconcile(store: Store, ai: AiControlStore): Boolean = synchronized(recoveryLock) {
        var changed = reconcilePrivate(store, ai)
        val uri = treeUri() ?: return changed
        val root = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
            ?.takeIf { it.isDirectory } ?: return changed
        val directories = runCatching {
            root.listFiles().filter { it.isDirectory }.associateBy { it.name }
        }.getOrDefault(emptyMap())
        for (record in ai.buildRecords().filter { it.status in setOf("SUBMITTED", "RUNNING", "SUCCESS", "SUCCESS_NEEDS_IMPORT", "FAILED") }.take(80)) {
            val files = runCatching { directories[record.projectId]?.listFiles()?.toList() }
                .getOrNull().orEmpty()
            val log = files.firstOrNull { it.isFile && it.name == "${record.id}.log" }
            val listedApk = files.firstOrNull { it.isFile && it.name?.startsWith("${record.id}_") == true &&
                it.name?.endsWith(".apk", ignoreCase = true) == true }
            val logUri = log?.uri ?: runCatching {
                require(record.id.matches(Regex("[a-f0-9-]{36}")) && record.projectId.matches(Regex("[a-f0-9-]{36}")))
                val rootId = DocumentsContract.getTreeDocumentId(uri)
                DocumentsContract.buildDocumentUriUsingTree(uri, "$rootId/${record.projectId}/${record.id}.log")
            }.getOrNull()
            val logText = logUri?.let { u ->
                runCatching {
                    context.contentResolver.openInputStream(u)?.bufferedReader()?.use { reader ->
                        val tail = StringBuilder()
                        val buffer = CharArray(8192)
                        while (true) {
                            val n = reader.read(buffer)
                            if (n < 0) break
                            tail.append(buffer, 0, n)
                            if (tail.length > 131072) tail.delete(0, tail.length - 131072)
                        }
                        tail.toString()
                    }
                }.getOrNull()
            }
            val result = logText?.lineSequence()?.lastOrNull { it.startsWith("BUILDDECK_RESULT=") }
                ?.substringAfter('=')?.substringBefore(' ').orEmpty()
            val apkUri = if (result == "SUCCESS" || record.status in setOf("SUCCESS", "SUCCESS_NEEDS_IMPORT")) {
                listedApk?.uri ?: runCatching {
                    val name = logText?.lineSequence()?.lastOrNull { it.startsWith("BUILDDECK_APK=") }
                        ?.substringAfter('=')?.substringAfterLast('/')
                        ?.takeIf { it.startsWith("${record.id}_") && it.matches(Regex("[a-f0-9-]{36}_[a-zA-Z0-9_.-]+\\.apk")) }
                    if (name != null) DocumentsContract.buildDocumentUriUsingTree(uri,
                        "${DocumentsContract.getTreeDocumentId(uri)}/${record.projectId}/$name")
                    else directApkUri(uri, record, ai)
                }.getOrNull()
            } else null
            val stage = logText?.lineSequence()
                ?.filter { it.startsWith("BUILDDECK_STAGE=") }?.lastOrNull()?.substringAfter('=').orEmpty()
            val project = store.projects().firstOrNull { it.id == record.projectId }
            if ((result == "SUCCESS" || record.status in setOf("SUCCESS", "SUCCESS_NEEDS_IMPORT")) && apkUri != null && project != null) {
                try {
                    val expected = (logText.orEmpty() + "\n" + runCatching { ai.buildLog(record) }.getOrDefault("")).lineSequence()
                        .lastOrNull { it.startsWith("BUILDDECK_APK_SHA256=") }
                        ?.substringAfter('=')?.trim()
                    require(expected != null && expected.matches(Regex("[a-f0-9]{64}"))) { "No APK integrity marker yet" }
                    val actual = context.contentResolver.openInputStream(apkUri)?.use { input ->
                        val sha = MessageDigest.getInstance("SHA-256")
                        val buffer = ByteArray(65536)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            sha.update(buffer, 0, n)
                        }
                        sha.digest().joinToString("") { "%02x".format(it) }
                    }
                    require(actual == expected) { "APK checksum mismatch" }
                    if (ai.buildRecords().firstOrNull { it.id == record.id }?.status == "READY") continue
                    store.importRelease(project, apkUri)
                    ai.completeLocalBuild(record.id, "READY", "APK saved automatically to the Vault", logText.orEmpty())
                    changed = true
                } catch (error: Exception) {
                    val note = "APK compiled; manual import needed: " + error.message.orEmpty().take(75)
                    if (record.note != note) {
                        ai.completeLocalBuild(record.id, "SUCCESS_NEEDS_IMPORT", note, logText.orEmpty())
                        changed = true
                    }
                }
            } else if (result == "FAILED" && record.status != "FAILED") {
                ai.completeLocalBuild(record.id, "FAILED", if (logText.orEmpty().contains("BUILDDECK_CANCELLED=YES")) "Cancelled by user" else "Build failed — check the log", logText.orEmpty())
                changed = true
            } else if (result == "SUCCESS" && record.status != "SUCCESS_NEEDS_IMPORT") {
                ai.completeLocalBuild(record.id, "SUCCESS_NEEDS_IMPORT", "Compiled; use manual APK import", logText.orEmpty())
                changed = true
            } else if (stage.isNotBlank() && record.note != stage && result.isBlank() &&
                record.status in setOf("SUBMITTED", "RUNNING")) {
                ai.updateBuildProgress(record.id, "RUNNING", stage)
                changed = true
            }
        }
        changed
    }
}
