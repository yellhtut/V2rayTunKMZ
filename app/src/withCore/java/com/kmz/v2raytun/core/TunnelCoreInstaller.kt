package com.kmz.v2raytun.core

import android.content.Context
import android.util.Base64
import android.util.Log
import libv2ray.Libv2ray
import java.io.File
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Registers the Xray-backed engine — compiled only when `libs/libv2ray.aar` is present.
 *
 * Its counterpart in `src/noCore/java` registers nothing, which is what lets
 * [com.kmz.v2raytun.V2RayTunApp] call this unconditionally.
 */
internal fun installTunnelCore(context: Context) {
    val assetDir = File(context.filesDir, "assets").apply { mkdirs() }

    // Must happen before any core call: it fixes the asset and certificate paths and the
    // XUDP base key for the process. The directory is handed over before it is necessarily
    // populated, which is safe because the core resolves paths per lookup, not once at init.
    Libv2ray.initCoreEnv(assetDir.absolutePath, xudpBaseKey(context))

    // ~15 MB of geo data, so not on the main thread. Configs from ConfigGenerator route by
    // literal CIDR and never reference geoip:/geosite:, so a tunnel started before this
    // finishes still works — these are here for correctness if that ever changes.
    thread(name = "geo-assets") {
        runCatching { copyGeoAssets(context, assetDir) }
            .onFailure { Log.w(TAG, "could not stage geo assets", it) }
    }

    TunnelCoreProvider.register(XrayTunnelCore())
    Log.i(TAG, "Xray core registered: ${runCatching { Libv2ray.checkVersionX() }.getOrNull()}")
}

/**
 * The .aar carries geoip.dat/geosite.dat in its own assets, which AGP merges into the APK.
 * The core reads them from the filesystem, so they are unpacked once on first run.
 */
private fun copyGeoAssets(context: Context, target: File) {
    for (name in GEO_ASSETS) {
        val out = File(target, name)
        // Size check rather than existence: a copy interrupted by a process death would
        // otherwise leave a truncated file that never gets repaired.
        val packaged = runCatching { context.assets.openFd(name).length }.getOrNull()
        if (out.isFile && packaged != null && out.length() == packaged) continue

        context.assets.open(name).use { input ->
            out.outputStream().use(input::copyTo)
        }
        Log.i(TAG, "staged $name (${out.length()} bytes)")
    }
}

/**
 * XUDP's base key, as the core demands it: 32 random bytes, base64url-encoded without padding.
 * Xray decodes this with RawURLEncoding and rejects anything that isn't exactly 32 decoded
 * bytes ("BaseKey must be 32 bytes"), which is why a UUID string — what an earlier build stored
 * here — fails core init before any profile is even read.
 *
 * The key only has to be stable and unpredictable per install; it is deliberately not derived
 * from a hardware ID, which would be a tracking vector and is restricted on modern Android.
 * A value left by that earlier build is detected as invalid and replaced in place.
 */
private fun xudpBaseKey(context: Context): String {
    val prefs = context.getSharedPreferences("core", Context.MODE_PRIVATE)

    prefs.getString(KEY_XUDP, null)?.let { stored ->
        if (decodesTo32Bytes(stored)) return stored
        Log.w(TAG, "stored XUDP base key is not 32 bytes; regenerating")
    }

    return newBaseKey().also {
        prefs.edit().putString(KEY_XUDP, it).apply()
    }
}

private fun newBaseKey(): String {
    val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
    return Base64.encodeToString(raw, BASE64_FLAGS)
}

/** Mirrors the core's own check: must decode (base64url, no padding) to exactly 32 bytes. */
private fun decodesTo32Bytes(key: String): Boolean = runCatching {
    Base64.decode(key, BASE64_FLAGS).size == 32
}.getOrDefault(false)

private const val TAG = "TunnelCore"
private const val KEY_XUDP = "xudpBaseKey"
// URL_SAFE + NO_PADDING match Go's base64.RawURLEncoding; NO_WRAP keeps it a single line.
private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
private val GEO_ASSETS = listOf("geoip.dat", "geosite.dat")
