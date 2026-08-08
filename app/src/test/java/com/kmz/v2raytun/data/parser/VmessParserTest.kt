package com.kmz.v2raytun.data.parser

import com.kmz.v2raytun.data.model.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VmessParserTest {

    private fun link(json: String) = "vmess://" + Base64Codec.encode(json)

    private fun parseOk(raw: String) =
        (UriParser.parse(raw) as? ParseResult.Success)?.profile
            ?: error("expected success, got ${UriParser.parse(raw)}")

    @Test
    fun `parses a full v2rayN format link`() {
        val profile = parseOk(
            link(
                """
                {"v":"2","ps":"Test Server","add":"example.com","port":"443",
                 "id":"b831381d-6324-4d53-ad4f-8cda48b30811","aid":"0","scy":"auto",
                 "net":"ws","type":"none","host":"cdn.example.com","path":"/ws",
                 "tls":"tls","sni":"cdn.example.com","alpn":"h2"}
                """.trimIndent(),
            ),
        )

        assertEquals("Test Server", profile.name)
        assertEquals(Protocol.VMESS, profile.protocol)
        assertEquals("example.com", profile.host)
        assertEquals(443, profile.port)
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", profile.uuid)
        assertEquals("ws", profile.network)
        assertEquals("/ws", profile.path)
        assertEquals("cdn.example.com", profile.hostHeader)
        assertEquals("tls", profile.tls)
        assertEquals("h2", profile.alpn)
    }

    @Test
    fun `reads numeric fields whether quoted or bare`() {
        val quoted = parseOk(link("""{"add":"a.com","port":"8443","id":"u","aid":"4"}"""))
        val bare = parseOk(link("""{"add":"a.com","port":8443,"id":"u","aid":4}"""))

        assertEquals(8443, quoted.port)
        assertEquals(4, quoted.alterId)
        assertEquals(quoted.port, bare.port)
        assertEquals(quoted.alterId, bare.alterId)
    }

    @Test
    fun `treats tls none as plaintext`() {
        assertEquals("", parseOk(link("""{"add":"a.com","port":"80","id":"u","tls":"none"}""")).tls)
        assertEquals("", parseOk(link("""{"add":"a.com","port":"80","id":"u","tls":""}""")).tls)
        assertEquals("tls", parseOk(link("""{"add":"a.com","port":"443","id":"u","tls":"tls"}""")).tls)
    }

    @Test
    fun `falls back to host header for sni when sni is absent`() {
        val profile = parseOk(
            link("""{"add":"1.2.3.4","port":"443","id":"u","net":"ws","host":"cdn.example.com"}"""),
        )
        assertEquals("cdn.example.com", profile.sni)
    }

    @Test
    fun `names the profile after the address when ps is missing`() {
        assertEquals("a.com:443", parseOk(link("""{"add":"a.com","port":"443","id":"u"}""")).name)
    }

    @Test
    fun `defaults network to tcp`() {
        assertEquals("tcp", parseOk(link("""{"add":"a.com","port":"443","id":"u"}""")).network)
    }

    @Test
    fun `decodes a unicode profile name intact`() {
        val profile = parseOk(link("""{"ps":"🇺🇸 US-1","add":"a.com","port":"443","id":"u"}"""))
        assertEquals("🇺🇸 US-1", profile.name)
    }

    @Test
    fun `parses the uri-shaped variant`() {
        val profile = parseOk("vmess://uuid-here@example.com:443?type=ws&path=/x&security=tls#Alt%20Form")
        assertEquals("Alt Form", profile.name)
        assertEquals("uuid-here", profile.uuid)
        assertEquals("example.com", profile.host)
        assertEquals(443, profile.port)
        assertEquals("ws", profile.network)
        assertEquals("/x", profile.path)
        assertEquals("tls", profile.tls)
    }

    @Test
    fun `rejects malformed links cleanly`() {
        val cases = mapOf(
            "vmess://" to "empty",
            "vmess://!!!not-base64!!!" to "base64",
            "vmess://${Base64Codec.encode("not json at all")}" to "JSON",
            "vmess://${Base64Codec.encode("""{"port":"443","id":"u"}""")}" to "address",
            "vmess://${Base64Codec.encode("""{"add":"a.com","id":"u"}""")}" to "port",
            "vmess://${Base64Codec.encode("""{"add":"a.com","port":"443"}""")}" to "id",
            "vmess://${Base64Codec.encode("""{"add":"a.com","port":"99999","id":"u"}""")}" to "range",
        )
        for ((input, expectedFragment) in cases) {
            val result = UriParser.parse(input)
            assertTrue("expected failure for $input, got $result", result is ParseResult.Failure)
            val reason = (result as ParseResult.Failure).reason
            assertTrue(
                "reason '$reason' should mention '$expectedFragment'",
                reason.contains(expectedFragment, ignoreCase = true),
            )
        }
    }

    @Test
    fun `failure preview does not leak the full link`() {
        val secret = "b831381d-6324-4d53-ad4f-8cda48b30811"
        val result = UriParser.parse("vmess://${Base64Codec.encode("""{"id":"$secret"}""")}")
        val failure = result as ParseResult.Failure
        assertTrue(failure.preview.length <= 25)
        assertTrue(!failure.preview.contains(secret))
    }
}
