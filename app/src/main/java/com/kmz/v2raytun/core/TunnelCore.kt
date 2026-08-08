package com.kmz.v2raytun.core

import android.util.Log

/**
 * The tunnelling engine, behind an interface.
 *
 * This is the seam that keeps Phase 5's service layer buildable while `libv2ray.aar` is not
 * in the tree (see app/libs/README.md). [MissingTunnelCore] is selected whenever no real
 * implementation is registered, so the app compiles, installs and runs — it just reports
 * honestly that it cannot tunnel instead of pretending to connect.
 */
interface TunnelCore {

    val isRunning: Boolean

    /**
     * Starts the engine and attaches it to the TUN.
     *
     * @param config the JSON from ConfigGenerator
     * @param tunFd the established TUN's file descriptor. The engine reads IP packets from
     *   it and writes replies back — this is what actually moves traffic, so an
     *   implementation that ignores it produces a tunnel that swallows every packet.
     * @throws TunnelException if the engine cannot start
     */
    fun start(config: String, tunFd: Int)

    fun stop()

    /** Bytes since [start], or null if the engine does not report them. */
    fun trafficStats(): TrafficStats?
}

data class TrafficStats(val uplink: Long, val downlink: Long)

class TunnelException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Stand-in used until the real core is dropped in. Fails loudly on [start] rather than
 * silently doing nothing, so a build without the .aar cannot masquerade as a working tunnel.
 */
object MissingTunnelCore : TunnelCore {

    override val isRunning: Boolean = false

    override fun start(config: String, tunFd: Int) {
        Log.w(TAG, "start() with no core present; config was ${config.length} bytes")
        throw TunnelException(
            "The tunnel core isn't bundled in this build. See app/libs/README.md.",
        )
    }

    override fun stop() = Unit

    override fun trafficStats(): TrafficStats? = null

    private const val TAG = "MissingTunnelCore"
}

/**
 * Where the service looks up its engine.
 *
 * Phase 5 step 3 registers the Xray-backed implementation here — one call, and every caller
 * below this line keeps working unchanged.
 */
object TunnelCoreProvider {

    @Volatile
    private var core: TunnelCore = MissingTunnelCore

    val isCorePresent: Boolean get() = core !== MissingTunnelCore

    fun register(core: TunnelCore) {
        this.core = core
    }

    fun get(): TunnelCore = core
}
