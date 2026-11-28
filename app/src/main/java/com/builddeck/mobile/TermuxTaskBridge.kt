package com.builddeck.mobile

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.UUID
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import android.os.ParcelFileDescriptor

internal object TermuxTaskBridge {
    const val PREF = "builddeck_task_manager"

    private fun report(context: Context, nonce: String): File = File(context.filesDir, "termux_task_snapshot_$nonce")

    fun openSnapshot(context: Context, nonce: String, secret: String): ParcelFileDescriptor {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val expected = prefs.getString("secret", null) ?: throw FileNotFoundException("No task request")
        if (!nonce.matches(Regex("[a-f0-9-]{36}")) || nonce != prefs.getString("request", null) ||
            System.currentTimeMillis() - prefs.getLong("sent", 0L) !in 0L..120_000L ||
            !MessageDigest.isEqual(expected.toByteArray(), secret.toByteArray())) throw FileNotFoundException("Unauthorized task snapshot")
        return ParcelFileDescriptor.open(report(context, nonce), ParcelFileDescriptor.MODE_CREATE or
            ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY)
    }

    fun privateSnapshot(context: Context): Pair<String, Long>? {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val nonce = prefs.getString("request", null)?.takeIf { it.matches(Regex("[a-f0-9-]{36}")) } ?: return null
        val file = report(context, nonce)
        return runCatching {
            if (!file.isFile || file.length() !in 1L..128_000L) null else {
                val text = file.readText()
                if (text.trimEnd().endsWith("END|$nonce")) text to file.lastModified() else null
            }
        }.getOrNull()
    }
    fun request(context: Context, action: String, id: String = "") {
        require(action in setOf("list", "cancel", "clean"))
        require(action != "cancel" || id.matches(Regex("[a-f0-9-]{36}")))
        val script = context.assets.open("termux_tasks.sh").bufferedReader().use { it.readText() }
        val nonce = UUID.randomUUID().toString()
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val secretBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes)
        val previous = prefs.getString("request", null)
        if (previous != null && previous.matches(Regex("[a-f0-9-]{36}"))) {
            privateSnapshot(context)?.let { (text, at) -> prefs.edit().putString("snapshot", text).putLong("received", at).apply() }
            report(context, previous).delete()
        }
        check(prefs.edit().putString("request", nonce).putString("secret", secret).putLong("sent", System.currentTimeMillis())
            .putString("error", "").commit())
        val uri = "content://${context.packageName}.handoff/task/$nonce/$secret"
        val response = Intent(context, TermuxTaskReceiver::class.java).apply {
            data = Uri.parse("builddeck://task-result/$nonce")
            putExtra("nonce", nonce)
        }
        val callback = PendingIntent.getBroadcast(context, nonce.hashCode(), response,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        val command = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-s", "--", action, id, nonce, uri))
            putExtra("com.termux.RUN_COMMAND_STDIN", script)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback)
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "BuildDeck task manager")
        }
        try {
            checkNotNull(context.startForegroundService(command)) { "Termux command service unavailable" }
        } catch (error: Exception) {
            callback.cancel()
            prefs.edit().putString("error", error.message.orEmpty()).putLong("received", System.currentTimeMillis()).apply()
            throw error
        }
    }
}

class TermuxTaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(TermuxTaskBridge.PREF, Context.MODE_PRIVATE)
        if (intent.getStringExtra("nonce") != prefs.getString("request", null)) return
        val result = intent.getBundleExtra("result")
        val code = result?.getInt("exitCode", -1) ?: -1
        val editor = prefs.edit().putLong("received", System.currentTimeMillis())
        if (code == 0) editor.putString("snapshot", result?.getString("stdout").orEmpty().takeLast(64000))
            .putString("error", "")
        else editor.putString("error", result?.getString("errmsg") ?: "Termux did not return a successful task snapshot")
        editor.apply()
    }
}

internal data class ManagedTermuxJob(val id: String, val phase: String, val process: String, val projectId: String)
internal fun managedJobs(snapshot: String): List<ManagedTermuxJob> = snapshot.lineSequence().mapNotNull {
    val fields = it.split('|')
    if (fields.size == 5 && fields[0] == "JOB" && fields[1].matches(Regex("[a-f0-9-]{36}")) &&
        fields[4].matches(Regex("[a-f0-9-]{36}"))) ManagedTermuxJob(fields[1], fields[2], fields[3], fields[4])
    else null
}.toList()
