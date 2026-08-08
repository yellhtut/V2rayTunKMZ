package com.kmz.v2raytun.core

import android.content.Context
import android.content.Intent
import android.net.VpnService

/**
 * Starting and stopping the tunnel from the UI side.
 *
 * Android requires one-time user consent before any app may open a TUN, and the consent
 * dialog can only be launched from an Activity — so [consentIntent] is handed back to the UI
 * to show rather than being launched here.
 */
object TunnelController {

    /**
     * The consent dialog to show before connecting, or null if permission is already granted.
     *
     * Consent is per-app and can be revoked at any time (including by the user enabling a
     * different VPN), so this must be re-checked before every connect rather than cached.
     */
    fun consentIntent(context: Context): Intent? = VpnService.prepare(context)

    fun connect(context: Context, profileId: Long) {
        val intent = Intent(context, V2RayVpnService::class.java)
            .setAction(V2RayVpnService.ACTION_CONNECT)
            .putExtra(V2RayVpnService.EXTRA_PROFILE_ID, profileId)
        context.startService(intent)
    }

    fun disconnect(context: Context) {
        val intent = Intent(context, V2RayVpnService::class.java)
            .setAction(V2RayVpnService.ACTION_DISCONNECT)
        context.startService(intent)
    }
}
