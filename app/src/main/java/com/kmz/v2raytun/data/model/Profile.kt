package com.kmz.v2raytun.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single proxy server, as parsed from a share link or entered by hand.
 *
 * The schema is deliberately flat: Room maps flat columns without converters, and every
 * protocol we support draws from the same pool of transport/TLS fields even though each
 * one only uses a subset. Fields not relevant to a given [protocol] stay at their default.
 */
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: Protocol,
    val host: String,
    val port: Int,

    // Credentials. vmess/vless use uuid; ss/trojan use password; ss also needs method.
    val uuid: String = "",
    val password: String = "",
    val method: String = "",
    val alterId: Int = 0,
    /** vmess payload encryption: auto, aes-128-gcm, chacha20-poly1305, none, zero. */
    val security: String = "auto",
    /** vless XTLS flow, e.g. xtls-rprx-vision. */
    val flow: String = "",

    // Transport.
    /** tcp, ws, grpc, h2, quic, kcp, httpupgrade. */
    val network: String = "tcp",
    val headerType: String = "none",
    val path: String = "",
    /** Host header for ws/h2, or the gRPC service name when [network] is grpc. */
    val hostHeader: String = "",

    // TLS.
    /** Empty string for plaintext, otherwise "tls" or "reality". */
    val tls: String = "",
    val sni: String = "",
    val alpn: String = "",
    val fingerprint: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val allowInsecure: Boolean = false,

    /** Set when this profile came from a subscription, so refreshes can replace it. */
    val subscriptionId: Long? = null,
) {
    /** What the core dials, and the SNI fallback. */
    val serverAddress: String get() = "$host:$port"

    /**
     * Never log a Profile directly — [uuid] and [password] are credentials. This is the
     * safe form for log lines and error messages.
     */
    fun redacted(): String = "Profile(name=$name, protocol=${protocol.scheme}, host=$host, port=$port)"

    override fun toString(): String = redacted()
}
