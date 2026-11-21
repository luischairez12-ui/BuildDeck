package com.builddeck.mobile

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class Project(val id: String, val name: String, val rootRelative: String, val created: Long)
data class Release(val id: String, val projectId: String, val name: String, val path: String, val added: Long)

data class BuildHandoff(val projectId: String, val zipName: String)
data class Snapshot(val fileName: String, val created: Long)

/** Reversible removal from the active Projects list; source data stays on-device. */
data class ArchivedProject(
    val project: Project,
    val archivedAt: Long,
    val releaseIds: List<String>
)

/** Local-only project storage. Nothing is uploaded to a server or GitHub. */
class Store(private val context: Context) {
    private val root = File(context.filesDir, "projects").apply { mkdirs() }
    private val releaseRoot = File(context.filesDir, "releases").apply { mkdirs() }
    private val snapshotRoot = File(context.filesDir, "snapshots").apply { mkdirs() }
    private val projectIndex = File(context.filesDir, "projects.json")
    private val releaseIndex = File(context.filesDir, "releases.json")
    private val archivedRoot = File(context.filesDir, "archived_projects").apply { mkdirs() }
    private val archivedIndex = File(context.filesDir, "archived_projects.json")

    fun projects(): List<Project> = readArray(projectIndex).mapNotNull { item ->
        runCatching {
            Project(item.getString("id"), item.getString("name"), item.optString("rootRelative"), item.getLong("created"))
        }.getOrNull()
    }.sortedByDescending { it.created }

    fun archivedProjects(): List<ArchivedProject> =
        readArray(archivedIndex).mapNotNull { row ->
            runCatching {
                val project = Project(row.getString("id"), row.getString("name"),
                    row.optString("rootRelative"), row.getLong("created"))
                val ids = row.optJSONArray("releaseIds") ?: JSONArray()
                ArchivedProject(project, row.getLong("archivedAt"),
                    (0 until ids.length()).map { ids.getString(it) })
            }.getOrNull()
        }.sortedByDescending { it.archivedAt }

    /**
     * Keep exactly one source project visible. This does not delete APKs,
     * snapshots, build logs, linked GitHub metadata, or the original source.
     * Removed sources are moved into private, reversible local archive storage.
     */
    fun archiveOtherProjects(keepId: String): Int {
        val active = projects()
        val keeper = active.firstOrNull { it.id == keepId }
            ?: error("The project to keep was not found")
        require(isGradleProject(keeper)) {
            "Choose a real Gradle source project, preferably the GitHub-linked BuildDeck."
        }
        val old = active.filterNot { it.id == keepId }
        for (project in old) archiveProject(project, keeper)
        return old.size
    }

    private fun archiveProject(project: Project, keeper: Project) {
        val source = File(root, project.id)
        val archive = File(archivedRoot, project.id)
        require(source.isDirectory) { "Project source missing: ${project.name}" }
        require(!archive.exists() && archivedProjects().none { it.project.id == project.id }) {
            "An archived project with that ID already exists"
        }
        val existingProjects = projects()
        require(existingProjects.any { it.id == project.id } && existingProjects.any { it.id == keeper.id }) {
            "Project list changed; reopen the cleanup screen"
        }
        val previousReleases = releases()
        val previousArchives = archivedProjects()
        val attached = previousReleases.filter { it.projectId == project.id }.map { it.id }
        val entry = ArchivedProject(project, System.currentTimeMillis(), attached)
        require(source.renameTo(archive)) { "Could not archive sources for ${project.name}" }
        try {
            saveArchivedProjects(previousArchives + entry)
            // Existing APKs stay visible in the Vault under the one surviving project.
            saveReleases(previousReleases.map {
                if (it.projectId == project.id) it.copy(projectId = keeper.id) else it
            })
            saveProjects(existingProjects.filterNot { it.id == project.id })
        } catch (error: Exception) {
            runCatching { saveProjects(existingProjects) }
            runCatching { saveReleases(previousReleases) }
            runCatching { saveArchivedProjects(previousArchives) }
            archive.renameTo(source)
            throw IllegalStateException("Could not archive ${project.name}: ${error.message}", error)
        }
    }

    /** Restore the archived project with its original ID, GitHub link and APK ownership. */
    fun restoreArchivedProject(id: String): Project {
        val entry = archivedProjects().firstOrNull { it.project.id == id }
            ?: error("Archive entry not found")
        require(projects().none { it.id == id }) { "An active project with that ID already exists" }
        val archive = File(archivedRoot, id)
        val destination = File(root, id)
        require(archive.isDirectory && !destination.exists()) { "Archive files are unavailable" }
        val previousProjects = projects()
        val previousReleases = releases()
        val previousArchives = archivedProjects()
        require(archive.renameTo(destination)) { "Could not move archived source back" }
        try {
            saveProjects(previousProjects + entry.project)
            saveReleases(previousReleases.map {
                if (it.id in entry.releaseIds) it.copy(projectId = id) else it
            })
            saveArchivedProjects(previousArchives.filterNot { it.project.id == id })
        } catch (error: Exception) {
            runCatching { saveProjects(previousProjects) }
            runCatching { saveReleases(previousReleases) }
            runCatching { saveArchivedProjects(previousArchives) }
            destination.renameTo(archive)
            throw IllegalStateException("Could not restore ${entry.project.name}: ${error.message}", error)
        }
        return entry.project
    }

