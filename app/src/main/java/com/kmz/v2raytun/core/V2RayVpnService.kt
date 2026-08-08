package com.kmz.v2raytun.core

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.kmz.v2raytun.core.config.ConfigGenerator
import com.kmz.v2raytun.data.model.Profile
import com.kmz.v2raytun.data.repo.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns the TUN device and the tunnelling engine's lifetime.
 *
 * Ordering matters and is deliberate: the notification goes up first (Android kills a
 * foreground service that does not post one in time), then the TUN is established, then the
 * engine starts. Teardown runs in reverse. Any failure funnels through [failAndStop] so the
 * UI never sees a half-connected state.
 */
class V2RayVpnService : VpnService() {

    private var tunInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val core: TunnelCore get() = TunnelCoreProvider.get()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                stopTunnel()
                return START_NOT_STICKY
            }
            else -> {
                val profileId = intent?.getLongExtra(EXTRA_PROFILE_ID, -1L) ?: -1L
                if (profileId <= 0L) {
                    failAndStop("No server was selected")
                    return START_NOT_STICKY
                }
                startTunnel(profileId)
                // Not sticky: on a process kill the TUN and engine are both gone, so a
                // silent restart with no profile would come back up as a dead tunnel.
                return START_NOT_STICKY
            }
        }
    }

    private fun startTunnel(profileId: Long) {
        TunnelMonitor.update(TunnelState.Connecting(profileId))
        TunnelNotifications.startForeground(this, connecting = true, serverName = null)

        scope.launch {
            val profile = runCatching { ProfileRepository.from(application).getById(profileId) }
                .getOrNull()

            if (profile == null) {
                failAndStop("That server no longer exists")
                return@launch
            }

            try {
                val tun = establishTun(profile)
                core.start(ConfigGenerator.toJson(profile), tun.fd) { fd -> protect(fd) }

                TunnelMonitor.update(TunnelState.Connected(profile.id, profile.name))
                TunnelNotifications.startForeground(
                    this@V2RayVpnService,
                    connecting = false,
                    serverName = profile.name,
                )
            } catch (e: TunnelException) {
                // Never log the profile itself — uuid and password are credentials.
                Log.w(TAG, "tunnel start failed for ${profile.redacted()}", e)
                failAndStop(e.message ?: "The tunnel could not be started")
            } catch (e: Exception) {
                Log.e(TAG, "unexpected failure starting the tunnel", e)
                failAndStop("Unexpected error: ${e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Routes everything into the tunnel, minus this app's own traffic.
     *
     * Excluding ourselves is not an optimisation: the engine's connection to the proxy would
     * otherwise be routed through the TUN that the engine itself is serving.
     */
    private fun establishTun(profile: Profile): ParcelFileDescriptor {
        val builder = Builder()
            .setSession(profile.name)
            .setMtu(TUN_MTU)
            .addAddress(TUN_ADDRESS, TUN_PREFIX)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(DNS_PRIMARY)
            .addDnsServer(DNS_SECONDARY)

        builder.addAddress(TUN_ADDRESS_V6, TUN_PREFIX_V6)
        builder.addRoute("::", 0)

        runCatching { builder.addDisallowedApplication(packageName) }
            .onFailure { Log.w(TAG, "could not exclude self from the tunnel", it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        tunInterface = builder.establish()
            ?: throw TunnelException("The system did not grant VPN permission")

        return tunInterface!!
    }

    private fun stopTunnel() {
        TunnelMonitor.update(TunnelState.Disconnecting)
        closeResources()
        TunnelMonitor.update(TunnelState.Disconnected)
        stopSelfCleanly()
    }

    private fun failAndStop(reason: String) {
        closeResources()
        TunnelMonitor.update(TunnelState.Failed(reason))
        stopSelfCleanly()
    }

    /** Reverse of setup, and safe to call twice — teardown races with onRevoke and onDestroy. */
    private fun closeResources() {
        runCatching { core.stop() }.onFailure { Log.w(TAG, "core.stop() failed", it) }
        runCatching { tunInterface?.close() }.onFailure { Log.w(TAG, "closing tun failed", it) }
        tunInterface = null
    }

    private fun stopSelfCleanly() {
        TunnelNotifications.stopForeground(this)
        stopSelf()
    }

    /** The user switched to another VPN app, or revoked ours in settings. */
    override fun onRevoke() {
        Log.i(TAG, "VPN permission revoked by the system")
        closeResources()
        TunnelMonitor.update(TunnelState.Disconnected)
        stopSelfCleanly()
        super.onRevoke()
    }

    override fun onDestroy() {
        closeResources()
        if (TunnelMonitor.state.value !is TunnelState.Failed) {
            TunnelMonitor.update(TunnelState.Disconnected)
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "V2RayVpnService"

        const val ACTION_CONNECT = "com.kmz.v2raytun.CONNECT"
        const val ACTION_DISCONNECT = "com.kmz.v2raytun.DISCONNECT"
        const val EXTRA_PROFILE_ID = "profileId"

        private const val TUN_MTU = 1500
        private const val TUN_ADDRESS = "10.10.10.2"
        private const val TUN_PREFIX = 32
        private const val TUN_ADDRESS_V6 = "fd00:2::2"
        private const val TUN_PREFIX_V6 = 126
        private const val DNS_PRIMARY = "1.1.1.1"
        private const val DNS_SECONDARY = "8.8.8.8"
    }
}
