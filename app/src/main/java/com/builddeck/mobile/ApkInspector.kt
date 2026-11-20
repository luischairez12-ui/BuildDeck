package com.builddeck.mobile

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

data class ApkDetails(
    val packageId: String,
    val version: String,
    val versionCode: Long,
    val verdict: String,
    val warning: Boolean
)

/** Only compares public APK signing certificates. Does not extract or handle private keys. */
object ApkInspector {
    @Suppress("DEPRECATION")
    fun inspect(context: Context, apkPath: String): ApkDetails? {
        val file = File(apkPath)
        if (!file.isFile) return null
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return null
        val pkg = archive.packageName ?: return null
        val version = archive.versionName ?: "unknown"
        val code = archive.longVersionCode
        val installed = try { pm.getPackageInfo(pkg, flags) } catch (_: PackageManager.NameNotFoundException) { null }
        val verdict: String
        val warning: Boolean
        if (installed == null) {
            verdict = "New install"
            warning = false
        } else {
            val cert1 = archive.signingInfo?.apkContentsSigners.orEmpty().map { sha(it.toByteArray()) }.toSet()
            val cert2 = installed.signingInfo?.apkContentsSigners.orEmpty().map { sha(it.toByteArray()) }.toSet()
            val matches = cert1.isNotEmpty() && cert1 == cert2
            verdict = when {
                !matches -> "Different signing certificate — cannot update installed app"
                code < installed.longVersionCode -> "Older version — downgrade may be blocked"
                code == installed.longVersionCode -> "Same version code — update may not change anything"
                else -> "Compatible app update"
            }
            warning = !matches || code < installed.longVersionCode
        }
        return ApkDetails(pkg, version, code, verdict, warning)
    }
    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
