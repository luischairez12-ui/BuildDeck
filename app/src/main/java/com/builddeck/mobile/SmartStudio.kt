package com.builddeck.mobile

import android.content.Context
import java.io.File
import java.util.Locale
import java.security.MessageDigest

internal object SmartStudio {
    private val versionPattern = Regex("(?i)(?:^|[ _-])v?(\\d+\\.\\d+(?:\\.\\d+){0,2})(?=$|[ _-])")
    fun version(name: String): String? = versionPattern.find(name)?.groupValues?.get(1)
    fun title(name: String): String {
        val base = name.substringBeforeLast(".zip", name)
        val cut = versionPattern.find(base)?.range?.first ?: base.length
        return base.substring(0, cut).replace(Regex("(?i)[ _-]+source$"), "")
            .replace(Regex("[_-]+"), " ").trim().ifBlank { name }
    }
    fun packageId(store: Store, project: Project): String? {
        val root = store.sourceDir(project)
        val files = listOf("app/build.gradle.kts", "app/build.gradle", "build.gradle.kts", "build.gradle")
        val pattern = Regex("""\bapplicationId\s*(?:=\s*)?["']([a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+)["']""")
        for (path in files) {
            val file = File(root, path)
            if (!file.isFile || file.length() > 1_000_000) continue
            val value = runCatching { pattern.find(file.readText())?.groupValues?.get(1) }.getOrNull()
            if (value != null) return value
        }
        return null
    }
    fun key(store: Store, project: Project): String =
        packageId(store, project)?.let { "id:$it" }
            ?: "name:" + title(project.name).lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    private fun parts(p: Project): List<Long> = version(p.name)?.split('.')?.map { it.toLongOrNull() ?: 0L }.orEmpty()
    fun ordered(projects: List<Project>): List<Project> = projects.sortedWith { a, b ->
        val x = parts(a); val y = parts(b)
        if (x.isEmpty() || y.isEmpty()) {
            if (x.isEmpty() != y.isEmpty()) { if (x.isEmpty()) 1 else -1 } else b.created.compareTo(a.created)
        } else {
            var result = 0
            for (i in 0 until maxOf(x.size, y.size)) {
                result = y.getOrElse(i) { 0L }.compareTo(x.getOrElse(i) { 0L })
                if (result != 0) break
            }
            if (result == 0) b.created.compareTo(a.created) else result
        }
    }
    fun groups(store: Store, projects: List<Project>): Map<String, List<Project>> =
        projects.groupBy { key(store, it) }.mapValues { ordered(it.value) }
    fun prefs(context: Context) = context.getSharedPreferences("smart_studio", Context.MODE_PRIVATE)
    fun favorites(context: Context): Set<String> = prefs(context).getStringSet("favorites", emptySet())?.toSet().orEmpty()
    fun toggleFavorite(context: Context, key: String): Set<String> {
        val old = favorites(context)
        val next = if (key in old) old - key else old + key
        prefs(context).edit().putStringSet("favorites", next).apply()
        return next
    }
    fun protected(context: Context): Set<String> = prefs(context).getStringSet("protected", emptySet())?.toSet().orEmpty()
    fun toggleProtected(context: Context, id: String): Set<String> {
        val old = protected(context)
        val next = if (id in old) old - id else old + id
        prefs(context).edit().putStringSet("protected", next).apply()
        return next
    }
    fun size(file: File): Long = if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()
    fun digest(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; md.update(buffer, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
