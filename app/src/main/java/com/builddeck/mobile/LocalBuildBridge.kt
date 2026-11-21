package com.builddeck.mobile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

/**
 * Sends the vetted, bundled shell builder to Termux. No network service is exposed.
 * Termux (not BuildDeck) owns the SDK, Gradle and signing key.
 */
object LocalBuildBridge {
    fun start(context: Context, handoff: BuildHandoff, recordId: String) {
        val script = context.assets.open("termux_builder.sh").bufferedReader().use { it.readText() }
        val handoffDir = File(context.filesDir, "builddeck_results").apply { mkdirs() }
        // Prevent stale bytes being mistaken for a fresh result.
        listOf("apk", "log", "stage").forEach { suffix ->
            File(handoffDir, "$recordId.$suffix").delete()
        }
        val transfer = BuildTransferProvider.issue(context, recordId)

        // Each invocation has its own one-shot callback; Termux returns exitCode/stdout/stderr.
        val response = Intent(context, LocalBuildResultReceiver::class.java).apply {
            putExtra(LocalBuildResultReceiver.EXTRA_RECORD, recordId)
            data = Uri.parse("builddeck://local-result/$recordId")
        }
        val callback = PendingIntent.getBroadcast(
            context, recordId.hashCode(), response,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
        )

        val command = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf(
                "-s", "--", handoff.projectId, handoff.zipName,
                transfer.getValue("apk"), transfer.getValue("log"), recordId, transfer.getValue("stage")
            ))
            putExtra("com.termux.RUN_COMMAND_STDIN", script)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback)
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "BuildDeck local APK")
        }
        try {
            checkNotNull(context.startService(command)) { "Termux command service unavailable" }
        } catch (error: Exception) {
            BuildTransferProvider.revoke(context, recordId)
            callback.cancel()
            throw error
        }
    }
}
