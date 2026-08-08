package com.kmz.v2raytun.data.parser

import com.kmz.v2raytun.data.model.Profile
import com.kmz.v2raytun.data.model.Protocol

/**
 * Parses `vless://` and `trojan://` links, which share one URI shape:
 * ```
 * vless://uuid@host:port?encryption=none&security=tls&type=ws&path=/x&sni=a.com#tag
 * trojan://password@host:port?security=tls&sni=a.com#tag
 * ```
 * The only structural difference is that the userInfo is a UUID for vless and a password
 * for trojan, so one implementation covers both with a [protocol] discriminator.
 */
internal class UriStyleParser(private val protocol: Protocol) : ProtocolParser {

    override fun parse(raw: String): ParseResult {
        val label = protocol.scheme
        val parts = UriParts.split(raw)
            ?: return ParseResult.failure(raw, "malformed $label URI")

        if (parts.userInfo.isEmpty()) {
            val missing = if (protocol == Protocol.VLESS) "id" else "password"
            return ParseResult.failure(raw, "$label link has no $missing")
        }

        val port = parts.port
            ?: return ParseResult.failure(raw, "$label link has no valid port")
        if (port !in 1..65535) return ParseResult.failure(raw, "port $port out of range")

        val q = parts.query
        val network = q["type"].orEmpty().ifEmpty { "tcp" }
        val hostHeader = q["host"].orEmpty()

        // gRPC carries its service name in serviceName, other transports use path. The
        // path component of the URI itself is a fallback for clients that put it there.
        val path = when {
            network == "grpc" -> q["serviceName"] ?: q["path"] ?: parts.path
            else -> q["path"] ?: parts.path
        }

        // reality is signalled via security=reality and brings its own key material.
        val security = q["security"].orEmpty().lowercase()
        val tls = if (security == "none") "" else security

        return ParseResult.Success(
            Profile(
                name = parts.fragment.ifEmpty { "${parts.host}:$port" },
                protocol = protocol,
                host = parts.host,
                port = port,
                uuid = if (protocol == Protocol.VLESS) parts.userInfo else "",
                password = if (protocol == Protocol.TROJAN) parts.userInfo else "",
                flow = q["flow"].orEmpty(),
                network = network,
                headerType = q["headerType"].orEmpty().ifEmpty { "none" },
                path = path,
                hostHeader = hostHeader,
                tls = tls,
                sni = q["sni"] ?: q["peer"] ?: hostHeader,
                alpn = q["alpn"].orEmpty(),
                fingerprint = q["fp"].orEmpty(),
                publicKey = q["pbk"].orEmpty(),
                shortId = q["sid"].orEmpty(),
                spiderX = q["spx"].orEmpty(),
                allowInsecure = q["allowInsecure"] == "1" ||
                    q["insecure"] == "1" ||
                    q["allowInsecure"].equals("true", ignoreCase = true),
            ),
        )
    }
}
