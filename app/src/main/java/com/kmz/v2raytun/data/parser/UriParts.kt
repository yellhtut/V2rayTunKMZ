package com.kmz.v2raytun.data.parser

/**
 * URI dissection shared by the vless/trojan/ss parsers.
 *
 * Hand-rolled for the same reason as [Base64Codec]: `android.net.Uri` is stubbed under
 * JVM unit tests. `java.net.URI` is available, but rejects some links that clients emit
 * in practice — unencoded characters in the fragment, mainly — and its query handling
 * would still need this much wrapping.
 */
internal object UriParts {

    /**
     * Splits `scheme://userInfo@host:port/path?query#fragment`.
     *
     * Returns null when the scheme separator or the host is missing; every other
     * component is optional.
     */
    fun split(raw: String): Parts? {
        val input = raw.trim()
        val schemeEnd = input.indexOf("://")
        if (schemeEnd <= 0) return null

        val scheme = input.substring(0, schemeEnd)
        var rest = input.substring(schemeEnd + 3)

        // Fragment first: it is last in the URI, and may legally contain ? and @ which
        // would otherwise confuse the splits below.
        var fragment = ""
        val hash = rest.indexOf('#')
        if (hash >= 0) {
            fragment = rest.substring(hash + 1)
            rest = rest.substring(0, hash)
        }

        var query = ""
        val questionMark = rest.indexOf('?')
        if (questionMark >= 0) {
            query = rest.substring(questionMark + 1)
            rest = rest.substring(0, questionMark)
        }

        var path = ""
        val slash = rest.indexOf('/')
        if (slash >= 0) {
            path = rest.substring(slash)
            rest = rest.substring(0, slash)
        }

        // userInfo may itself contain '@' (base64 payloads in ss links do), so split on
        // the LAST '@' — everything before it is credentials, after it is the authority.
        var userInfo = ""
        val at = rest.lastIndexOf('@')
        if (at >= 0) {
            userInfo = rest.substring(0, at)
            rest = rest.substring(at + 1)
        }

        val (host, port) = splitHostPort(rest) ?: return null

        return Parts(
            scheme = scheme,
            userInfo = userInfo,
            host = host,
            port = port,
            path = path,
            query = parseQuery(query),
            fragment = percentDecode(fragment),
        )
    }

    /**
     * Separates host from port, keeping IPv6 literals intact. `[::1]:443` splits to
     * `::1` and 443; a bare `[::1]` yields no port.
     */
    fun splitHostPort(authority: String): Pair<String, Int?>? {
        if (authority.isEmpty()) return null

        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0) return null
            val host = authority.substring(1, close)
            if (host.isEmpty()) return null
            val remainder = authority.substring(close + 1)
            val port = if (remainder.startsWith(":")) {
                remainder.substring(1).toIntOrNull() ?: return null
            } else {
                null
            }
            return host to port
        }

        val colon = authority.lastIndexOf(':')
        if (colon < 0) return authority to null
        val host = authority.substring(0, colon)
        if (host.isEmpty()) return null
        val port = authority.substring(colon + 1).toIntOrNull() ?: return null
        return host to port
    }

    /** Parses `a=1&b=2` into a map, percent-decoding both keys and values. */
    fun parseQuery(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            if (eq < 0) {
                result[percentDecode(pair)] = ""
            } else {
                val key = percentDecode(pair.substring(0, eq))
                if (key.isNotEmpty()) result[key] = percentDecode(pair.substring(eq + 1))
            }
        }
        return result
    }

    /**
     * Percent-decoding for URI components.
     *
     * Note this does NOT treat `+` as a space. `URLDecoder` does, which corrupts ws paths
     * and gRPC service names that legitimately contain `+`. Malformed escapes are passed
     * through literally rather than throwing — a bad `%` in a display name should not
     * fail an otherwise valid link.
     */
    fun percentDecode(value: String): String {
        if (!value.contains('%')) return value

        // Decoding only ever shrinks: a %XX triple (3 bytes as UTF-8) becomes 1 byte, and
        // literal characters keep their own length. So the input's UTF-8 length bounds it.
        val bytes = ByteArray(value.toByteArray(Charsets.UTF_8).size)
        var length = 0
        var i = 0
        while (i < value.length) {
            if (value[i] == '%' && i + 2 < value.length) {
                val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes[length++] = hex.toByte()
                    i += 3
                    continue
                }
            }
            // Encode the whole run of literal characters at once. Doing this per-char
            // would split surrogate pairs and mangle emoji, which show up constantly in
            // server display names.
            val runStart = i
            while (i < value.length && !(value[i] == '%' && i + 2 < value.length &&
                    value.substring(i + 1, i + 3).toIntOrNull(16) != null)
            ) {
                i++
            }
            val encoded = value.substring(runStart, i).toByteArray(Charsets.UTF_8)
            encoded.copyInto(bytes, length)
            length += encoded.size
        }
        return String(bytes, 0, length, Charsets.UTF_8)
    }

    data class Parts(
        val scheme: String,
        val userInfo: String,
        val host: String,
        val port: Int?,
        val path: String,
        val query: Map<String, String>,
        val fragment: String,
    )
}
