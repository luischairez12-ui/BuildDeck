package com.builddeck.mobile

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Receives a one-shot, explicit PendingIntent result from Termux.
 * No public receiver and no network endpoint. Never runs commands on its own.
 */
class LocalBuildResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recordId = intent.getStringExtra(EXTRA_RECORD) ?: return
        if (!recordId.matches(Regex("[a-f0-9-]{36}"))) return
        val bundle = intent.getBundleExtra("result")
        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                val store = Store(appContext)
                val ai = AiControlStore(appContext, store)
                val record = ai.buildRecords().firstOrNull { it.id == recordId }
                if (record != null) {
                    val output = bundle?.getString("stdout").orEmpty()
                    val errors = bundle?.getString("stderr").orEmpty()
                    val message = bundle?.getString("errmsg").orEmpty()
                    val exit = bundle?.getInt("exitCode", Int.MIN_VALUE) ?: Int.MIN_VALUE
                    val internalErr = bundle?.getInt("err", Activity.RESULT_OK) ?: Int.MIN_VALUE
                    val succeeded = exit == 0 && internalErr == Activity.RESULT_OK &&
                        output.contains("BUILDDECK_RESULT=SUCCESS")
                    val console = "Exit code: $exit / Termux status: $internalErr\n" +
                        output + "\n" + errors + "\n" + message
                    val project = store.projects().firstOrNull { it.id == record.projectId }
                    val apk = File(File(appContext.filesDir, "builddeck_results"), "$recordId.apk")
                    var saved = false
                    if (succeeded && project != null && apk.length() > 100L) {
                        try {
                            store.importLocalRelease(project, apk, "BuildDeck-local-${System.currentTimeMillis()}.apk")
                            saved = true
                        } catch (error: Exception) {
                            Log.w("BuildDeck", "Automatic APK import failed; manual import remains available", error)
                        }
                    }
                    val status = when {
                        !succeeded -> "FAILED"
                        saved -> "READY"
                        else -> "SUCCESS_NEEDS_IMPORT"
                    }
                    val note = when (status) {
                        "READY" -> "APK compiled and saved to APK Vault"
                        "SUCCESS_NEEDS_IMPORT" -> "Compiled; import APK from Downloads/BuildDeck/Out"
                        else -> (message.ifBlank { errors.takeLast(150).ifBlank { output.takeLast(150) } })
                            .ifBlank { "Termux build failed with exit code $exit" }
                    }
                    ai.completeLocalBuild(recordId, status, note, console)
                }
                // Only revoke grants after Termux has completed the command.
                val dir = File(appContext.filesDir, "builddeck_results")
                for (file in listOf(File(dir, "$recordId.apk"), File(dir, "$recordId.log"))) {
                    val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", file)
                    appContext.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            } catch (error: Exception) {
                Log.e("BuildDeck", "Unable to process Termux result", error)
                runCatching {
                    AiControlStore(appContext, Store(appContext)).completeLocalBuild(
                        recordId, "FAILED", "Could not process build result: ${error.message}", error.stackTraceToString()
                    )
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val EXTRA_RECORD = "com.builddeck.mobile.extra.BUILD_RECORD"
    }
}
