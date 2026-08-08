package com.kmz.v2raytun.core

import android.content.Context
import android.util.Log
import libv2ray.Libv2ray
import java.io.File
import java.util.UUID
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
 * Per-install identifier the core uses to derive XUDP's base key. It only has to be stable
 * and unpredictable — deliberately not a hardware ID, which would be a tracking vector and
 * is restricted on modern Android anyway.
 */
private fun xudpBaseKey(context: Context): String {
    val prefs = context.getSharedPreferences("core", Context.MODE_PRIVATE)
    prefs.getString(KEY_XUDP, null)?.let { return it }

    return UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_XUDP, it).apply()
    }
}

private const val TAG = "TunnelCore"
private const val KEY_XUDP = "xudpBaseKey"
private val GEO_ASSETS = listOf("geoip.dat", "geosite.dat")
