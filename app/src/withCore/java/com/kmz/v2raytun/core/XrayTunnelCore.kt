package com.kmz.v2raytun.core

import android.util.Log
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * Binds [TunnelCore] to AndroidLibXrayLite — compiled only when `libs/libv2ray.aar` is present.
 *
 * Verified against v26.7.31 (upstream commit b213898). The API is not stable across versions:
 * older releases exposed `V2RayPoint` with a `V2RayVPNServiceSupportsSet` callback, this one
 * exposes [CoreController] with [CoreCallbackHandler]. If a rebuilt .aar stops compiling here,
 * read `cheatsheet.txt` from the Tunnel core workflow before changing anything.
 *
 * The whole tunnel is this one library: `StartLoop` takes the TUN's file descriptor, and gVisor
 * netstack is compiled in, so the core reads IP packets off the TUN itself. There is no
 * separate tun2socks process to launch and no local socks inbound to bridge to.
 */
internal class XrayTunnelCore : TunnelCore {

    private var controller: CoreController? = null

    /**
     * Set before we ask the core to stop, so the resulting [CoreCallbackHandler.shutdown]
     * callback can be told apart from the core dying on its own. Volatile because the
     * callback arrives on one of the core's own threads.
     */
    @Volatile
    private var stopRequested = false

    /**
     * Delegated to the core rather than tracked here. It flips this flag itself when the loop
     * exits on its own — for instance if the config is rejected — and a local copy would then
     * be quietly wrong.
     */
    override val isRunning: Boolean
        get() = controller?.isRunning == true

    override fun start(config: String, tunFd: Int) {
        if (isRunning) return
        stopRequested = false

        val handler = object : CoreCallbackHandler {
            // These return a status code to the core, not to us; 0 means "handled".
            override fun startup(): Long = 0L

            override fun shutdown(): Long {
                // Fires on every stop, including ours — reporting a failure unconditionally
                // would turn every clean disconnect into an error snackbar. Only an
                // unrequested shutdown is a fault: a rejected config, or a fatal transport
                // error, with nothing awaiting start() by that point to throw to.
                if (stopRequested) {
                    Log.i(TAG, "core shut down as requested")
                } else {
                    Log.w(TAG, "core shut down on its own")
                    TunnelMonitor.update(TunnelState.Failed("The tunnel stopped unexpectedly"))
                }
                return 0L
            }

            override fun onEmitStatus(code: Long, message: String?): Long {
                Log.d(TAG, "core status $code: ${message.orEmpty()}")
                return 0L
            }
        }

        val core = Libv2ray.newCoreController(handler)
        controller = core

        try {
            // int32 in Go. An fd never exceeds Int range, so this is a widening no-op in
            // Kotlin, but the core does read and write this descriptor directly.
            core.startLoop(config, tunFd)
        } catch (e: Exception) {
            // Thrown for a malformed config or a failed inbound bind. Clearing the reference
            // matters: a half-started controller must not be left for stop() to act on.
            controller = null
            throw TunnelException(e.message ?: "The tunnel core refused to start", e)
        }
    }

    override fun stop() {
        val core = controller ?: return
        controller = null
        stopRequested = true
        runCatching { core.stopLoop() }
            .onFailure { Log.w(TAG, "stopLoop() failed", it) }
    }

    /**
     * Reading these counters **resets them** — upstream's `QueryStats` documents that it
     * "retrieves and resets". So this reports bytes since the previous call, and callers must
     * accumulate if they want a session total rather than calling it from two places.
     */
    override fun trafficStats(): TrafficStats? {
        val core = controller ?: return null
        return runCatching {
            TrafficStats(
                uplink = core.queryStats(PROXY_TAG, "uplink"),
                downlink = core.queryStats(PROXY_TAG, "downlink"),
            )
        }.getOrNull()
    }

    private companion object {
        const val TAG = "XrayTunnelCore"

        /** Must match the outbound tag ConfigGenerator emits, or the counters read zero. */
        const val PROXY_TAG = "proxy"
    }
}
