package com.kmz.v2raytun.data.parser

import com.kmz.v2raytun.data.model.Profile
import com.kmz.v2raytun.data.model.Protocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Parses `vmess://` links.
 *
 * The common form is `vmess://` + base64 of a JSON object (the "v2rayN format"):
 * ```
 * {"v":"2","ps":"name","add":"host","port":"443","id":"uuid","aid":"0","scy":"auto",
 *  "net":"ws","type":"none","host":"sni.example","path":"/ws","tls":"tls","sni":"...","alpn":"h2"}
 * ```
 * Every numeric field is quoted by some clients and bare in others, so all of them are
 * read leniently.
 *
 * A minority of clients emit a URI-shaped variant instead
 * (`vmess://uuid@host:port?net=ws...`); that is handled as a fallback.
 *
 * Uses kotlinx-serialization rather than `org.json` on purpose: the `org.json` bundled in
 * android.jar is stubbed for JVM unit tests, so parsing would quietly yield defaults there
 * instead of real values.
 */
internal object VmessParser : ProtocolParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun parse(raw: String): ParseResult {
        val body = raw.trim().removePrefix("vmess://").trim()
        if (body.isEmpty()) return ParseResult.failure(raw, "empty vmess link")

        // A '@' before any '?' means this is the URI-shaped variant, not base64 JSON.
        return if (looksLikeUriForm(body)) {
            parseUriForm(raw)
        } else {
            parseJsonForm(raw, body)
        }
    }

    private fun looksLikeUriForm(body: String): Boolean {
        val at = body.indexOf('@')
        if (at < 0) return false
        val question = body.indexOf('?')
        return question < 0 || at < question
    }

    private fun parseJsonForm(raw: String, body: String): ParseResult {
        val decoded = Base64Codec.decodeToStringOrNull(body)
            ?: return ParseResult.failure(raw, "vmess payload is not valid base64")

        val obj = try {
            json.parseToJsonElement(decoded).jsonObject
        } catch (e: Exception) {
            return ParseResult.failure(raw, "vmess payload is not valid JSON")
        }

        val host = obj.str("add")
        if (host.isEmpty()) return ParseResult.failure(raw, "vmess link has no address")

        val port = obj.str("port").toIntOrNull()
            ?: return ParseResult.failure(raw, "vmess link has no valid port")
        if (port !in 1..65535) return ParseResult.failure(raw, "port $port out of range")

        val uuid = obj.str("id")
        if (uuid.isEmpty()) return ParseResult.failure(raw, "vmess link has no id")

        // In vmess JSON, "host" carries the Host header for ws/h2 and the seed for kcp;
        // "path" carries the ws path or the gRPC service name.
        val hostHeader = obj.str("host")

        return ParseResult.Success(
            Profile(
                name = obj.str("ps").ifEmpty { "$host:$port" },
                protocol = Protocol.VMESS,
                host = host,
                port = port,
                uuid = uuid,
                alterId = obj.str("aid").toIntOrNull() ?: 0,
                security = obj.str("scy").ifEmpty { "auto" },
                network = obj.str("net").ifEmpty { "tcp" },
                headerType = obj.str("type").ifEmpty { "none" },
                path = obj.str("path"),
                hostHeader = hostHeader,
                tls = normalizeTls(obj.str("tls")),
                sni = obj.str("sni").ifEmpty { hostHeader },
                alpn = obj.str("alpn"),
                fingerprint = obj.str("fp"),
                allowInsecure = obj.str("allowInsecure").isTruthy(),
            ),
        )
    }

    private fun parseUriForm(raw: String): ParseResult {
        val parts = UriParts.split(raw)
            ?: return ParseResult.failure(raw, "malformed vmess URI")
        val port = parts.port
            ?: return ParseResult.failure(raw, "vmess link has no valid port")
        if (port !in 1..65535) return ParseResult.failure(raw, "port $port out of range")
        if (parts.userInfo.isEmpty()) return ParseResult.failure(raw, "vmess link has no id")

        val q = parts.query
        val hostHeader = q["host"].orEmpty()

        return ParseResult.Success(
            Profile(
                name = parts.fragment.ifEmpty { "${parts.host}:$port" },
                protocol = Protocol.VMESS,
                host = parts.host,
                port = port,
                uuid = parts.userInfo,
                alterId = q["aid"]?.toIntOrNull() ?: 0,
                security = q["encryption"] ?: q["scy"] ?: "auto",
                network = q["type"] ?: q["net"] ?: "tcp",
                headerType = q["headerType"] ?: "none",
                path = q["path"] ?: q["serviceName"] ?: "",
                hostHeader = hostHeader,
                tls = normalizeTls(q["security"].orEmpty()),
                sni = q["sni"] ?: hostHeader,
                alpn = q["alpn"].orEmpty(),
                fingerprint = q["fp"].orEmpty(),
                allowInsecure = q["allowInsecure"].orEmpty().isTruthy(),
            ),
        )
    }

    /** vmess JSON uses "tls" / "" / "none"; normalize the negatives to empty. */
    private fun normalizeTls(value: String): String =
        if (value.isEmpty() || value.equals("none", ignoreCase = true)) "" else value.lowercase()

    private fun String.isTruthy(): Boolean = equals("true", ignoreCase = true) || this == "1"

    /**
     * Reads a field as a string whether the emitter quoted it or not — `"port":"443"` and
     * `"port":443` both yield "443".
     */
    private fun JsonObject.str(key: String): String {
        val primitive = this[key] as? JsonPrimitive ?: return ""
        return primitive.content.trim().takeIf { it != "null" }.orEmpty()
    }
}