    private fun saveArchivedProjects(items: List<ArchivedProject>) {
        val array = JSONArray()
        items.forEach { entry ->
            val project = entry.project
            val releases = JSONArray()
            entry.releaseIds.forEach { releases.put(it) }
            array.put(JSONObject().put("id", project.id).put("name", project.name)
                .put("rootRelative", project.rootRelative).put("created", project.created)
                .put("archivedAt", entry.archivedAt).put("releaseIds", releases))
        }
        val temporary = File(context.filesDir, "archived_projects.json.pending")
        temporary.writeText(array.toString())
        require(temporary.renameTo(archivedIndex) || run {
            archivedIndex.delete(); temporary.renameTo(archivedIndex)
        }) { "Cannot update archived project index" }
    }

    fun renameProject(project: Project, newName: String): Project {
        val renamed = project.copy(name = newName.trim().take(60))
        require(renamed.name.isNotBlank())
        val projects = projects().map { if (it.id == project.id) renamed else it }
        saveProjects(projects)
        return renamed
    }

    fun releases(projectId: String? = null): List<Release> = readArray(releaseIndex).mapNotNull { item ->
        runCatching {
            Release(item.getString("id"), item.getString("projectId"), item.getString("name"), item.getString("path"), item.getLong("added"))
        }.getOrNull()
    }.filter { projectId == null || it.projectId == projectId }.sortedByDescending { it.added }

