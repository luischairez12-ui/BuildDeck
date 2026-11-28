package com.builddeck.mobile

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** A user-controlled, offline patch inbox. This is NOT a remote AI connection. */
data class AiAccess(val read: Boolean = false, val write: Boolean = false, val build: Boolean = false)

data class PatchOperation(
    val kind: String,
    val path: String,
    val content: String?,
    val expectedSha256: String?
)

data class PatchBundle(
    val requestId: String,
    val title: String,
    val summary: String,
    val projectId: String?,
    val projectName: String?,
    val requestBuild: Boolean,
    val operations: List<PatchOperation>
)

data class PatchPreview(val index: Int, val path: String, val kind: String, val before: String?,
                        val after: String?, val conflict: String?, val diff: String)

data class BuildRecord(
    val id: String,
    val projectId: String,
    val submittedAt: Long,
    val status: String,
    val logFile: String? = null,
    val note: String = ""
)

class AiControlStore(private val context: Context, private val store: Store) {
    private val settingsFile = File(context.filesDir, "ai_access.json")
    private val buildsFile = File(context.filesDir, "build_requests.json")
    private val logsDir = File(context.filesDir, "build_logs").apply { mkdirs() }

    fun access(project: Project): AiAccess {
        val json = readObject(settingsFile).optJSONObject(project.id) ?: return AiAccess()
        return AiAccess(json.optBoolean("read"), json.optBoolean("write"), json.optBoolean("build"))
    }

    fun setAccess(project: Project, policy: AiAccess) {
        val root = readObject(settingsFile)
        root.put(project.id, JSONObject().put("read", policy.read).put("write", policy.write).put("build", policy.build))
        writeSafely(settingsFile, root.toString())
    }

