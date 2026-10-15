package com.optiscan.app.utils

import android.os.Build
import com.optiscan.app.BuildConfig

/**
 * Networking configuration for backend communication.
 *
 * DEBUG BUILDS:
 * On Android emulators, '10.0.2.2' maps to '127.0.0.1' (localhost) on the host computer.
 * On physical devices connected via USB, '127.0.0.1' maps with `adb reverse tcp:8000 tcp:8000`.
 *
 * RELEASE BUILDS:
 * Must NOT silently target 127.0.0.1, emulator addresses, or ADB reverse forwarding.
 * Targets a configured hosted HTTPS backend URL or explicitly reports unconfigured status.
 */
object NetworkConfig {
    const val EMULATOR_BASE_URL = "http://10.0.2.2:8000/"
    const val PHYSICAL_DEVICE_REVERSE_URL = "http://127.0.0.1:8000/"

    val isDebug: Boolean
        get() = BuildConfig.DEBUG

    /**
     * Heuristic to detect whether the app is executing inside an Android emulator.
     */
    val isEmulator: Boolean
        get() = (Build.BRAND?.startsWith("generic") == true && Build.DEVICE?.startsWith("generic") == true)
                || Build.FINGERPRINT?.startsWith("generic") == true
                || Build.HARDWARE?.contains("goldfish") == true
                || Build.HARDWARE?.contains("ranchu") == true
                || Build.MODEL?.contains("google_sdk") == true
                || Build.MODEL?.contains("Emulator") == true
                || Build.MODEL?.contains("Android SDK built for x86") == true
                || Build.MANUFACTURER?.contains("Genymotion") == true

    /**
     * Determines the initial default backend URL based on build variant.
     */
    fun getDefaultBaseUrl(): String {
        return if (isDebug) {
            if (isEmulator) EMULATOR_BASE_URL else PHYSICAL_DEVICE_REVERSE_URL
        } else {
            val buildUrl = BuildConfig.DEFAULT_BACKEND_URL.trim()
            if (isValidHostedUrl(buildUrl)) normalizeUrl(buildUrl) else ""
        }
    }

    /**
     * Validates whether a given URL has an acceptable HTTP/HTTPS scheme and minimal structure.
     */
    fun isValidUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim().lowercase()
        return (trimmed.startsWith("http://") || trimmed.startsWith("https://")) && trimmed.length > 8
    }

    /**
     * Validates that the URL is a legitimate hosted production URL (HTTPS, not loopback or local subnet).
     */
    fun isValidHostedUrl(url: String?): Boolean {
        if (!isValidUrl(url)) return false
        val trimmed = url!!.trim().lowercase()
        val isHttps = trimmed.startsWith("https://")
        val isLocal = isLocalAddress(trimmed)
        return isHttps && !isLocal
    }

    /**
     * Checks if a URL targets a local development address (emulator, loopback, or private LAN IP).
     */
    fun isLocalAddress(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase().trim()
        return lower.contains("10.0.2.2") ||
                lower.contains("127.0.0.1") ||
                lower.contains("localhost") ||
                lower.contains("0.0.0.0") ||
                lower.startsWith("http://192.168.") ||
                lower.startsWith("http://10.") ||
                lower.startsWith("http://172.")
    }

    /**
     * Normalizes a URL by ensuring a trailing slash.
     */
    fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    /**
     * Active backend base URL.
     */
    var activeBaseUrl: String = getDefaultBaseUrl()
        set(value) {
            field = normalizeUrl(value)
        }

    /**
     * Returns true if the backend is properly configured for the active build variant.
     * In Debug: any valid URL (local HTTP or hosted HTTPS) is permitted for testing.
     * In Release: strictly requires a valid hosted HTTPS backend URL (no silent localhost or blank).
     */
    fun isConfigured(): Boolean {
        return if (isDebug) {
            isValidUrl(activeBaseUrl)
        } else {
            isValidHostedUrl(activeBaseUrl)
        }
    }
}
