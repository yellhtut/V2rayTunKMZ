package com.kmz.v2raytun.core.config

import com.kmz.v2raytun.data.model.Profile
import com.kmz.v2raytun.data.model.Protocol
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Builds the Xray JSON config that the core is started with.
 *
 * The tunnel is wired TUN -> tun2socks -> local socks inbound -> proxy outbound, so the
 * generated config always exposes a socks port on loopback ([SOCKS_PORT]) rather than
 * touching the TUN device itself. That keeps this class pure: it is just a [Profile] to
 * JSON transform, unit-testable without the core or an Android device.
 */
object ConfigGenerator {

    /** Loopback socks inbound that tun2socks forwards into. */
    const val SOCKS_PORT = 10808

    /**
     * Dropping nulls is required, not cosmetic: Xray distinguishes an absent key from a
     * present-but-empty one, so writing `"serverName": ""` would disable SNI instead of
     * letting it default. [encodeDefaults] stays on because real defaults like `level: 8`
     * do need to appear.
     */
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * Sent straight to the proxy without entering the tunnel. Listed as literal CIDRs rather
     * than `geoip:private` so no geoip.dat asset is needed — the app ships without one.
     */
    private val PRIVATE_RANGES = listOf(
        "127.0.0.0/8",
        "10.0.0.0/8",
        "172.16.0.0/12",
        "192.168.0.0/16",
        "169.254.0.0/16",
        "::1/128",
        "fc00::/7",
        "fe80::/10",
    )

    fun toJson(profile: Profile): String = json.encodeToString(generate(profile))

    fun generate(profile: Profile): XrayConfig = XrayConfig(
        inbounds = listOf(
            Inbound(
                tag = "socks",
                port = SOCKS_PORT,
                listen = "127.0.0.1",
                protocol = "socks",
                sniffing = Sniffing(),
            ),
        ),
        outbounds = listOf(
            proxyOutbound(profile),
            Outbound(tag = "direct", protocol = "freedom"),
            Outbound(tag = "block", protocol = "blackhole"),
        ),
        dns = DnsConfig(servers = listOf("1.1.1.1", "8.8.8.8")),
        routing = Routing(
            rules = listOf(RoutingRule(outboundTag = "direct", ip = PRIVATE_RANGES)),
        ),
    )

    private fun proxyOutbound(profile: Profile) = Outbound(
        tag = "proxy",
        protocol = xrayProtocolName(profile.protocol),
        settings = outboundSettings(profile),
        streamSettings = streamSettings(profile),
    )

    /** Xray spells shadowsocks out in full; the others match their scheme. */
    private fun xrayProtocolName(protocol: Protocol): String = when (protocol) {
        Protocol.SHADOWSOCKS -> "shadowsocks"
        else -> protocol.scheme
    }

    private fun outboundSettings(profile: Profile): OutboundSettings = when (profile.protocol) {
        Protocol.VMESS -> OutboundSettings(
            vnext = listOf(
                VNext(
                    address = profile.host,
                    port = profile.port,
                    users = listOf(
                        User(
                            id = profile.uuid,
                            alterId = profile.alterId,
                            security = profile.security.ifEmpty { "auto" },
                        ),
                    ),
                ),
            ),
        )

        Protocol.VLESS -> OutboundSettings(
            vnext = listOf(
                VNext(
                    address = profile.host,
                    port = profile.port,
                    users = listOf(
                        User(
                            id = profile.uuid,
                            encryption = "none",
                            flow = profile.flow.ifEmpty { null },
                        ),
                    ),
                ),
            ),
        )

        Protocol.SHADOWSOCKS -> OutboundSettings(
            servers = listOf(
                ServerEntry(
                    address = profile.host,
                    port = profile.port,
                    method = profile.method,
                    password = profile.password,
                ),
            ),
        )

        Protocol.TROJAN -> OutboundSettings(
            servers = listOf(
                ServerEntry(
                    address = profile.host,
                    port = profile.port,
                    password = profile.password,
                ),
            ),
        )
    }

