package com.kmz.v2raytun

import android.app.Application
import com.kmz.v2raytun.core.installTunnelCore

class V2RayTunApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Which engine this resolves to was decided at compile time, by whether
        // libs/libv2ray.aar was present — see the sourceSets block in app/build.gradle.kts.
        installTunnelCore(this)
    }
}
