package com.kmz.v2raytun.data.parser

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Base64CodecTest {

    @Test
    fun `decodes standard padded base64`() {
        assertEquals("hello world", Base64Codec.decodeToStringOrNull("aGVsbG8gd29ybGQ="))
    }

    @Test
    fun `decodes without padding`() {
        // v2rayN and friends routinely strip '='.
        assertEquals("hello world", Base64Codec.decodeToStringOrNull("aGVsbG8gd29ybGQ"))
        assertEquals("any carnal pleasure.", Base64Codec.decodeToStringOrNull("YW55IGNhcm5hbCBwbGVhc3VyZS4"))
    }

    @Test
    fun `decodes url-safe alphabet`() {
        // Bytes chosen so standard encoding yields '+' and '/', url-safe yields '-' and '_'.
        val bytes = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xBF.toByte())
        assertEquals("+/+/", Base64Codec.encode(bytes))
        assertArrayEquals(bytes, Base64Codec.decodeOrNull("-_-_"))
    }

    @Test
    fun `ignores embedded whitespace and newlines`() {
        assertEquals("hello world", Base64Codec.decodeToStringOrNull("aGVsbG8g\nd29y\r\nbGQ="))
    }

    @Test
    fun `rejects non-base64 characters`() {
        assertNull(Base64Codec.decodeOrNull("not base64!"))
        assertNull(Base64Codec.decodeOrNull("aGVsbG8*"))
    }

    @Test
    fun `rejects input that ends mid-byte`() {
        // One trailing character carries 6 bits — not enough for a byte.
        assertNull(Base64Codec.decodeOrNull("Y"))
        // 5 chars = 30 bits = 3 bytes plus a stray 6, so the input ended mid-group.
        assertNull(Base64Codec.decodeOrNull("YWJjZ"))
    }

    @Test
    fun `round-trips through encode and decode`() {
        for (input in listOf("a", "ab", "abc", "abcd", "vmess://x", "🇺🇸 US-1")) {
            val encoded = Base64Codec.encode(input)
            assertEquals(input, Base64Codec.decodeToStringOrNull(encoded))
        }
    }

    @Test
    fun `encode produces correct padding`() {
        assertEquals("YQ==", Base64Codec.encode("a"))
        assertEquals("YWI=", Base64Codec.encode("ab"))
        assertEquals("YWJj", Base64Codec.encode("abc"))
    }

    @Test
    fun `decodeIfText rejects binary payloads`() {
        val binary = Base64Codec.encode(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        assertNull(Base64Codec.decodeIfText(binary))
        assertEquals("plain text", Base64Codec.decodeIfText(Base64Codec.encode("plain text")))
    }

    @Test
    fun `decodeIfText allows newlines because link lists contain them`() {
        val list = "vmess://aaa\nss://bbb"
        assertEquals(list, Base64Codec.decodeIfText(Base64Codec.encode(list)))
    }
}
