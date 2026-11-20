package com.builddeck.mobile

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class RepoLink(val owner: String, val repo: String, val branch: String)
data class SyncCandidate(val path: String, val oldSha: String?, val text: String)

/** Public GitHub repo import and explicitly approved source-only GitHub sync. */
class GitHubWorkspace(private val context: Context, private val store: Store) {
    private val links = File(context.filesDir, "github_workspace").apply { mkdirs() }
    private val settings = context.getSharedPreferences("github_account", Context.MODE_PRIVATE)
    private val apiBase = "https://api.github.com"
    private val accountAlias = "builddeck-github-token-v1"

    private fun encode(x: String) = URLEncoder.encode(x, "UTF-8").replace("+", "%20")
    private fun digest(x: String) = MessageDigest.getInstance("SHA-256").digest(x.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun getKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(accountAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            accountAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }

    private fun token(): String? {
        val stored = settings.getString("ciphertext", null) ?: return null
        return runCatching {
            val bytes = Base64.getDecoder().decode(stored)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }.getOrNull()
    }
    fun connectedAccount(): String? = settings.getString("user", null)?.takeIf { token() != null }
    fun disconnectAccount() { settings.edit().clear().apply() }

    fun connectAccount(rawToken: String): String {
        val value = rawToken.trim()
        require(value.length in 20..300 && !value.contains('\n')) { "Enter a valid fine-grained GitHub access token" }
        val user = (requestJson("/user", "GET", null, value) ?: error("GitHub account not found")).getString("login")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val stored = Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()))
        settings.edit().putString("ciphertext", stored).putString("user", user).apply()
        return user
    }

    private fun connection(path: String, method: String, secret: String? = token()): HttpURLConnection {
        require(path.startsWith("/") && !path.contains("..")) { "Invalid GitHub API path" }
        val con = URL(apiBase + path).openConnection() as HttpURLConnection
        con.requestMethod = method
        con.connectTimeout = 20000
        con.readTimeout = 60000
        con.setRequestProperty("Accept", "application/vnd.github+json")
        con.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        con.setRequestProperty("User-Agent", "BuildDeck-Android")
        if (secret != null) con.setRequestProperty("Authorization", "Bearer $secret")
        return con
    }

    private fun requestJson(path: String, method: String = "GET", data: JSONObject? = null,
                            secret: String? = token(), allowMissing: Boolean = false): JSONObject? {
        val con = connection(path, method, secret)
        try {
            if (data != null) {
                con.doOutput = true
                con.setRequestProperty("Content-Type", "application/json")
                con.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = con.responseCode
            if (code == 404 && allowMissing) return null
            val raw = (if (code in 200..299) con.inputStream else con.errorStream)
                ?.bufferedReader()?.use { it.readText().take(1200000) }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(raw).optString("message") }.getOrDefault("GitHub request failed")
                throw IllegalStateException("GitHub HTTP $code: $msg")
            }
            return JSONObject(raw)
        } finally { con.disconnect() }
    }

    private fun parseSpec(spec: String): Pair<String, String> {
        val pieces = spec.trim().removePrefix("https://github.com/").trimEnd('/').split('/')
        require(pieces.size == 2 && pieces.all { it.matches(Regex("[A-Za-z0-9_.-]{1,100}")) }) {
            "Use owner/repository, such as luischairez12-ui/BuildDeck"
        }
        return pieces[0] to pieces[1]
    }

    fun link(project: Project): RepoLink? = load(project)?.let {
        RepoLink(it.getString("owner"), it.getString("repo"), it.getString("branch"))
    }

    /** Import full project ZIP and record original Git blob SHAs for conflict detection. */
    fun importRepo(spec: String, requestedBranch: String = ""): Project {
        val (owner, repo) = parseSpec(spec)
        val detail = requestJson("/repos/$owner/$repo") ?: error("Repository unavailable")
        val branch = requestedBranch.trim().ifEmpty { detail.getString("default_branch") }
        require(branch.length in 1..180 && !branch.contains("..") && !branch.startsWith('/')) { "Invalid branch" }
        val tree = requestJson("/repos/$owner/$repo/git/trees/${encode(branch)}?recursive=1")
            ?: error("Cannot inspect GitHub source tree")
        require(!tree.optBoolean("truncated")) { "Repository is too large for safe source sync" }
        val remote = JSONObject()
        val items = tree.getJSONArray("tree")
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            if (item.optString("type") == "blob") remote.put(item.getString("path"), item.getString("sha"))
        }
        val cache = File(context.cacheDir, "github").apply { mkdirs() }
        val archive = File(cache, "$repo.zip")
        val con = connection("/repos/$owner/$repo/zipball/${encode(branch)}", "GET")
        try {
            require(con.responseCode in 200..299) { "GitHub source ZIP download failed: HTTP ${con.responseCode}" }
            con.inputStream.use { input ->
                archive.outputStream().use { out ->
                    val buf = ByteArray(65536)
                    var length = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        length += n
                        require(length <= 100L * 1024 * 1024) { "Source ZIP exceeds 100 MB" }
                        out.write(buf, 0, n)
                    }
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", archive)
            val imported = store.importProject(uri)
            val project = store.renameProject(imported, repo)
            val local = JSONObject()
            store.sourceFiles(project).filter(::syncAllowed).forEach { path ->
                local.put(path, digest(store.readText(project, path)))
            }
            save(project, JSONObject().put("owner", owner).put("repo", repo).put("branch", branch)
                .put("remote", remote).put("local", local))
            return project
        } finally {
            con.disconnect()
            archive.delete()
        }
    }

    fun changedFiles(project: Project): List<SyncCandidate> {
        val state = load(project) ?: return emptyList()
        val baseline = state.optJSONObject("local") ?: JSONObject()
        val remote = state.optJSONObject("remote") ?: JSONObject()
        return store.sourceFiles(project).filter(::syncAllowed).mapNotNull { path ->
            val text = store.readText(project, path)
            if (baseline.optString(path, "!").equals(digest(text))) null
            else SyncCandidate(path, remote.optString(path).ifBlank { null }, text)
        }.take(50)
    }

    /** User approves the exact paths first. Every file checks the remote blob SHA. */
    fun pushChanges(project: Project, selectedPaths: List<String>): Int {
        val state = load(project) ?: error("This project was not imported from GitHub")
        require(connectedAccount() != null) { "Connect a fine-grained GitHub token with Contents read/write permission" }
        val link = link(project) ?: error("Repository link missing")
        val approved = selectedPaths.distinct()
        require(approved.isNotEmpty() && approved.size <= 30) { "Choose 1 to 30 files" }
        val candidates = changedFiles(project).associateBy { it.path }
        var uploaded = 0
        for (path in approved) {
            val file = candidates[path] ?: error("File changed since preview: $path")
            val current = requestJson(
                "/repos/${link.owner}/${link.repo}/contents/${path.split('/').joinToString("/") { encode(it) }}?ref=${encode(link.branch)}",
                allowMissing = true
            )
            val remoteSha = current?.optString("sha")?.ifBlank { null }
            require(remoteSha == file.oldSha) {
                "Remote conflict in $path. Reimport the repository to avoid overwriting someone else's changes."
            }
            val body = JSONObject().put("message", "BuildDeck: update $path")
                .put("branch", link.branch)
                .put("content", Base64.getEncoder().encodeToString(file.text.toByteArray(Charsets.UTF_8)))
            if (remoteSha != null) body.put("sha", remoteSha)
            val result = requestJson("/repos/${link.owner}/${link.repo}/contents/${path.split('/').joinToString("/") { encode(it) }}", "PUT", body)
                ?: error("GitHub did not return a commit")
            state.getJSONObject("remote").put(path, result.getJSONObject("content").getString("sha"))
            state.getJSONObject("local").put(path, digest(file.text))
            save(project, state)
            uploaded++
        }
        return uploaded
    }

    /** Narrow, read-only helper for the reviewed incoming GitHub bridge. */
    internal fun readGitHubJson(path: String): JSONObject =
        requestJson(path) ?: error("GitHub did not return JSON")

    internal fun readLinkState(project: Project): JSONObject? = load(project)

    internal fun writeLinkState(project: Project, value: JSONObject) = save(project, value)

    internal fun canSyncSource(path: String): Boolean = syncAllowed(path)

    private fun syncAllowed(path: String): Boolean {
        val segments = path.split('/')
        val name = segments.last().lowercase()
        if (segments.any { it in setOf(".git", ".gradle", "build", ".idea", "node_modules") }) return false
        if (name.contains("secret") || name.contains("token") || name.contains("credential") ||
            name == ".env" || name.startsWith("local.properties") || name.endsWith(".key") ||
            name.endsWith(".keystore") || name.endsWith(".jks") || name.endsWith(".pem") ||
            name.endsWith(".properties")) return false
        return name.substringAfterLast('.', "") in setOf(
            "kt","kts","java","xml","json","md","txt","gradle","yaml","yml","toml","sh"
        )
    }

    private fun save(p: Project, value: JSONObject) {
        val file = File(links, "${p.id}.json")
        val temp = File(links, "${p.id}.pending")
        temp.writeText(value.toString())
        require(temp.renameTo(file) || run { file.delete(); temp.renameTo(file) }) { "Cannot save GitHub link" }
    }
    private fun load(p: Project): JSONObject? = runCatching {
        JSONObject(File(links, "${p.id}.json").readText())
    }.getOrNull()
}
