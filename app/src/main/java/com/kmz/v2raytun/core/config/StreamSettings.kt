package com.kmz.v2raytun.core.config

import kotlinx.serialization.Serializable

/**
 * How an outbound's bytes reach the server, and how they are wrapped on the way.
 *
 * Xray keys the transport block off [network] and the security block off [security], so only
 * the matching one is ever populated: `wsSettings` for ws, `realitySettings` for reality, and
 * so on. The rest stay null and are omitted from the JSON.
 */
@Serializable
data class StreamSettings(
    val network: String = "tcp",
    /** "" for plaintext, "tls", or "reality". */
    val security: String = "",
    val tlsSettings: TlsSettings? = null,
    val realitySettings: RealitySettings? = null,
    val wsSettings: WsSettings? = null,
    val grpcSettings: GrpcSettings? = null,
    val httpSettings: HttpSettings? = null,
    val tcpSettings: TcpSettings? = null,
    val kcpSettings: KcpSettings? = null,
    val quicSettings: QuicSettings? = null,
    val httpupgradeSettings: HttpUpgradeSettings? = null,
)

@Serializable
data class TlsSettings(
    val serverName: String? = null,
    val allowInsecure: Boolean = false,
    val alpn: List<String>? = null,
    /** uTLS browser fingerprint to imitate, e.g. chrome. */
    val fingerprint: String? = null,
)

/** REALITY replaces TLS: no certificate, the server is authenticated by [publicKey]. */
@Serializable
data class RealitySettings(
    val publicKey: String,
    val serverName: String? = null,
    val shortId: String? = null,
    val spiderX: String? = null,
    val fingerprint: String? = null,
)

@Serializable
data class WsSettings(
    val path: String = "/",
    val headers: Map<String, String>? = null,
)

@Serializable
data class GrpcSettings(
    val serviceName: String = "",
    val multiMode: Boolean = false,
)

@Serializable
data class HttpSettings(
    val path: String = "/",
    val host: List<String>? = null,
)

@Serializable
data class TcpSettings(val header: TcpHeader? = null)

@Serializable
data class TcpHeader(
    val type: String = "none",
    val request: TcpRequest? = null,
)

/** Only present for the "http" obfuscation header, which fakes a plain HTTP request. */
@Serializable
data class TcpRequest(
    val path: List<String>,
    val headers: Map<String, List<String>>? = null,
)

@Serializable
data class KcpSettings(
    val seed: String? = null,
    val header: ObfuscationHeader = ObfuscationHeader(),
)

/** The fake-packet header shared by the kcp and quic transports. */
@Serializable
data class ObfuscationHeader(val type: String = "none")

@Serializable
data class QuicSettings(
    val security: String = "none",
    val key: String = "",
    val header: ObfuscationHeader = ObfuscationHeader(),
)

@Serializable
data class HttpUpgradeSettings(
    val path: String = "/",
    val host: String? = null,
)
