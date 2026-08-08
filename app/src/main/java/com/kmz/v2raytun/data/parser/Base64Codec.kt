package com.kmz.v2raytun.data.parser

/**
 * Base64 that tolerates what share links actually contain.
 *
 * Deliberately hand-rolled rather than delegating to a platform codec:
 *  - `android.util.Base64` is stubbed out under JVM unit tests (returns null when
 *    `unitTests.isReturnDefaultValues` is on), which would make parser tests silently
 *    pass against nulls.
 *  - `java.util.Base64` is API 26+, and this app supports API 24.
 *
 * Beyond portability, real-world links break strict decoders in three ways, all handled
 * here: the URL-safe alphabet (`-_` for `+/`), stripped `=` padding, and embedded
 * whitespace or newlines from copy-paste.
 */
object Base64Codec {

    private const val STANDARD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** Maps every accepted character to its 6-bit value; -1 means "not a base64 char". */
    private val DECODE_TABLE = IntArray(128) { -1 }.apply {
        STANDARD.forEachIndexed { index, c -> this[c.code] = index }
        // Accept the URL-safe alphabet in the same pass, so callers never have to guess
        // which variant a link used.
        this['-'.code] = 62
        this['_'.code] = 63
    }

    /**
     * Decodes [input] to bytes, or returns null if it is not valid base64.
     *
     * Padding is optional; whitespace is ignored. A trailing group of a single character
     * carries no whole byte and is treated as corruption rather than silently dropped.
     */
    fun decodeOrNull(input: String): ByteArray? {
        val out = ByteArray(input.length * 3 / 4 + 3)
        var outLen = 0
        var accumulator = 0
        var bitsCollected = 0

        for (c in input) {
            if (c == '=') break
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t') continue

            val code = c.code
            val value = if (code < 128) DECODE_TABLE[code] else -1
            if (value < 0) return null

            accumulator = (accumulator shl 6) or value
            bitsCollected += 6
            if (bitsCollected >= 8) {
                bitsCollected -= 8
                out[outLen++] = (accumulator shr bitsCollected).toByte()
            }
        }

        // Leftover bits are only legitimate as zero-padding within the final group.
        // 6 stray bits means the input ended mid-byte.
        if (bitsCollected >= 6) return null

        return out.copyOf(outLen)
    }

    /** Decodes [input] to a UTF-8 string, or returns null if it is not valid base64. */
    fun decodeToStringOrNull(input: String): String? =
        decodeOrNull(input)?.toString(Charsets.UTF_8)

    /**
     * Decodes only if the result is plausible text — used when probing whether a blob is
     * a base64-wrapped subscription list rather than a bare list of links. Rejects results
     * containing control characters, which is what binary garbage decodes to.
     */
    fun decodeIfText(input: String): String? {
        val decoded = decodeToStringOrNull(input) ?: return null
        if (decoded.isEmpty()) return null
        val hasControlChars = decoded.any { it.code < 0x20 && it != '\n' && it != '\r' && it != '\t' }
        return if (hasControlChars) null else decoded
    }

    /** Standard-alphabet encoding with padding, for building share links. */
    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i + 2 < bytes.size) {
            val n = (bytes[i].toInt() and 0xFF shl 16) or
                (bytes[i + 1].toInt() and 0xFF shl 8) or
                (bytes[i + 2].toInt() and 0xFF)
            sb.append(STANDARD[n shr 18 and 0x3F])
            sb.append(STANDARD[n shr 12 and 0x3F])
            sb.append(STANDARD[n shr 6 and 0x3F])
            sb.append(STANDARD[n and 0x3F])
            i += 3
        }
        when (bytes.size - i) {
            1 -> {
                val n = bytes[i].toInt() and 0xFF shl 16
                sb.append(STANDARD[n shr 18 and 0x3F])
                sb.append(STANDARD[n shr 12 and 0x3F])
                sb.append("==")
            }
            2 -> {
                val n = (bytes[i].toInt() and 0xFF shl 16) or (bytes[i + 1].toInt() and 0xFF shl 8)
                sb.append(STANDARD[n shr 18 and 0x3F])
                sb.append(STANDARD[n shr 12 and 0x3F])
                sb.append(STANDARD[n shr 6 and 0x3F])
                sb.append('=')
            }
        }
        return sb.toString()
    }

    fun encode(text: String): String = encode(text.toByteArray(Charsets.UTF_8))
}
