package com.builddeck.mobile

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

data class ApkDetails(
    val packageId: String,
    val version: String,
    val versionCode: Long,
    val verdict: String,
    val warning: Boolean,
    val installedVersion: String? = null,
    val installedVersionCode: Long? = null,
    val signatureState: String = "Not verified",
    val updateState: String = "UNKNOWN"
)

/** Compare only public APK certificate fingerprints. Never handles private signing keys. */
object ApkInspector {
    @Suppress("DEPRECATION")
    fun inspect(context: Context, apkPath: String): ApkDetails? {
        val apk = File(apkPath)
        if (!apk.isFile) return null
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return null
        val pkg = archive.packageName ?: return null
        val version = archive.versionName ?: "unknown"
        val code = archive.longVersionCode
        val installed = try {
            pm.getPackageInfo(pkg, flags)
        } catch (_: PackageManager.NameNotFoundException) { null }

        if (installed == null) {
            return ApkDetails(pkg, version, code,
                "No installed package visible — new install or package visibility restriction",
                warning = false, updateState = "UNKNOWN")
        }
        val uploadedCerts = archive.signingInfo?.apkContentsSigners.orEmpty()
            .map { sha(it.toByteArray()) }.toSet()
        val installedCerts = installed.signingInfo?.apkContentsSigners.orEmpty()
            .map { sha(it.toByteArray()) }.toSet()
        val signVerified = uploadedCerts.isNotEmpty() && installedCerts.isNotEmpty()
        val signsMatch = signVerified && uploadedCerts == installedCerts
        val state = when {
            signVerified && !signsMatch -> "WRONG_SIGNATURE"
            code < installed.longVersionCode -> "DOWNGRADE"
            !signVerified -> "SIGNATURE_UNKNOWN"
            code == installed.longVersionCode -> "SAME_VERSION"
            else -> "UPDATE"
        }
        val verdict = when (state) {
            "WRONG_SIGNATURE" -> "Different signing certificate — cannot update"
            "DOWNGRADE" -> "Older APK — Android normally blocks downgrades"
            "SIGNATURE_UNKNOWN" -> "Signing compatibility could not be verified"
            "SAME_VERSION" -> "Same version code — updating may not change anything"
            else -> "Compatible app update"
        }
        return ApkDetails(pkg, version, code, verdict,
            warning = state in setOf("WRONG_SIGNATURE", "DOWNGRADE"),
            installedVersion = installed.versionName,
            installedVersionCode = installed.longVersionCode,
            signatureState = when {
                !signVerified -> "Could not verify"
                signsMatch -> "Certificate match"
                else -> "Certificate mismatch"
            },
            updateState = state)
    }

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
