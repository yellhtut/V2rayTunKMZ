package com.kmz.v2raytun.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the tunnel is in its lifecycle. */
sealed interface TunnelState {
    data object Disconnected : TunnelState

    data class Connecting(val profileId: Long) : TunnelState

    data class Connected(val profileId: Long, val profileName: String) : TunnelState

    data object Disconnecting : TunnelState

    /** The tunnel stopped, or never started, because of [reason]. */
    data class Failed(val reason: String) : TunnelState

    val isBusy: Boolean get() = this is Connecting || this is Disconnecting

    val isActive: Boolean get() = this is Connected || this is Connecting
}

/**
 * The single place the tunnel's state is published.
 *
 * A VpnService is started by the system rather than constructed by us, and it can be killed
 * (or revoked) without the UI asking — so state lives here rather than in a ViewModel, and
 * the service writes to it as the source of truth. Kept process-wide because there is only
 * ever one tunnel; binding to the service just to read a status flag would be heavier for
 * no gain.
 */
object TunnelMonitor {

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    fun update(state: TunnelState) {
        _state.value = state
    }

    /**
     * Clears a [TunnelState.Failed] once the UI has shown it, so a stale error does not
     * outlive the screen that reported it. Anything else is left alone.
     */
    fun clearFailure() {
        if (_state.value is TunnelState.Failed) _state.value = TunnelState.Disconnected
    }
}
