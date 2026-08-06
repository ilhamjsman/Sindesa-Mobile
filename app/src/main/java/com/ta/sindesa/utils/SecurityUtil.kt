package com.ta.sindesa.utils

import android.os.Build
import java.io.File

object SecurityUtil {

    /**
     * Mengecek apakah aplikasi berjalan di emulator.
     * Bagian dari OWASP M9: Reverse Engineering.
     */
    fun isRunningOnEmulator(): Boolean {
        if (com.ta.sindesa.BuildConfig.DEBUG) return false
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.BOARD == "QC_Reference_Phone"
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HOST.startsWith("Build")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT)
    }

    /**
     * Mengecek apakah perangkat memiliki indikasi telah di-root.
     * Bagian dari OWASP M8: Binary Protection.
     */
    fun isDeviceRooted(): Boolean {
        if (com.ta.sindesa.BuildConfig.DEBUG) return false
        return checkRootMethod1() || checkRootMethod2() || checkRootMethod3()
    }

    private fun checkRootMethod1(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    private fun checkRootMethod2(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }

    private fun checkRootMethod3(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val reader = process.inputStream.bufferedReader()
            reader.readLine() != null
        } catch (t: Throwable) {
            false
        } finally {
            process?.destroy()
        }
    }

    /**
     * Menampilkan dialog peringatan keamanan dan menutup aplikasi.
     */
    fun showSecurityWarning(context: android.app.Activity) {
        android.app.AlertDialog.Builder(context)
            .setTitle("Keamanan Terdeteksi")
            .setMessage("Aplikasi tidak dapat berjalan pada perangkat yang di-root atau emulator demi keamanan data Anda.")
            .setCancelable(false)
            .setPositiveButton("Keluar") { _, _ ->
                context.finishAffinity()
            }
            .show()
    }
}
