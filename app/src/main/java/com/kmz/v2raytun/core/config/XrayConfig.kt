package com.kmz.v2raytun.core.config

import kotlinx.serialization.Serializable

/**
 * The subset of Xray's JSON config this app generates.
 *
 * Only the keys we actually set are modelled — Xray supplies its own defaults for the rest.
 * Nullable fields are dropped from the output entirely (see [ConfigGenerator]), because Xray
 * treats an absent key and a present-but-empty one differently: an empty `serverName`
 * disables SNI rather than falling back to the host.
 */
@Serializable
data class XrayConfig(
    val log: LogConfig = LogConfig(),
    val inbounds: List<Inbound>,
    val outbounds: List<Outbound>,
    val dns: DnsConfig? = null,
    val routing: Routing? = null,
)

@Serializable
data class LogConfig(val loglevel: String = "warning")

@Serializable
data class Inbound(
    val tag: String,
    val port: Int,
    val listen: String,
    val protocol: String,
    val settings: InboundSettings = InboundSettings(),
    val sniffing: Sniffing? = null,
)

@Serializable
data class InboundSettings(
    val auth: String = "noauth",
    val udp: Boolean = true,
)

/**
 * Lets routing act on the real destination. Without it every connection arriving over the
 * TUN looks like a bare IP, so domain-based rules could never match.
 */
@Serializable
data class Sniffing(
    val enabled: Boolean = true,
    val destOverride: List<String> = listOf("http", "tls"),
)

@Serializable
data class DnsConfig(val servers: List<String>)

@Serializable
data class Routing(
    val domainStrategy: String = "IPIfNonMatch",
    val rules: List<RoutingRule> = emptyList(),
)

@Serializable
data class RoutingRule(
    val outboundTag: String,
    val type: String = "field",
    val ip: List<String>? = null,
    val domain: List<String>? = null,
)

@Serializable
data class Outbound(
    val tag: String,
    val protocol: String,
    val settings: OutboundSettings? = null,
    val streamSettings: StreamSettings? = null,
)

/**
 * One shape covering every protocol's outbound settings: vmess/vless describe the server
 * under [vnext], shadowsocks/trojan under [servers]. Exactly one is ever set.
 */
@Serializable
data class OutboundSettings(
    val vnext: List<VNext>? = null,
    val servers: List<ServerEntry>? = null,
)

@Serializable
data class VNext(
    val address: String,
    val port: Int,
    val users: List<User>,
)

@Serializable
data class User(
    val id: String,
    val level: Int = 8,
    /** vmess only. */
    val alterId: Int? = null,
    /** vmess payload cipher. vless has none and uses [encryption] instead. */
    val security: String? = null,
    /** vless only, always "none" — the transport's TLS does the encrypting. */
    val encryption: String? = null,
    /** vless XTLS flow, e.g. xtls-rprx-vision. */
    val flow: String? = null,
)

/** shadowsocks and trojan both describe the server inline rather than via vnext. */
@Serializable
data class ServerEntry(
    val address: String,
    val port: Int,
    val level: Int = 8,
    /** shadowsocks only. */
    val method: String? = null,
    val password: String? = null,
)
