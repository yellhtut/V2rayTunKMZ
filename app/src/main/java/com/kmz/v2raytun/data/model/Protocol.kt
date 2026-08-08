package com.kmz.v2raytun.data.model

/**
 * Proxy protocols this client can tunnel through. The [scheme] is the URI scheme used by
 * share links, and doubles as the `protocol` field name in a generated Xray outbound.
 */
enum class Protocol(val scheme: String) {
    VMESS("vmess"),
    VLESS("vless"),
    SHADOWSOCKS("ss"),
    TROJAN("trojan"),
    ;

    companion object {
        fun fromScheme(scheme: String): Protocol? =
            entries.firstOrNull { it.scheme.equals(scheme, ignoreCase = true) }
    }
}
