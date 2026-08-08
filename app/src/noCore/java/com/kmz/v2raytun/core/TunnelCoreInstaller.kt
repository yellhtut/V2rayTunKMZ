package com.kmz.v2raytun.core

import android.content.Context
import android.util.Log

/**
 * Registers nothing — compiled when `libs/libv2ray.aar` is absent.
 *
 * The counterpart in `src/withCore/java` registers the real engine. Routing both through one
 * function is what lets [com.kmz.v2raytun.V2RayTunApp] have a single unconditional call site,
 * so no file under `src/main` ever names a class that lives inside the .aar.
 *
 * [TunnelCoreProvider] already defaults to [MissingTunnelCore], so there is genuinely nothing
 * to do here beyond saying so once, where a bug report will show it.
 */
@Suppress("UNUSED_PARAMETER")
internal fun installTunnelCore(context: Context) {
    Log.i("TunnelCore", "no tunnel core in this build; see app/libs/README.md")
}
