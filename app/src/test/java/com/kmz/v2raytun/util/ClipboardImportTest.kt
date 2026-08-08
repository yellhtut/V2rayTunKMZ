package com.kmz.v2raytun.util

import com.kmz.v2raytun.data.parser.Base64Codec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardImportTest {

    private val vmess = "vmess://" + Base64Codec.encode(
        """{"ps":"V","add":"a.com","port":"443","id":"u"}""",
    )
    private val vless = "vless://uuid@b.com:443?security=tls#L"

    @Test
    fun `imports a single pasted link`() {
        val result = ClipboardImport.parse(vmess)
        assertEquals(1, result.importedCount)
        assertEquals(0, result.skippedCount)
        assertEquals("V", result.profiles.first().name)
    }

    @Test
    fun `imports a multi-line paste`() {
        val result = ClipboardImport.parse("$vmess\n$vless")
        assertEquals(2, result.importedCount)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun `imports a base64 subscription blob`() {
        val result = ClipboardImport.parse(Base64Codec.encode("$vmess\n$vless"))
        assertEquals(2, result.importedCount)
    }

    @Test
    fun `counts bad lines as skipped rather than dropping them`() {
        val result = ClipboardImport.parse("$vmess\nnonsense\n$vless\nhttps://example.com")
        assertEquals(2, result.importedCount)
        assertEquals(2, result.skippedCount)
        assertTrue(result.failures.all { it.reason.isNotEmpty() })
    }

    @Test
    fun `treats null and blank clipboards as empty`() {
        assertTrue(ClipboardImport.parse(null).isEmpty)
        assertTrue(ClipboardImport.parse("").isEmpty)
        assertTrue(ClipboardImport.parse("   \n  ").isEmpty)
    }

    @Test
    fun `reports garbage-only input as skipped, not as an empty clipboard`() {
        val result = ClipboardImport.parse("just some random text")
        assertEquals(0, result.importedCount)
        assertEquals(1, result.skippedCount)
        assertTrue(!result.isEmpty)
    }
}