    fun importProject(uri: Uri): Project {
        val id = UUID.randomUUID().toString()
        val destination = File(root, id).apply { mkdirs() }
        val source = File(destination, "source").apply { mkdirs() }
        val maxUncompressed = 512L * 1024 * 1024
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open source ZIP")
            var count = 0
            var total = 0L
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    count++
                    require(count <= 15_000) { "ZIP contains too many files" }
                    val name = entry.name.replace('\\', '/')
                    require(!name.startsWith('/') && !name.contains("../") && !name.startsWith("../") && !name.contains(':')) { "Unsafe path in ZIP" }
                    val output = File(source, name)
                    require(output.canonicalPath.startsWith(source.canonicalPath + File.separator)) { "ZIP path escapes project" }
                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else {
                        output.parentFile?.mkdirs()
                        FileOutputStream(output).use { out ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val n = zip.read(buffer)
                                if (n < 0) break
                                total += n
                                require(total <= maxUncompressed) { "Uncompressed ZIP is over 512 MB" }
                                out.write(buffer, 0, n)
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            require(count > 0) { "Empty ZIP" }
            val gradleSettings = source.walkTopDown().maxDepth(4)
                .firstOrNull { it.isFile && (it.name == "settings.gradle" || it.name == "settings.gradle.kts") }
            if (gradleSettings == null) {
                val isCompiledApk = File(source, "classes.dex").exists() ||
                    File(source, "resources.arsc").exists() ||
                    File(source, "classes2.dex").exists()
                val reason = if (isCompiledApk) {
                    "This archive looks like a compiled APK, not source code. " +
                        "Import the APK under Releases, or import the original Gradle repository through GitHub Workspace."
                } else {
                    "Missing settings.gradle or settings.gradle.kts. " +
                        "Import the complete Android Gradle source ZIP, or use GitHub Workspace to import the repository."
                }
                error(reason)
            }
            val projectRoot = gradleSettings.parentFile ?: source
            val relative = if (projectRoot == source) "" else projectRoot.relativeTo(source).invariantSeparatorsPath
            val display = displayName(uri).substringBeforeLast('.').take(48).ifBlank { "Imported project" }
            val project = Project(id, projectRoot.name.takeIf { it != "source" } ?: display, relative, System.currentTimeMillis())
            val index = projects().toMutableList().apply { add(project) }
            saveProjects(index)
            return project
        } catch (ex: Exception) {
            destination.deleteRecursively()
            throw ex
        }
    }

    fun sourceDir(project: Project): File = File(File(root, project.id), "source").let {
        if (project.rootRelative.isEmpty()) it else File(it, project.rootRelative)
    }

    fun isGradleProject(project: Project): Boolean {
        val dir = sourceDir(project)
        return File(dir, "settings.gradle.kts").isFile || File(dir, "settings.gradle").isFile
    }

    fun sourceFiles(project: Project): List<String> {
        val home = sourceDir(project)
        val skipped = setOf(".git", ".gradle", "build", "node_modules", ".idea")
        val allowed = setOf("kt", "kts", "java", "xml", "json", "md", "txt", "properties", "gradle", "yaml", "yml", "sh")
        return home.walkTopDown().onEnter { it == home || it.name !in skipped }
            .filter { it.isFile && it.length() <= 1_000_000L && (it.extension.lowercase() in allowed || it.name == "gradlew") }
            .take(500)
            .map { it.relativeTo(home).invariantSeparatorsPath }
            .toList().sorted()
    }

    fun readText(project: Project, relativePath: String): String {
        val file = safeFile(project, relativePath)
        require(file.isFile && file.length() <= 1_000_000) { "Not an editable text file" }
        return file.readText()
    }

    fun saveText(project: Project, relativePath: String, value: String) {
        require(value.toByteArray(Charsets.UTF_8).size <= 1_000_000) { "Editor limit is 1 MB" }
        val file = safeFile(project, relativePath)
        require(file.isFile) { "File not found" }
        file.writeText(value)
    }

    private fun safeFile(project: Project, relativePath: String): File {
        require(relativePath.isNotBlank() && !relativePath.startsWith('/')) { "Invalid file path" }
        val home = sourceDir(project)
        return File(home, relativePath).also {
            require(it.canonicalPath.startsWith(home.canonicalPath + File.separator)) { "Path escapes project" }
        }
    }

    /** Resolve only source-relative paths; AI patches have additional path restrictions. */
    fun resolveSourceFile(project: Project, relativePath: String): File = safeFile(project, relativePath)

    fun writeProjectText(project: Project, relativePath: String, value: String) {
        require(value.toByteArray(Charsets.UTF_8).size <= 1_000_000) { "AI edit exceeds 1 MB" }
        val file = safeFile(project, relativePath)
        file.parentFile?.mkdirs()
        val staged = File(file.parentFile, ".${file.name}.builddeck-pending")
        staged.writeText(value)
        require(staged.renameTo(file) || run { file.delete(); staged.renameTo(file) }) { "Cannot update $relativePath" }
    }

    fun deleteProjectText(project: Project, relativePath: String) {
        val file = safeFile(project, relativePath)
        require(file.isFile && file.delete()) { "Cannot delete $relativePath" }
    }

    fun saveSnapshot(project: Project): File {
        val file = File(snapshotRoot, "${project.id}_${System.currentTimeMillis()}.zip")
        zipSource(project, file)
        return file
    }

    fun snapshots(project: Project): List<Snapshot> = snapshotRoot.listFiles()
        ?.filter { it.name.startsWith(project.id + "_") && it.extension == "zip" }
        ?.map { Snapshot(it.name, it.lastModified()) }
        ?.sortedByDescending { it.created } ?: emptyList()

    /** Restoring creates an automatic safety snapshot of the current source first. */
    fun restoreSnapshot(project: Project, snapshot: Snapshot) {
        require(snapshot.fileName.startsWith(project.id + "_") && snapshot.fileName.endsWith(".zip")) { "Invalid snapshot" }
        val archive = File(snapshotRoot, snapshot.fileName)
        require(archive.isFile && archive.canonicalFile.parentFile == snapshotRoot.canonicalFile) { "Snapshot missing" }
        val projectStorage = File(root, project.id)
        val temporary = File(projectStorage, "restoring-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            var total = 0L
            var count = 0
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    count++
                    require(count <= 15_000) { "Snapshot has too many files" }
                    val name = entry.name.replace('\\', '/')
                    require(!name.startsWith('/') && !name.contains("../") && !name.startsWith("../") && !name.contains(':')) { "Unsafe snapshot path" }
                    val output = File(temporary, name)
                    require(output.canonicalPath.startsWith(temporary.canonicalPath + File.separator)) { "Path escapes snapshot" }
                    if (entry.isDirectory) output.mkdirs() else {
                        output.parentFile?.mkdirs()
                        FileOutputStream(output).use { out ->
                            val bytes = ByteArray(32 * 1024)
                            while (true) {
                                val n = zip.read(bytes)
                                if (n < 0) break
                                total += n
                                require(total <= 512L * 1024 * 1024) { "Snapshot exceeds 512 MB" }
                                out.write(bytes, 0, n)
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            require(count > 0) { "Snapshot is empty" }
            saveSnapshot(project)
            val home = sourceDir(project)
            val backup = File(projectStorage, "previous-${System.currentTimeMillis()}")
            require(home.renameTo(backup)) { "Couldn't stage current source" }
            if (!temporary.renameTo(home)) {
                backup.renameTo(home)
                error("Couldn't restore the selected source snapshot")
            }
            backup.deleteRecursively()
        } finally { temporary.deleteRecursively() }
    }

    fun exportSource(project: Project, uri: Uri) {
        val temporary = File(context.cacheDir, "${project.id}_export.zip")
        try {
            zipSource(project, temporary)
            context.contentResolver.openOutputStream(uri, "w")?.use { out -> temporary.inputStream().use { it.copyTo(out) } }
                ?: error("Unable to write export file")
        } finally { temporary.delete() }
    }

    fun handoffForLocalBuild(project: Project): BuildHandoff {
        require(isGradleProject(project)) {
            "This project has no Gradle settings file. Import the original source repository through GitHub Workspace; don't import a compiled APK as source."
        }
        val stamp = System.currentTimeMillis()
        val zipName = "${project.id}_${stamp}.zip"
        val temporary = File(context.cacheDir, zipName)
        try {
            zipSource(project, temporary)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, zipName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BuildDeck/Inbox")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Cannot create Downloads ZIP")
            try {
                resolver.openOutputStream(uri)?.use { out -> temporary.inputStream().use { it.copyTo(out) } }
                    ?: error("Cannot write build bundle")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (ex: Exception) {
                resolver.delete(uri, null, null)
                throw ex
            }
            return BuildHandoff(project.id, zipName)
        } finally { temporary.delete() }
    }

    fun importRelease(project: Project, uri: Uri): Release {
        val name = displayName(uri)
        require(name.lowercase().endsWith(".apk")) { "Select an APK file" }
        val id = UUID.randomUUID().toString()
        val dest = File(releaseRoot, "$id.apk")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var size = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        size += n
                        require(size <= 500L * 1024 * 1024) { "APK exceeds 500 MB" }
                        output.write(buffer, 0, n)
                    }
                }
            } ?: error("Could not open APK")
            require(dest.length() > 100) { "APK is empty or invalid" }
            val release = Release(id, project.id, name, dest.absolutePath, System.currentTimeMillis())
            saveReleases(releases() + release)
            return release
        } catch (ex: Exception) {
            dest.delete()
            throw ex
        }
    }