    /**
     * Shadowsocks is the one protocol that carries no transport layer of its own — Xray
     * rejects a stream block on it — so it gets none. Everything else is described by the
     * transport ([Profile.network]) plus the security layer ([Profile.tls]).
     */
    private fun streamSettings(profile: Profile): StreamSettings? {
        if (profile.protocol == Protocol.SHADOWSOCKS) return null

        val network = profile.network.lowercase().ifEmpty { "tcp" }
        val security = profile.tls.lowercase()

        return StreamSettings(
            network = network,
            security = security,
            tlsSettings = if (security == "tls") tlsSettings(profile) else null,
            realitySettings = if (security == "reality") realitySettings(profile) else null,
            wsSettings = if (network == "ws") wsSettings(profile) else null,
            grpcSettings = if (network == "grpc") grpcSettings(profile) else null,
            httpSettings = if (network == "h2" || network == "http") httpSettings(profile) else null,
            tcpSettings = if (network == "tcp") tcpSettings(profile) else null,
            kcpSettings = if (network == "kcp") kcpSettings(profile) else null,
            quicSettings = if (network == "quic") quicSettings(profile) else null,
            httpupgradeSettings =
                if (network == "httpupgrade") httpUpgradeSettings(profile) else null,
        )
    }

    /**
     * SNI falls back to the explicit host header and then to the server address, matching
     * how clients generally resolve it — a profile whose sni is blank but whose ws Host is
     * set is still expected to present that name.
     */
    private fun serverName(profile: Profile): String =
        profile.sni.ifEmpty { profile.hostHeader.ifEmpty { profile.host } }

    private fun tlsSettings(profile: Profile) = TlsSettings(
        serverName = serverName(profile),
        allowInsecure = profile.allowInsecure,
        alpn = profile.alpn.takeIf { it.isNotEmpty() }
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotEmpty),
        fingerprint = profile.fingerprint.ifEmpty { null },
    )

    private fun realitySettings(profile: Profile) = RealitySettings(
        publicKey = profile.publicKey,
        serverName = serverName(profile),
        shortId = profile.shortId.ifEmpty { null },
        spiderX = profile.spiderX.ifEmpty { null },
        // REALITY is only convincing while imitating a real client, so unlike plain TLS
        // this cannot be left unset.
        fingerprint = profile.fingerprint.ifEmpty { "chrome" },
    )

    private fun wsSettings(profile: Profile) = WsSettings(
        path = profile.path.ifEmpty { "/" },
        headers = profile.hostHeader.takeIf { it.isNotEmpty() }?.let { mapOf("Host" to it) },
    )

    /** For gRPC the service name rides in hostHeader; older links put it in path. */
    private fun grpcSettings(profile: Profile) = GrpcSettings(
        serviceName = profile.hostHeader.ifEmpty { profile.path.trimStart('/') },
        multiMode = profile.headerType == "multi",
    )

    private fun httpSettings(profile: Profile) = HttpSettings(
        path = profile.path.ifEmpty { "/" },
        host = profile.hostHeader.takeIf { it.isNotEmpty() }
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotEmpty),
    )

    private fun tcpSettings(profile: Profile): TcpSettings? {
        // Plain tcp needs no block at all; only the http disguise carries settings.
        if (profile.headerType != "http") return null
        return TcpSettings(
            header = TcpHeader(
                type = "http",
                request = TcpRequest(
                    path = listOf(profile.path.ifEmpty { "/" }),
                    headers = profile.hostHeader.takeIf { it.isNotEmpty() }
                        ?.let { mapOf("Host" to it.split(",").map(String::trim)) },
                ),
            ),
        )
    }

    private fun kcpSettings(profile: Profile) = KcpSettings(
        seed = profile.path.ifEmpty { null },
        header = ObfuscationHeader(type = profile.headerType.ifEmpty { "none" }),
    )

    private fun quicSettings(profile: Profile) = QuicSettings(
        security = profile.security.ifEmpty { "none" },
        key = profile.path,
        header = ObfuscationHeader(type = profile.headerType.ifEmpty { "none" }),
    )

    private fun httpUpgradeSettings(profile: Profile) = HttpUpgradeSettings(
        path = profile.path.ifEmpty { "/" },
        host = profile.hostHeader.ifEmpty { null },
    )
}