    fun readPatch(uri: Uri, project: Project): PatchBundle {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            // Bounded read protects against huge or malformed handoff files.
            val out = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(16384)
            while (true) {
                val n = input.read(chunk)
                if (n == -1) break
                require(out.size() + n <= 5_000_000) { "Patch bundle exceeds 5 MB" }
                out.write(chunk, 0, n)
            }
            out.toByteArray()
        } ?: error("Cannot open patch bundle")
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.optString("schema") == "builddeck.patch.v1") { "Unsupported patch schema" }
        val targetId = json.optString("projectId").ifBlank { null }
        val targetName = json.optString("projectName").ifBlank { null }
        require(targetId != null || targetName != null) { "Patch must name its target project" }
        if (targetId != null) require(targetId == project.id) { "Patch targets a different project ID" }
        if (targetName != null) require(targetName == project.name) { "Patch targets a different project name" }
        val array = json.getJSONArray("operations")
        require(array.length() in 1..50) { "Patch must include 1 to 50 file changes" }
        val seen = mutableSetOf<String>()
        val operations = (0 until array.length()).map { index ->
            val op = array.getJSONObject(index)
            val kind = op.getString("op")
            require(kind == "write" || kind == "delete") { "Unsupported operation: $kind" }
            val path = op.getString("path")
            validatePath(path)
            require(seen.add(path)) { "Duplicate patch path: $path" }
            val content = if (kind == "write") op.getString("content") else null
            require(content == null || content.toByteArray(Charsets.UTF_8).size <= 1_000_000) { "File over 1 MB: $path" }
            val expected = op.optString("expectedSha256").ifBlank { null }
            require(expected == null || expected == "absent" || expected.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid SHA256 fingerprint" }
            PatchOperation(kind, path, content, expected)
        }
        return PatchBundle(
            json.optString("requestId").take(100).ifBlank { UUID.randomUUID().toString() },
            json.optString("title").take(100).ifBlank { "Imported AI patch" },
            json.optString("summary").take(500), targetId, targetName,
            json.optBoolean("requestBuild", false), operations
        )
    }

    /** Preview MUST be recomputed just before commit to detect changed source. */
    fun preview(project: Project, bundle: PatchBundle): List<PatchPreview> = bundle.operations.mapIndexed { index, op ->
        val file = store.resolveSourceFile(project, op.path)
        val before = if (file.exists()) {
            require(file.isFile && file.length() <= 1_000_000) { "Not a small text file: ${op.path}" }
            file.readText()
        } else null
        val checksum = before?.let { digest(it.toByteArray(Charsets.UTF_8)) }
        val conflict = when {
            op.expectedSha256 == "absent" && before != null -> "Expected a new file, but it already exists"
            op.expectedSha256 != null && op.expectedSha256 != "absent" && checksum != op.expectedSha256.lowercase() -> "Source checksum changed since the patch was created"
            op.kind == "delete" && before == null -> "Cannot delete a missing file"
            op.kind == "write" && before != null && op.expectedSha256 == null -> "Missing original-file checksum; regenerate patch against current source"
            op.kind == "write" && before == null && op.expectedSha256 != "absent" -> "New file must explicitly use expectedSha256: absent"
            op.kind == "delete" && op.expectedSha256 == null -> "Delete requires an original-file checksum"
            else -> null
        }
        PatchPreview(index, op.path, op.kind, before, op.content, conflict, previewDiff(before, op.content))
    }

    fun applyPatch(project: Project, bundle: PatchBundle, selected: Set<Int>): File {
        require(access(project).write) { "Grant AI source-edit permission in this project first" }
        require(selected.isNotEmpty()) { "Select at least one file" }
        val previews = preview(project, bundle)
        require(selected.all { it in previews.indices && previews[it].conflict == null }) { "File conflicts: review the patch again" }
        val snapshot = store.saveSnapshot(project)
        try {
            // Write selected files after verifying all paths and fingerprints.
            selected.sorted().forEach { i ->
                val op = bundle.operations[i]
                when (op.kind) {
                    "write" -> store.writeProjectText(project, op.path, op.content!!)
                    "delete" -> store.deleteProjectText(project, op.path)
                }
            }
        } catch (error: Exception) {
            // Snapshot is deliberately retained for manual recovery even on partial failure.
            throw IllegalStateException("Patch did not finish. Restore snapshot ${snapshot.name} if needed: ${error.message}", error)
        }
        return snapshot
    }

    fun recordBuild(project: Project, note: String = "Submitted to Termux"): BuildRecord = synchronized(buildRecordsLock) {
        val record = BuildRecord(UUID.randomUUID().toString(), project.id, System.currentTimeMillis(), "SUBMITTED", note = note)
        writeBuilds(buildRecords() + record)
        record
    }

    fun buildRecords(projectId: String? = null): List<BuildRecord> = readArray(buildsFile).mapNotNull { raw ->
        runCatching { BuildRecord(raw.getString("id"), raw.getString("projectId"),
            raw.getLong("submittedAt"), raw.optString("status", "SUBMITTED"),
            raw.optString("logFile").ifBlank { null }, raw.optString("note")) }.getOrNull()
    }.filter { projectId == null || it.projectId == projectId }.sortedByDescending { it.submittedAt }

    /** Official Termux callback result; never equate 'submitted' with 'built'. */
    fun completeLocalBuild(recordId: String, state: String, detail: String, console: String): BuildRecord? = synchronized(buildRecordsLock) {
        require(state in setOf("READY", "SUCCESS_NEEDS_IMPORT", "FAILED"))
        val records = buildRecords().toMutableList()
        val index = records.indexOfFirst { it.id == recordId }
        if (index == -1) return@synchronized null
        if (records[index].status == "READY" && state != "READY") return@synchronized records[index]
        val logFile = File(logsDir, "$recordId.log")
        val deliveredLog = File(File(context.filesDir, "builddeck_results"), "$recordId.log")
        if (deliveredLog.isFile && deliveredLog.length() in 1L..4_000_000L) {
            deliveredLog.copyTo(logFile, overwrite = true)
        } else {
            logFile.writeText(console.takeLast(120_000))
        }
        val updated = records[index].copy(
            status = state,
            logFile = logFile.absolutePath,
            note = detail.take(240)
        )
        records[index] = updated
        writeBuilds(records)
        updated
    }

    fun updateBuildProgress(recordId: String, status: String, note: String): Unit = synchronized(buildRecordsLock) {
        require(status in setOf("SUBMITTED", "RUNNING", "SUCCESS_NEEDS_IMPORT"))
        val records = buildRecords().toMutableList()
        val index = records.indexOfFirst { it.id == recordId }
        if (index == -1 || records[index].status in setOf("READY", "FAILED")) return@synchronized
        if (records[index].status == "SUCCESS_NEEDS_IMPORT" && status != "SUCCESS_NEEDS_IMPORT") return@synchronized
        records[index] = records[index].copy(status = status, note = note.take(240))
        writeBuilds(records)
    }

    /** Explicitly clear an obsolete request; never mislabel it as a failed build. */
    fun dismissBuild(recordId: String) = synchronized(buildRecordsLock) {
        val existing = buildRecords()
        writeBuilds(existing.filterNot { it.id == recordId })
    }

    /**
     * Clean the visible history for this project only.
     * Never remove in-progress or needs-import requests, saved APKs, or source backups.
     */
    fun clearCompletedHistory(projectId: String): Int = synchronized(buildRecordsLock) {
        val existing = buildRecords()
        val after = existing.filterNot { record ->
            record.projectId == projectId && record.status in setOf("READY", "FAILED")
        }
        val removed = existing.size - after.size
        if (removed > 0) writeBuilds(after)
        removed
    }

    fun importBuildLog(recordId: String, uri: Uri): BuildRecord = synchronized(buildRecordsLock) {
        val records = buildRecords().toMutableList()
        val i = records.indexOfFirst { it.id == recordId }
        require(i != -1) { "Build request not found" }
        val output = File(logsDir, "${recordId}.log")
        context.contentResolver.openInputStream(uri)?.use { input ->
            output.outputStream().use { dest ->
                val chunk = ByteArray(16_384)
                var total = 0L
                while (true) {
                    val n = input.read(chunk)
                    if (n == -1) break
                    total += n
                    require(total <= 4_000_000) { "Build log exceeds 4 MB" }
                    dest.write(chunk, 0, n)
                }
            }
        } ?: error("Cannot open build log")
        val text = output.readText()
        val status = when {
            text.contains("BUILDDECK_RESULT=SUCCESS") -> "SUCCESS"
            text.contains("BUILDDECK_RESULT=FAILED") -> "FAILED"
            else -> "LOG_IMPORTED"
        }
        val updated = records[i].copy(status = status, logFile = output.absolutePath)
        records[i] = updated
        writeBuilds(records)
        updated
    }

    fun buildLog(record: BuildRecord): String {
        val path = record.logFile ?: return "No log imported yet"
        val file = File(path)
        require(file.canonicalFile.parentFile == logsDir.canonicalFile) { "Invalid log location" }
        return file.readText().takeLast(60_000)
    }

    private fun readObject(file: File): JSONObject = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
    private fun readArray(file: File): List<JSONObject> = runCatching {
        val root = JSONArray(file.readText()); (0 until root.length()).map { root.getJSONObject(it) }
    }.getOrDefault(emptyList())
    private fun writeBuilds(records: List<BuildRecord>) {
        val root = JSONArray()
        records.forEach { root.put(JSONObject().put("id", it.id).put("projectId", it.projectId)
            .put("submittedAt", it.submittedAt).put("status", it.status)
            .put("logFile", it.logFile ?: "").put("note", it.note)) }
        writeSafely(buildsFile, root.toString())
    }
    private fun writeSafely(dest: File, text: String) {
        val temporary = File(dest.parentFile, dest.name + ".pending")
        temporary.writeText(text)
        require(temporary.renameTo(dest)) { "Cannot save BuildDeck record" }
    }

    private fun validatePath(path: String) {
        require(path.isNotBlank() && path.length <= 240 && !path.startsWith('/') && '\\' !in path && ':' !in path) { "Unsafe file path" }
        val segments = path.split('/')
        require(segments.all { it.isNotBlank() && it != "." && it != ".." && it !in setOf(".git", ".gradle", ".idea", "build", "node_modules") }) { "Disallowed path segment" }
        val name = segments.last().lowercase()
        require(name !in setOf("local.properties", ".env", "gradle-wrapper.jar", "keystore.properties")
            && !name.endsWith(".jks") && !name.endsWith(".keystore") && !name.endsWith(".pem") && !name.endsWith(".key")) { "Sensitive or binary files cannot be edited through AI patches" }
        val extension = name.substringAfterLast('.', "")
        require(extension in setOf("kt", "java", "kts", "xml", "json", "md", "txt", "properties", "gradle", "yaml", "yml", "sh", "gitignore", "toml")) { "AI patches only support reviewed text files" }
    }

    companion object {
        private val buildRecordsLock = Any()
        fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

        private fun previewDiff(before: String?, after: String?): String {
            val old = before?.lines() ?: emptyList()
            val newer = after?.lines() ?: emptyList()
            var prefix = 0
            while (prefix < minOf(old.size, newer.size) && old[prefix] == newer[prefix]) prefix++
            var suffix = 0
            while (suffix < minOf(old.size, newer.size) - prefix &&
                old[old.size - 1 - suffix] == newer[newer.size - 1 - suffix]) suffix++
            val oldChanged = old.subList(prefix, old.size - suffix).take(70)
            val newChanged = newer.subList(prefix, newer.size - suffix).take(70)
            val header = "@@ around line ${prefix + 1} @@\n"
            val changes = oldChanged.joinToString("\n") { "- $it" } + "\n" + newChanged.joinToString("\n") { "+ $it" }
            val trailing = if (old.size + newer.size - 2 * suffix - 2 * prefix > 140) "\n… preview truncated" else ""
            return (header + changes + trailing).take(20_000)
        }
    }
}