    /** Imports the APK deposited by Termux into this app's write-granted FileProvider. */
    fun importLocalRelease(project: Project, incoming: File, suggestedName: String): Release {
        val inbox = File(context.filesDir, "builddeck_results").canonicalFile
        require(incoming.canonicalFile.parentFile == inbox) { "APK is outside the authorized result inbox" }
        require(incoming.isFile && incoming.length() in 101L..(500L * 1024 * 1024)) { "No valid APK was transferred" }
        incoming.inputStream().use { input ->
            require(input.read() == 'P'.code && input.read() == 'K'.code) { "Invalid APK archive" }
        }
        val id = UUID.randomUUID().toString()
        val target = File(releaseRoot, "$id.apk")
        try {
            incoming.copyTo(target, overwrite = false)
            val safeName = suggestedName.substringAfterLast('/').take(100).ifBlank { "local-debug.apk" }
            val name = if (safeName.endsWith(".apk", ignoreCase = true)) safeName else "$safeName.apk"
            val release = Release(id, project.id, name, target.absolutePath, System.currentTimeMillis())
            saveReleases(releases() + release)
            return release
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    private fun zipSource(project: Project, destination: File) {
        val source = sourceDir(project)
        require(source.isDirectory) { "Project sources missing" }
        ZipOutputStream(FileOutputStream(destination).buffered()).use { zip ->
            source.walkTopDown()
                .onEnter { it == source || it.name !in setOf(".git", ".gradle", "build", ".idea", "node_modules") }
                .filter { it.isFile && it.canonicalPath.startsWith(source.canonicalPath + File.separator) }
                .filter { file ->
                    val name = file.name.lowercase()
                    name !in setOf("local.properties", ".env", "keystore.properties") &&
                        !name.endsWith(".jks") && !name.endsWith(".keystore") &&
                        !name.endsWith(".pem") && !name.endsWith(".key")
                }
                .forEach { file ->
                    val entry = ZipEntry(file.relativeTo(source).invariantSeparatorsPath)
                    zip.putNextEntry(entry)
                    FileInputStream(file).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }

    private fun displayName(uri: Uri): String = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: (uri.lastPathSegment?.substringAfterLast('/') ?: "Imported")

    private fun readArray(file: File): List<JSONObject> = runCatching {
        val data = JSONArray(file.readText())
        (0 until data.length()).map { data.getJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun saveProjects(items: List<Project>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("rootRelative", it.rootRelative).put("created", it.created)) }
        projectIndex.writeText(array.toString())
    }

    private fun saveReleases(items: List<Release>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("id", it.id).put("projectId", it.projectId).put("name", it.name).put("path", it.path).put("added", it.added)) }
        releaseIndex.writeText(array.toString())
    }
}
