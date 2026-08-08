package com.kmz.v2raytun.data.parser

import com.kmz.v2raytun.data.model.Protocol

/**
 * Entry point for turning share links into profiles.
 *
 * Handles the three shapes a user can paste: a single link, a newline-separated list, and
 * a base64-wrapped subscription blob that decodes into such a list.
 */
object UriParser {

    private val parsers: Map<Protocol, ProtocolParser> = mapOf(
        Protocol.VMESS to VmessParser,
        Protocol.VLESS to UriStyleParser(Protocol.VLESS),
        Protocol.TROJAN to UriStyleParser(Protocol.TROJAN),
        Protocol.SHADOWSOCKS to ShadowsocksParser,
    )

    /** Parses exactly one link. Never throws — malformed input yields [ParseResult.Failure]. */
    fun parse(raw: String): ParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ParseResult.failure(raw, "empty line")

        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd <= 0) return ParseResult.failure(raw, "not a share link")

        val scheme = trimmed.substring(0, schemeEnd)
        val protocol = Protocol.fromScheme(scheme)
            ?: return ParseResult.failure(raw, "unsupported protocol '$scheme'")

        // A parser bug should cost one link, not the whole import.
        return try {
            parsers.getValue(protocol).parse(trimmed)
        } catch (e: Exception) {
            ParseResult.failure(raw, "could not parse $scheme link")
        }
    }

    /**
     * Parses everything in [input], which may be a single link, several separated by
     * newlines, or a base64 subscription blob wrapping either.
     *
     * Blank lines are skipped silently. Every non-blank line produces a result, so callers
     * can report exactly which ones failed.
     */
    fun parseMany(input: String): List<ParseResult> {
        val text = input.trim()
        if (text.isEmpty()) return emptyList()

        val content = if (looksLikeLinkList(text)) text else unwrapSubscription(text) ?: text

        return content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { parse(it) }
            .toList()
    }

    /** True when the text already contains a recognizable scheme, so no decode is needed. */
    private fun looksLikeLinkList(text: String): Boolean =
        Protocol.entries.any { text.contains("${it.scheme}://", ignoreCase = true) }

    /**
     * Subscription endpoints return the whole link list base64'd. Returns the decoded
     * list, or null if this was not such a blob.
     */
    private fun unwrapSubscription(text: String): String? {
        // Strip the line breaks a wrapped HTTP body arrives with before decoding.
        val compact = text.filterNot { it == '\n' || it == '\r' || it == ' ' }
        val decoded = Base64Codec.decodeIfText(compact) ?: return null
        return if (looksLikeLinkList(decoded)) decoded else null
    }
}
