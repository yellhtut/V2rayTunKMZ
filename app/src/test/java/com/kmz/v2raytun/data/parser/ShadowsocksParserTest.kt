package com.kmz.v2raytun.data.parser

import com.kmz.v2raytun.data.model.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadowsocksParserTest {

    private fun parseOk(raw: String) =
        (UriParser.parse(raw) as? ParseResult.Success)?.profile
            ?: error("expected success, got ${UriParser.parse(raw)}")

    @Test
    fun `parses SIP002 with base64 credentials`() {
        val creds = Base64Codec.encode("aes-256-gcm:my-password")
        val profile = parseOk("ss://$creds@example.com:8388#Node%201")

        assertEquals(Protocol.SHADOWSOCKS, profile.protocol)
        assertEquals("Node 1", profile.name)
        assertEquals("example.com", profile.host)
        assertEquals(8388, profile.port)
        assertEquals("aes-256-gcm", profile.method)
        assertEquals("my-password", profile.password)
    }

    @Test
    fun `parses SIP002 with plaintext credentials`() {
        val profile = parseOk("ss://chacha20-ietf-poly1305:pass123@1.2.3.4:443#Plain")
        assertEquals("chacha20-ietf-poly1305", profile.method)
        assertEquals("pass123", profile.password)
        assertEquals("1.2.3.4", profile.host)
        assertEquals(443, profile.port)
    }

    @Test
    fun `parses the legacy fully-encoded form`() {
        val body = Base64Codec.encode("aes-128-gcm:secret@legacy.example.com:1080")
        val profile = parseOk("ss://$body#Legacy")

        assertEquals("Legacy", profile.name)
        assertEquals("legacy.example.com", profile.host)
        assertEquals(1080, profile.port)
        assertEquals("aes-128-gcm", profile.method)
        assertEquals("secret", profile.password)
    }

    @Test
    fun `keeps colons inside the password`() {
        // Splitting credentials on the last colon would truncate this to "pass".
        val creds = Base64Codec.encode("aes-256-gcm:pass:with:colons")
        assertEquals("pass:with:colons", parseOk("ss://$creds@a.com:443").password)
    }

    @Test
    fun `keeps an at-sign inside the password`() {
        // The authority split must use the LAST '@', not the first.
        val creds = Base64Codec.encode("aes-256-gcm:p@ssw0rd")
        val profile = parseOk("ss://$creds@a.com:443")
        assertEquals("p@ssw0rd", profile.password)
        assertEquals("a.com", profile.host)
    }

    @Test
    fun `parses an ipv6 literal address`() {
        val profile = parseOk("ss://aes-256-gcm:pw@[2001:db8::1]:8388#v6")
        assertEquals("2001:db8::1", profile.host)
        assertEquals(8388, profile.port)
    }

    @Test
    fun `names the profile after the address when the tag is missing`() {
        assertEquals("a.com:443", parseOk("ss://aes-256-gcm:pw@a.com:443").name)
    }

    @Test
    fun `rejects unsupported SIP003 plugins by name`() {
        val creds = Base64Codec.encode("aes-256-gcm:pw")
        val result = UriParser.parse("ss://$creds@a.com:443?plugin=obfs-local;obfs=http")
        val failure = result as ParseResult.Failure
        assertTrue(failure.reason.contains("obfs-local"))
        assertTrue(failure.reason.contains("not supported"))
    }

    @Test
    fun `rejects malformed links cleanly`() {
        val cases = listOf(
            "ss://",
            "ss://!!!not-base64!!!",
            "ss://${Base64Codec.encode("no-colon-here@a.com:443")}",
            "ss://${Base64Codec.encode("aes-256-gcm:pw")}",
            "ss://aes-256-gcm:pw@a.com",
            "ss://aes-256-gcm:pw@a.com:99999",
            "ss://aes-256-gcm:@a.com:443",
        )
        for (input in cases) {
            assertTrue(
                "expected failure for $input",
                UriParser.parse(input) is ParseResult.Failure,
            )
        }
    }
}
