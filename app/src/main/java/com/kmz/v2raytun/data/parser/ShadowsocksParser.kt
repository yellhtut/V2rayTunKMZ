package com.kmz.v2raytun.data.parser

import com.kmz.v2raytun.data.model.Profile
import com.kmz.v2raytun.data.model.Protocol

/**
 * Parses `ss://` links, which exist in two incompatible shapes under one scheme.
 *
 * **SIP002** (current): `ss://base64(method:password)@host:port?plugin=...#tag`
 * — only the credentials are base64'd; the authority is plaintext. Some emitters skip the
 * base64 entirely and write `ss://method:password@host:port`.
 *
 * **Legacy**: `ss://base64(method:password@host:port)#tag` — the whole body is base64'd.
 *
 * Disambiguation is by structure, not by guessing: if a `@` survives outside the base64
 * blob, it is SIP002. Otherwise the body is decoded and re-examined.
 */
internal object ShadowsocksParser : ProtocolParser {

    override fun parse(raw: String): ParseResult {
        val trimmed = raw.trim()
        var body = trimmed.removePrefix("ss://")
        if (body.isEmpty()) return ParseResult.failure(raw, "empty ss link")

        // The tag is outside the base64 in both forms, so lift it off first.
        var tag = ""
        val hash = body.indexOf('#')
        if (hash >= 0) {
            tag = UriParts.percentDecode(body.substring(hash + 1))
            body = body.substring(0, hash)
        }

        return if (body.contains('@')) {
            parseSip002(raw, body, tag)
        } else {
            parseLegacy(raw, body, tag)
        }
    }

    /** `base64(method:password)@host:port?plugin=...` — or plaintext credentials. */
    private fun parseSip002(raw: String, body: String, tag: String): ParseResult {
        var rest = body
        var query = emptyMap<String, String>()
        val question = rest.indexOf('?')
        if (question >= 0) {
            query = UriParts.parseQuery(rest.substring(question + 1))
            rest = rest.substring(0, question)
        }

        // Split on the LAST '@': a base64 credential blob can itself contain '@' once
        // decoded, and padding characters never include it, so the last one is the
        // authority separator.
        val at = rest.lastIndexOf('@')
        val credentialPart = rest.substring(0, at)
        val authority = rest.substring(at + 1)

        val (host, port) = UriParts.splitHostPort(authority)
            ?: return ParseResult.failure(raw, "ss link has a malformed address")
        if (port == null) return ParseResult.failure(raw, "ss link has no port")
        if (port !in 1..65535) return ParseResult.failure(raw, "port $port out of range")

        // Credentials may be base64'd or already plaintext.
        val credentials = if (credentialPart.contains(':')) {
            UriParts.percentDecode(credentialPart)
        } else {
            Base64Codec.decodeToStringOrNull(credentialPart)
                ?: return ParseResult.failure(raw, "ss credentials are not valid base64")
        }

        val (method, password) = splitCredentials(credentials)
            ?: return ParseResult.failure(raw, "ss credentials are not method:password")

        // SIP003 plugins (obfs-local, v2ray-plugin, …) need either a separate native
        // binary or a transport mapping this client does not implement. Rejecting with a
        // reason beats importing a profile that silently never connects.
        val plugin = query["plugin"].orEmpty()
        if (plugin.isNotEmpty()) {
            val pluginName = plugin.substringBefore(';')
            return ParseResult.failure(raw, "ss plugin '$pluginName' is not supported")
        }

        return success(host, port, method, password, tag, query)
    }

    /** `base64(method:password@host:port)` — the whole body is encoded. */
    private fun parseLegacy(raw: String, body: String, tag: String): ParseResult {
        val decoded = Base64Codec.decodeToStringOrNull(body)
            ?: return ParseResult.failure(raw, "ss payload is not valid base64")

        val at = decoded.lastIndexOf('@')
        if (at < 0) return ParseResult.failure(raw, "ss payload is missing its address")

        val credentials = decoded.substring(0, at)
        val (host, port) = UriParts.splitHostPort(decoded.substring(at + 1))
            ?: return ParseResult.failure(raw, "ss link has a malformed address")
        if (port == null) return ParseResult.failure(raw, "ss link has no port")
        if (port !in 1..65535) return ParseResult.failure(raw, "port $port out of range")

        val (method, password) = splitCredentials(credentials)
            ?: return ParseResult.failure(raw, "ss credentials are not method:password")

        return success(host, port, method, password, tag, emptyMap())
    }

    /**
     * Splits on the FIRST colon: the method never contains one, and passwords frequently
     * do. Splitting on the last colon would silently truncate such passwords.
     */
    private fun splitCredentials(credentials: String): Pair<String, String>? {
        val colon = credentials.indexOf(':')
        if (colon <= 0) return null
        val method = credentials.substring(0, colon).trim()
        val password = credentials.substring(colon + 1)
        if (method.isEmpty() || password.isEmpty()) return null
        return method to password
    }

    private fun success(
        host: String,
        port: Int,
        method: String,
        password: String,
        tag: String,
        query: Map<String, String>,
    ): ParseResult {
        // v2ray-style transport parameters occasionally ride along on ss links.
        val network = query["type"].orEmpty().ifEmpty { "tcp" }
        val hostHeader = query["host"].orEmpty()

        return ParseResult.Success(
            Profile(
                name = tag.ifEmpty { "$host:$port" },
                protocol = Protocol.SHADOWSOCKS,
                host = host,
                port = port,
                method = method,
                password = password,
                network = network,
                path = query["path"].orEmpty(),
                hostHeader = hostHeader,
                tls = query["security"].orEmpty().let { if (it == "none") "" else it },
                sni = query["sni"] ?: hostHeader,
            ),
        )
    }
}
