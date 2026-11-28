package com.builddeck.mobile

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.UUID

internal object TermuxTaskBridge {
    const val PREF = "builddeck_task_manager"
    fun request(context: Context, action: String, id: String = "") {
        require(action in setOf("list", "cancel", "clean"))
        require(action != "cancel" || id.matches(Regex("[a-f0-9-]{36}")))
        val script = context.assets.open("termux_tasks.sh").bufferedReader().use { it.readText() }
        val nonce = UUID.randomUUID().toString()
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().putString("request", nonce).putLong("sent", System.currentTimeMillis())
            .putString("error", "").apply()
        val response = Intent(context, TermuxTaskReceiver::class.java).apply {
            data = Uri.parse("builddeck://task-result/$nonce")
            putExtra("nonce", nonce)
        }
        val callback = PendingIntent.getBroadcast(context, nonce.hashCode(), response,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        val command = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-s", "--", action, id))
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
