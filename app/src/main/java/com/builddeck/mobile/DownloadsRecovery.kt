package com.builddeck.mobile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Requests only known, previously approved build records; no arbitrary filesystem access. */
internal object DownloadsRecovery {
    private val pendingStatuses = setOf("SUBMITTED", "RUNNING", "SUCCESS", "SUCCESS_NEEDS_IMPORT", "DISMISSED", "FAILED")

    @Synchronized
    fun request(context: Context, records: List<BuildRecord>, manual: Boolean): Int {
        if (ContextCompat.checkSelfPermission(context, "com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
            if (manual) error("Grant BuildDeck permission to run Termux commands first")
            return 0
        }
        val prefs = context.getSharedPreferences("builddeck_download_recovery", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("lastDispatch", 0L) < 120_000L) return 0
        val candidates = records.filter { record ->
            record.status in pendingStatuses &&
                record.id.matches(Regex("[a-f0-9-]{36}")) && record.projectId.matches(Regex("[a-f0-9-]{36}")) &&
                (manual || now - record.submittedAt >= 120_000L && now - record.submittedAt <= 2 * 24 * 60 * 60_000L) &&
                now - prefs.getLong(record.id, 0L) >= (if (manual) 120_000L else 15 * 60_000L)
        }.take(if (manual) 12 else 4)
        if (candidates.isEmpty()) return 0
        val args = mutableListOf("-s", "--")
        val accepted = mutableListOf<String>()
        for (record in candidates) {
            val transfer = runCatching { BuildTransferProvider.issue(context, record.id, recovery = true) }.getOrNull() ?: continue
            args.addAll(listOf(record.projectId, record.id, transfer.getValue("apk"), transfer.getValue("log")))
            accepted.add(record.id)
        }
        if (accepted.isEmpty()) return 0
        val script = context.assets.open("termux_recover_downloads.sh").bufferedReader().use { it.readText() }
        val command = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", args.toTypedArray())
            putExtra("com.termux.RUN_COMMAND_STDIN", script)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "BuildDeck Downloads recovery")
        }
        checkNotNull(context.startForegroundService(command)) { "Termux command service unavailable" }
        prefs.edit().putLong("lastDispatch", now).apply()
        val edit = prefs.edit()
        accepted.forEach { edit.putLong(it, now) }
        edit.apply()
        return accepted.size
    }
}
