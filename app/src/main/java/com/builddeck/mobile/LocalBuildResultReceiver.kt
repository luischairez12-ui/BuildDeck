package com.builddeck.mobile

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.security.MessageDigest
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
        // Missing extras are not a compiler failure. Durable handoff recovery may still succeed.
        if (bundle == null) {
            Log.w("BuildDeck", "Termux callback missing result bundle for $recordId")
            return
        }
        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            synchronized(OutputCollector.recoveryLock) {
            try {
                val store = Store(appContext)
                val ai = AiControlStore(appContext, store)
                val record = ai.buildRecords().firstOrNull { it.id == recordId }
                if (record != null && record.status != "READY") {
                    val output = bundle?.getString("stdout").orEmpty()
                    val errors = bundle?.getString("stderr").orEmpty()
                    val message = bundle?.getString("errmsg").orEmpty()
                    val exit = bundle?.getInt("exitCode", Int.MIN_VALUE) ?: Int.MIN_VALUE
                    val internalErr = bundle?.getInt("err", Activity.RESULT_OK) ?: Int.MIN_VALUE
                    // Termux sometimes finishes before all stdout reaches the callback.
                    // Its authenticated write-only handoff also delivers a full log.
                    // Trust only a real exitCode 0 + matching success marker and APK hash.
                    val resultDir = File(appContext.filesDir, "builddeck_results")
                    val handoffLog = File(resultDir, "$recordId.log")
                    val diskOutput = if (handoffLog.isFile &&
                        handoffLog.length() in 1L..4_000_000L) {
                        runCatching { handoffLog.readText() }.getOrDefault("")
                    } else ""
                    val completeOutput = output + "\n" + diskOutput
                    val succeeded = exit == 0 && internalErr == Activity.RESULT_OK &&
                        completeOutput.contains("BUILDDECK_RESULT=SUCCESS")
                    val console = "Exit code: $exit / Termux status: $internalErr\n" +
                        completeOutput + "\n" + errors + "\n" + message
                    val project = store.projects().firstOrNull { it.id == record.projectId }
                    val apk = File(resultDir, "$recordId.apk")
                    val expectedSha = completeOutput.lineSequence()
                        .lastOrNull { it.startsWith("BUILDDECK_APK_SHA256=") }
                        ?.substringAfter('=')
                        ?.trim()
                        ?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
                    val validDigest = if (succeeded && expectedSha != null &&
                        apk.isFile && apk.length() in 101L..(500L * 1024 * 1024)) {
                        runCatching {
                            val sha = MessageDigest.getInstance("SHA-256")
                            apk.inputStream().buffered().use { input ->
                                val buffer = ByteArray(65_536)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count == -1) break
                                    sha.update(buffer, 0, count)
                                }
                            }
                            val actual = sha.digest().joinToString("") { "%02x".format(it) }
                            MessageDigest.isEqual(
                                actual.toByteArray(Charsets.US_ASCII),
                                expectedSha.toByteArray(Charsets.US_ASCII)
                            )
                        }.getOrDefault(false)
                    } else false
                    var saved = false
                    if (succeeded && project != null && validDigest) {
                        try {
                            store.importLocalRelease(project, apk, "BuildDeck-$recordId.apk")
                            saved = true
                        } catch (error: Exception) {
                            Log.w("BuildDeck", "Direct APK import failed; existing manual fallback remains", error)
                        }
                    }
                    val status = when {
                        !succeeded -> "FAILED"
                        saved -> "READY"
                        else -> "SUCCESS_NEEDS_IMPORT"
                    }
                    val note = when (status) {
                        "READY" -> "APK compiled, verified and automatically saved in APK Vault"
                        "SUCCESS_NEEDS_IMPORT" -> if (apk.length() > 100L && !validDigest)
                            "Compiled; direct transfer did not pass integrity check. Import from Downloads/BuildDeck/Out."
                            else "Compiled; automatic transfer unavailable. Import APK from Downloads/BuildDeck/Out."
                        else -> (message.ifBlank { errors.takeLast(150).ifBlank { output.takeLast(150) } })
                            .ifBlank { "Termux build failed with exit code $exit" }
                    }
                    ai.completeLocalBuild(recordId, status, note, console)
                    if (status == "SUCCESS_NEEDS_IMPORT") {
                        runCatching { OutputCollector(appContext).reconcile(store, ai) }
                    }
                }
                // Expire the one-time write-only upload capability on completion.
                BuildTransferProvider.revoke(appContext, recordId)
            } catch (error: Exception) {
                Log.e("BuildDeck", "Unable to process Termux result", error)
                runCatching {
                    AiControlStore(appContext, Store(appContext)).completeLocalBuild(
                        recordId, "FAILED", "Could not process build result: ${error.message}", error.stackTraceToString()
                    )
                }
            } finally {
                BuildTransferProvider.revoke(appContext, recordId)
                pending.finish()
            }
            }
        }.start()
    }

    companion object {
        const val EXTRA_RECORD = "com.builddeck.mobile.extra.BUILD_RECORD"
    }
}
