package com.kmz.v2raytun.data.parser

import com.kmz.v2raytun.data.model.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UriParserTest {

    private val vmess = "vmess://" + Base64Codec.encode(
        """{"ps":"V","add":"a.com","port":"443","id":"u","net":"ws","tls":"tls"}""",
    )
    private val vless = "vless://uuid@b.com:443?security=tls#L"
    private val trojan = "trojan://pw@c.com:443?security=tls#T"
    private val ss = "ss://" + Base64Codec.encode("aes-256-gcm:pw") + "@d.com:8388#S"

    @Test
    fun `dispatches every supported scheme`() {
        val protocols = listOf(vmess, vless, trojan, ss).map {
            (UriParser.parse(it) as ParseResult.Success).profile.protocol
        }
        assertEquals(
            listOf(Protocol.VMESS, Protocol.VLESS, Protocol.TROJAN, Protocol.SHADOWSOCKS),
            protocols,
        )
    }

    @Test
    fun `rejects a non-share-link`() {
        assertTrue(UriParser.parse("https://example.com") is ParseResult.Failure)
        assertTrue(UriParser.parse("just some text") is ParseResult.Failure)
        assertTrue(UriParser.parse("") is ParseResult.Failure)
    }

    @Test
    fun `names the unsupported scheme in the failure reason`() {
        val failure = UriParser.parse("hysteria2://pw@a.com:443") as ParseResult.Failure
        assertTrue(failure.reason.contains("hysteria2"))
    }

    @Test
    fun `parses a newline-separated list`() {
        val results = UriParser.parseMany("$vmess\n$vless\n$trojan\n$ss")
        assertEquals(4, results.size)
        assertTrue(results.all { it is ParseResult.Success })
    }

    @Test
    fun `reports failures alongside successes instead of dropping them`() {
        val results = UriParser.parseMany("$vmess\ngarbage line\n$vless")
        assertEquals(3, results.size)
        assertTrue(results[0] is ParseResult.Success)
        assertTrue(results[1] is ParseResult.Failure)
        assertTrue(results[2] is ParseResult.Success)
    }

    @Test
    fun `skips blank lines and surrounding whitespace`() {
        val results = UriParser.parseMany("\n\n  $vmess  \n\n   \n$vless\n\n")
        assertEquals(2, results.size)
        assertTrue(results.all { it is ParseResult.Success })
    }

    @Test
    fun `unwraps a base64 subscription blob`() {
        val blob = Base64Codec.encode("$vmess\n$vless\n$ss")
        val results = UriParser.parseMany(blob)
        assertEquals(3, results.size)
        assertTrue(results.all { it is ParseResult.Success })
    }

    @Test
    fun `unwraps a subscription blob that arrived line-wrapped`() {
        // HTTP bodies often wrap base64 at a fixed column.
        val blob = Base64Codec.encode("$vmess\n$vless").chunked(64).joinToString("\n")
        val results = UriParser.parseMany(blob)
        assertEquals(2, results.size)
        assertTrue(results.all { it is ParseResult.Success })
    }

    @Test
    fun `handles a single link with no newline`() {
        assertEquals(1, UriParser.parseMany(vmess).size)
    }

    @Test
    fun `returns nothing for empty input`() {
        assertTrue(UriParser.parseMany("").isEmpty())
        assertTrue(UriParser.parseMany("   \n  \n ").isEmpty())
    }

    @Test
    fun `treats undecodable text as a line list rather than a subscription`() {
        val results = UriParser.parseMany("this is not base64 and not a link")
        assertEquals(1, results.size)
        assertTrue(results[0] is ParseResult.Failure)
    }
}
