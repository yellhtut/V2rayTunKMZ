package com.kmz.v2raytun.data.parser

import com.kmz.v2raytun.data.model.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UriStyleParserTest {

    private fun parseOk(raw: String) =
        (UriParser.parse(raw) as? ParseResult.Success)?.profile
            ?: error("expected success, got ${UriParser.parse(raw)}")

    @Test
    fun `parses a vless reality link`() {
        val profile = parseOk(
            "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443" +
                "?encryption=none&security=reality&type=tcp&flow=xtls-rprx-vision" +
                "&sni=www.microsoft.com&fp=chrome&pbk=abcdef123&sid=1a2b#Reality%20Node",
        )

        assertEquals(Protocol.VLESS, profile.protocol)
        assertEquals("Reality Node", profile.name)
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", profile.uuid)
        assertEquals("example.com", profile.host)
        assertEquals(443, profile.port)
        assertEquals("reality", profile.tls)
        assertEquals("xtls-rprx-vision", profile.flow)
        assertEquals("www.microsoft.com", profile.sni)
        assertEquals("chrome", profile.fingerprint)
        assertEquals("abcdef123", profile.publicKey)
        assertEquals("1a2b", profile.shortId)
    }

    @Test
    fun `parses a vless websocket link`() {
        val profile = parseOk(
            "vless://uuid@cdn.example.com:443?type=ws&path=%2Fwebsocket&host=front.example.com&security=tls#WS",
        )
        assertEquals("ws", profile.network)
        assertEquals("/websocket", profile.path)
        assertEquals("front.example.com", profile.hostHeader)
        assertEquals("tls", profile.tls)
        // sni falls back to the Host header when not given explicitly.
        assertEquals("front.example.com", profile.sni)
    }

    @Test
    fun `reads the grpc service name`() {
        val profile = parseOk("vless://uuid@a.com:443?type=grpc&serviceName=GunService&security=tls")
        assertEquals("grpc", profile.network)
        assertEquals("GunService", profile.path)
    }

    @Test
    fun `parses a trojan link and stores the password`() {
        val profile = parseOk("trojan://my-password@example.com:443?security=tls&sni=example.com#Trojan%20A")

        assertEquals(Protocol.TROJAN, profile.protocol)
        assertEquals("Trojan A", profile.name)
        assertEquals("my-password", profile.password)
        assertEquals("", profile.uuid)
        assertEquals("example.com", profile.host)
        assertEquals(443, profile.port)
    }

    @Test
    fun `treats security none as plaintext`() {
        assertEquals("", parseOk("vless://uuid@a.com:80?security=none").tls)
        assertEquals("", parseOk("vless://uuid@a.com:80").tls)
    }

    @Test
    fun `parses an ipv6 literal address`() {
        val profile = parseOk("vless://uuid@[2001:db8::1]:443?security=tls")
        assertEquals("2001:db8::1", profile.host)
        assertEquals(443, profile.port)
    }

    @Test
    fun `does not turn plus into a space in the path`() {
        // URLDecoder would corrupt this; the path must survive verbatim.
        assertEquals("/a+b", parseOk("vless://uuid@a.com:443?type=ws&path=/a+b").path)
    }

    @Test
    fun `honours the insecure flag under either spelling`() {
        assertTrue(parseOk("trojan://pw@a.com:443?allowInsecure=1").allowInsecure)
        assertTrue(parseOk("trojan://pw@a.com:443?insecure=1").allowInsecure)
    }

    @Test
    fun `rejects malformed links cleanly`() {
        val cases = listOf(
            "vless://",
            "vless://@a.com:443",
            "vless://uuid@a.com",
            "vless://uuid@a.com:0",
            "vless://uuid@a.com:70000",
            "trojan://a.com:443",
        )
        for (input in cases) {
            assertTrue(
                "expected failure for $input",
                UriParser.parse(input) is ParseResult.Failure,
            )
        }
    }
}
