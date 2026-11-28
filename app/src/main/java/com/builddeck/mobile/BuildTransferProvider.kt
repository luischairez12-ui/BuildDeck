package com.builddeck.mobile

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Narrow, capability-token upload endpoint for a user-approved Termux build.
 *
 * Termux runs /system/bin/content as a separate identity on some Android builds.
 * A write-granted FileProvider URI therefore does not always work. This provider
 * requires a one-use, 256-bit capability generated for an existing build record,
 * accepts only APK/log/stage data, and never offers read access or directory paths.
 * The capability is removed when the official Termux callback is received.
 */
class BuildTransferProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode !in setOf("w", "wt")) throw FileNotFoundException("Write-only build handoff")
        val app = context ?: throw FileNotFoundException("No BuildDeck context")
        val parts = uri.pathSegments
        if (parts.size == 3 && parts[0] == "task") return TermuxTaskBridge.openSnapshot(app, parts[1], parts[2])
        if (parts.size != 4 || parts[0] != "v1") throw FileNotFoundException("Invalid path")
        val id = parts[1]
        val secret = parts[2]
        val kind = parts[3]
        if (!id.matches(Regex("[a-f0-9-]{36}")) || kind !in setOf("apk", "log", "stage")) {
            throw FileNotFoundException("Invalid build result")
        }
        val prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val authorized = prefs.getString("key_$id", null) ?: throw FileNotFoundException("Expired capability")
        val issued = prefs.getLong("time_$id", 0L)
        if (System.currentTimeMillis() - issued !in 0L..(6L * 60 * 60 * 1000)) {
            throw FileNotFoundException("Expired build capability")
        }
        if (!MessageDigest.isEqual(authorized.toByteArray(), secret.toByteArray())) {
            throw FileNotFoundException("Unauthorized build")
        }
        val known = AiControlStore(app, Store(app)).buildRecords()
            .any { it.id == id && it.status in setOf("SUBMITTED", "RUNNING", "SUCCESS", "SUCCESS_NEEDS_IMPORT", "FAILED", "DISMISSED") }
        if (!known) throw FileNotFoundException("Build no longer running")
        val suffix = when (kind) {
            "apk" -> "apk"
            "log" -> "log"
            else -> "stage"
        }
        val dir = File(app.filesDir, "builddeck_results").apply { mkdirs() }
        val target = File(dir, "$id.$suffix")
        return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_CREATE or
            ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY)
    }

    override fun getType(uri: Uri): String = "application/octet-stream"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val PREF = "builddeck_ephemeral_build_handoff"

        /** Mint only after the user has approved the build and its record exists. */
        fun issue(context: Context, recordId: String, recovery: Boolean = false): Map<String, String> {
            require(recordId.matches(Regex("[a-f0-9-]{36}")))
            require(AiControlStore(context, Store(context)).buildRecords()
                .any { it.id == recordId && (it.status in setOf("SUBMITTED", "RUNNING") ||
                    recovery && it.status in setOf("SUCCESS", "SUCCESS_NEEDS_IMPORT", "FAILED", "DISMISSED")) })
            val secretBytes = ByteArray(32)
            SecureRandom().nextBytes(secretBytes)
            val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val existing = prefs.getString("key_$recordId", null)?.takeIf {
                recovery && System.currentTimeMillis() - prefs.getLong("time_$recordId", 0L) in 0L..(6L * 60 * 60 * 1000)
            }
            val secret = existing ?: Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes)
            if (existing == null) check(prefs.edit().putString("key_$recordId", secret)
                .putLong("time_$recordId", System.currentTimeMillis()).commit())
            return listOf("apk", "log", "stage").associateWith { kind ->
                "content://${context.packageName}.handoff/v1/$recordId/$secret/$kind"
            }
        }

        fun revoke(context: Context, recordId: String) {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .remove("key_$recordId").remove("time_$recordId").apply()
        }

        fun stage(context: Context, recordId: String): String? {
            if (!recordId.matches(Regex("[a-f0-9-]{36}"))) return null
            val file = File(File(context.filesDir, "builddeck_results"), "$recordId.stage")
            if (!file.isFile || file.length() !in 1L..200L) return null
            return runCatching { file.readText().trim().take(110) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
        }
    }
}
