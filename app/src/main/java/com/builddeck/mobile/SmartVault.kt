package com.builddeck.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

internal class SmartVault(private val context: Context) {
    private val prefs = context.getSharedPreferences("smart_vault", Context.MODE_PRIVATE)
    private val resolver = context.contentResolver
    private fun folder(key: String, write: Boolean = false): DocumentFile? {
        val uri = prefs.getString(key, null)?.let(Uri::parse) ?: return null
        if (resolver.persistedUriPermissions.none { it.uri == uri && it.isReadPermission && (!write || it.isWritePermission) }) return null
        return DocumentFile.fromTreeUri(context, uri)?.takeIf { it.isDirectory }
    }
    fun connect(key: String, uri: Uri) {
        val write = key == "destination"
        val f = DocumentFile.fromTreeUri(context, uri) ?: error("Folder unavailable")
        require(f.isDirectory && f.canRead() && (!write || f.canWrite())) { "Folder access unavailable" }
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or
            (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0))
        prefs.edit().putString(key, uri.toString()).apply()
    }
    fun name(key: String): String = folder(key, key == "destination")?.name ?: "Not selected"
    fun disconnect(key: String) { prefs.edit().remove(key).apply() }
    fun autoScan(): Boolean = prefs.getBoolean("autoScan", false)
    fun setAutoScan(value: Boolean) { prefs.edit().putBoolean("autoScan", value).apply() }
    fun export(release: Release, project: Project): String {
        val root = folder("destination", true) ?: error("Choose an APK Vault destination in Settings")
        val source = File(release.path).canonicalFile
        require(source.parentFile == File(context.filesDir, "releases").canonicalFile && source.isFile)
        val label = SmartStudio.title(project.name).replace(Regex("[^A-Za-z0-9._ -]"), "_").take(70).ifBlank { "AndroidApp" }
        val dir = root.findFile(label)?.takeIf { it.isDirectory } ?: root.createDirectory(label) ?: error("Cannot create project folder")
        val v = ApkInspector.inspect(context, source.path)?.version.orEmpty().replace(Regex("[^A-Za-z0-9._-]"), "_").take(24)
        val filename = label + "-" + (if (v.isBlank()) "" else "v$v-") + release.id.take(8) + ".apk"
        if (dir.findFile(filename) != null) return "$label/$filename"
        val dest = dir.createFile("application/vnd.android.package-archive", filename) ?: error("Cannot create APK")
        try {
            resolver.openOutputStream(dest.uri, "w")?.use { out -> source.inputStream().use { it.copyTo(out) } }
                ?: error("Cannot write APK")
            return "$label/" + (dest.name ?: filename)
        } catch (error: Exception) {
            dest.delete()
            throw error
        }
    }
    fun exportIfConfigured(release: Release, project: Project) {
        runCatching { if (folder("destination", true) != null) export(release, project) }
    }
    fun scan(store: Store): String {
        val root = folder("scan") ?: return "Choose an APK collection folder in Settings first."
        val groups = SmartStudio.groups(store, store.projects())
        val byPackage = groups.filterKeys { it.startsWith("id:") }.mapKeys { it.key.removePrefix("id:") }
        val known = store.releases().mapNotNull { r -> runCatching { SmartStudio.digest(File(r.path)) }.getOrNull() }.toMutableSet()
        var added = 0; var duplicates = 0; var skipped = 0; var checked = 0
        val queue = java.util.ArrayDeque<Pair<DocumentFile, Int>>()
        queue.add(root to 0)
        while (queue.isNotEmpty() && checked < 500) {
            val (dir, depth) = queue.removeFirst()
            for (file in runCatching { dir.listFiles() }.getOrDefault(emptyArray())) {
                if (checked >= 500) break
                checked++
                if (file.isDirectory && depth < 3) { queue.add(file to depth + 1); continue }
                if (!file.isFile || file.name?.endsWith(".apk", true) != true) continue
                if (file.length() > 500L * 1024 * 1024) { skipped++; continue }
                val temp = File.createTempFile("vault-scan-", ".apk", context.cacheDir)
                try {
                    resolver.openInputStream(file.uri)?.use { input ->
                        temp.outputStream().use { out ->
                            val buffer = ByteArray(65536); var total = 0L
                            while (true) {
                                val n = input.read(buffer); if (n < 0) break
                                total += n; require(total <= 500L * 1024 * 1024); out.write(buffer, 0, n)
                            }
                        }
                    } ?: error("Cannot read")
                    val hash = SmartStudio.digest(temp)
                    if (hash in known) { duplicates++; continue }
                    val packageId = ApkInspector.inspect(context, temp.path)?.packageId
                    val project = byPackage[packageId]?.firstOrNull()
                    if (project == null) { skipped++; continue }
                    store.importRelease(project, file.uri)
                    known.add(hash); added++
                } catch (_: Exception) { skipped++ }
                finally { temp.delete() }
            }
        }
        return "Collected $added APKs • $duplicates duplicates skipped • $skipped unmatched/unreadable" +
            if (checked >= 500) " • scan limit reached" else ""
    }
}
