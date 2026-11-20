package com.builddeck.mobile

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Base64

data class IncomingChange(
    val path: String,
    val kind: String,
    val previousRemoteSha: String?,
    val incomingRemoteSha: String?,
    val localFingerprint: String?,
    val text: String?,
    val conflict: String?
)

data class IncomingReview(
    val link: RepoLink,
    val headSha: String,
    val changes: List<IncomingChange>,
    val skipped: Int
)

/** Read-only GitHub polling, followed by an explicit, approval-only apply step. */
class GitHubIncoming(private val store: Store, private val github: GitHubWorkspace) {
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun enc(x: String) = java.net.URLEncoder.encode(x, "UTF-8").replace("+", "%20")

    private fun snapshot(link: RepoLink): Pair<String, Map<String, String>> {
        val commit = github.readGitHubJson(
            "/repos/${link.owner}/${link.repo}/commits/${enc(link.branch)}"
        )
        val sha = commit.getString("sha")
        val treeSha = commit.getJSONObject("commit").getJSONObject("tree").getString("sha")
        val tree = github.readGitHubJson(
            "/repos/${link.owner}/${link.repo}/git/trees/$treeSha?recursive=1"
        )
        require(!tree.optBoolean("truncated")) { "GitHub returned a truncated tree. Sync is blocked." }
        val items = tree.getJSONArray("tree")
        require(items.length() <= 25000) { "Repository too large for safe mobile sync." }
        val files = linkedMapOf<String, String>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            if (item.optString("type") == "blob") files[item.getString("path")] = item.getString("sha")
        }
        return sha to files
    }

    private fun blob(link: RepoLink, sha: String): String {
        require(sha.matches(Regex("[a-f0-9]{40,64}"))) { "Invalid source SHA" }
        val raw = github.readGitHubJson("/repos/${link.owner}/${link.repo}/git/blobs/$sha")
        val size = raw.getLong("size")
        require(size in 0..1_000_000L) { "File exceeds 1 MB" }
        require(raw.optString("encoding") == "base64") { "Unsupported source encoding" }
        val bytes = Base64.getMimeDecoder().decode(raw.getString("content"))
        require(bytes.size.toLong() == size) { "Source size mismatch" }
        val result = String(bytes, Charsets.UTF_8)
        require(result.toByteArray(Charsets.UTF_8).contentEquals(bytes)) { "Non-UTF-8 source" }
        return result
    }

    fun check(project: Project): IncomingReview {
        val link = github.link(project) ?: error("Import the project from GitHub first to link it.")
        val state = github.readLinkState(project) ?: error("GitHub baseline missing")
        val previous = state.optJSONObject("remote") ?: JSONObject()
        val localBaseline = state.optJSONObject("local") ?: JSONObject()
        val (head, current) = snapshot(link)
        val paths = linkedSetOf<String>()
        val keys = previous.keys()
        while (keys.hasNext()) paths.add(keys.next())
        paths.addAll(current.keys)
        val changed = paths.filter {
            previous.optString(it).ifBlank { null } != current[it]
        }.sorted()
        require(changed.size <= 150) { "More than 150 changed files; import a fresh copy for safety." }
        val allowed = changed.filter(github::canSyncSource)
        val result = allowed.take(40).map { path ->
            val priorRemote = previous.optString(path).ifBlank { null }
            val nextRemote = current[path]
            val localFile = store.resolveSourceFile(project, path)
            val local = if (localFile.isFile && localFile.length() <= 1_000_000) {
                runCatching { store.readText(project, path) }.getOrNull()
            } else null
            val fingerprint = local?.let(::digest)
            val baseline = localBaseline.optString(path).ifBlank { null }
            var conflict = when {
                nextRemote == null -> "Deleted upstream; review deletion manually."
                localFile.exists() && !localFile.isFile -> "Local path is not a text file."
                localFile.isFile && local == null -> "Local file is too large or unreadable."
                priorRemote == null && localFile.exists() -> "Local file already exists."
                priorRemote != null && baseline == null -> "No matching local baseline."
                priorRemote != null && local == null -> "Local file was removed."
                priorRemote != null && baseline != fingerprint -> "Local edits conflict with GitHub changes."
                else -> null
            }
            val newText = if (conflict == null && nextRemote != null) {
                runCatching { blob(link, nextRemote) }.getOrElse {
                    conflict = "Source cannot be downloaded safely: ${it.message}"
                    null
                }
            } else null
            IncomingChange(
                path, when {
                    nextRemote == null -> "REMOVED"
                    priorRemote == null -> "ADDED"
                    else -> "UPDATED"
                }, priorRemote, nextRemote, fingerprint, newText, conflict
            )
        }
        return IncomingReview(link, head, result, changed.size - result.size)
    }

    /** Rechecks branch head, project files and every approved path before writes. */
    fun apply(project: Project, review: IncomingReview, selected: Set<String>): File {
        require(selected.isNotEmpty() && selected.size <= 40) { "Select 1-40 files" }
        require(github.link(project) == review.link) { "Repository link changed; review again." }
        val (headNow, current) = snapshot(review.link)
        require(headNow == review.headSha) { "GitHub updated since review. Fetch changes again." }
        val candidates = review.changes.associateBy { it.path }
        val chosen = selected.sorted().map { path ->
            val item = candidates[path] ?: error("Unreviewed path: $path")
            require(item.conflict == null && item.text != null && item.incomingRemoteSha != null) {
                "Cannot apply conflicting file: $path"
            }
            require(github.canSyncSource(path) && current[path] == item.incomingRemoteSha) {
                "Remote source has changed: $path"
            }
            val file = store.resolveSourceFile(project, path)
            val now = if (file.isFile) digest(store.readText(project, path)) else null
            require(now == item.localFingerprint) { "Local source changed since preview: $path" }
            item
        }
        val safetyCopy = store.saveSnapshot(project)
        try {
            for (item in chosen) store.writeProjectText(project, item.path, item.text!!)
            val state = github.readLinkState(project) ?: error("GitHub link disappeared")
            val remote = state.optJSONObject("remote") ?: JSONObject()
            val local = state.optJSONObject("local") ?: JSONObject()
            for (item in chosen) {
                remote.put(item.path, item.incomingRemoteSha)
                local.put(item.path, digest(item.text!!))
            }
            state.put("remote", remote).put("local", local)
            github.writeLinkState(project, state)
        } catch (error: Exception) {
            throw IllegalStateException(
                "Update interrupted. Restore saved version ${safetyCopy.name} if needed: ${error.message}",
                error
            )
        }
        return safetyCopy
    }
}
