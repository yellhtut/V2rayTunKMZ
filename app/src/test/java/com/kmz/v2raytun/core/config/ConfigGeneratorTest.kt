package com.kmz.v2raytun.core.config

import com.kmz.v2raytun.data.model.Profile
import com.kmz.v2raytun.data.model.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigGeneratorTest {

    private fun profile(
        protocol: Protocol = Protocol.VMESS,
        host: String = "example.com",
        port: Int = 443,
        uuid: String = "b831381d-6324-4d53-ad4f-8cda48b30811",
        password: String = "",
        method: String = "",
        network: String = "tcp",
        tls: String = "",
        path: String = "",
        hostHeader: String = "",
        sni: String = "",
        alpn: String = "",
        fingerprint: String = "",
        publicKey: String = "",
        shortId: String = "",
        flow: String = "",
        headerType: String = "none",
        alterId: Int = 0,
        security: String = "auto",
        allowInsecure: Boolean = false,
    ) = Profile(
        name = "Test",
        protocol = protocol,
        host = host,
        port = port,
        uuid = uuid,
        password = password,
        method = method,
        network = network,
        tls = tls,
        path = path,
        hostHeader = hostHeader,
        sni = sni,
        alpn = alpn,
        fingerprint = fingerprint,
        publicKey = publicKey,
        shortId = shortId,
        flow = flow,
        headerType = headerType,
        alterId = alterId,
        security = security,
        allowInsecure = allowInsecure,
    )

    private fun proxyOf(profile: Profile) =
        ConfigGenerator.generate(profile).outbounds.first { it.tag == "proxy" }

    @Test
    fun `exposes a loopback socks inbound`() {
        val inbound = ConfigGenerator.generate(profile()).inbounds.single()

        assertEquals("socks", inbound.protocol)
        assertEquals(ConfigGenerator.SOCKS_PORT, inbound.port)
        assertEquals("127.0.0.1", inbound.listen)
        assertTrue("udp must be on or DNS breaks", inbound.settings.udp)
        assertEquals(true, inbound.sniffing?.enabled)
    }

    @Test
    fun `enables the outbound traffic counters the core reads back`() {
        // XrayTunnelCore.trafficStats() calls QueryStats("proxy", ...), which reads zero
        // unless the config both turns the stats manager on and asks for outbound counters.
        val config = ConfigGenerator.generate(profile())

        assertTrue(config.policy.system.statsOutboundUplink)
        assertTrue(config.policy.system.statsOutboundDownlink)
        assertTrue("the proxy outbound the counters key off must be present",
            config.outbounds.any { it.tag == "proxy" })
    }

    @Test
    fun `always emits proxy, direct and block outbounds`() {
        val tags = ConfigGenerator.generate(profile()).outbounds.map { it.tag }
        assertEquals(listOf("proxy", "direct", "block"), tags)
    }

    @Test
    fun `routes private ranges direct so the LAN stays reachable`() {
        val rule = ConfigGenerator.generate(profile()).routing?.rules?.single()

        assertEquals("direct", rule?.outboundTag)
        assertTrue(rule?.ip?.contains("192.168.0.0/16") == true)
        assertTrue(rule?.ip?.contains("127.0.0.0/8") == true)
    }

    @Test
    fun `maps vmess to vnext with alterId and cipher`() {
        val out = proxyOf(profile(protocol = Protocol.VMESS, alterId = 4, security = "zero"))
        val user = out.settings?.vnext?.single()?.users?.single()

        assertEquals("vmess", out.protocol)
        assertEquals("example.com", out.settings?.vnext?.single()?.address)
        assertEquals(443, out.settings?.vnext?.single()?.port)
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", user?.id)
        assertEquals(4, user?.alterId)
        assertEquals("zero", user?.security)
        assertNull("vmess has no encryption field", user?.encryption)
    }

    @Test
    fun `maps vless with encryption none and preserves flow`() {
        val user = proxyOf(profile(protocol = Protocol.VLESS, flow = "xtls-rprx-vision"))
            .settings?.vnext?.single()?.users?.single()

        assertEquals("none", user?.encryption)
        assertEquals("xtls-rprx-vision", user?.flow)
        assertNull("vless must not carry alterId", user?.alterId)
        assertNull("vless must not carry a vmess cipher", user?.security)
    }

    @Test
    fun `omits an empty vless flow rather than sending a blank one`() {
        val user = proxyOf(profile(protocol = Protocol.VLESS, flow = ""))
            .settings?.vnext?.single()?.users?.single()
        assertNull(user?.flow)
    }

    @Test
    fun `maps shadowsocks to a server entry and gives it no stream block`() {
        val out = proxyOf(
            profile(
                protocol = Protocol.SHADOWSOCKS,
                method = "aes-256-gcm",
                password = "secret",
            ),
        )
        val server = out.settings?.servers?.single()

        assertEquals("shadowsocks", out.protocol)
        assertEquals("aes-256-gcm", server?.method)
        assertEquals("secret", server?.password)
        assertNull("shadowsocks takes no transport block", out.streamSettings)
        assertNull(out.settings?.vnext)
    }

    @Test
    fun `maps trojan to a password-only server entry`() {
        val out = proxyOf(profile(protocol = Protocol.TROJAN, password = "hunter2"))
        val server = out.settings?.servers?.single()

        assertEquals("trojan", out.protocol)
        assertEquals("hunter2", server?.password)
        assertNull("trojan has no cipher method", server?.method)
    }

    @Test
    fun `builds ws settings with the host header`() {
        val stream = proxyOf(profile(network = "ws", path = "/ray", hostHeader = "cdn.example.com"))
            .streamSettings

        assertEquals("ws", stream?.network)
        assertEquals("/ray", stream?.wsSettings?.path)
        assertEquals(mapOf("Host" to "cdn.example.com"), stream?.wsSettings?.headers)
    }

    @Test
    fun `defaults an empty ws path to slash and omits absent headers`() {
        val ws = proxyOf(profile(network = "ws")).streamSettings?.wsSettings
        assertEquals("/", ws?.path)
        assertNull(ws?.headers)
    }

    @Test
    fun `reads the grpc service name from the host header`() {
        val grpc = proxyOf(profile(network = "grpc", hostHeader = "GunService")).streamSettings
        assertEquals("GunService", grpc?.grpcSettings?.serviceName)
        assertNull(grpc?.wsSettings)
    }

    @Test
    fun `falls back to the path for a grpc service name from older links`() {
        val grpc = proxyOf(profile(network = "grpc", path = "/GunService")).streamSettings
        assertEquals("GunService", grpc?.grpcSettings?.serviceName)
    }

    @Test
    fun `leaves plain tcp without a tcp settings block`() {
        val stream = proxyOf(profile(network = "tcp")).streamSettings
        assertEquals("tcp", stream?.network)
        assertNull(stream?.tcpSettings)
    }

    @Test
    fun `builds the http disguise header for tcp`() {
        val tcp = proxyOf(
            profile(network = "tcp", headerType = "http", path = "/news", hostHeader = "a.com,b.com"),
        ).streamSettings?.tcpSettings

        assertEquals("http", tcp?.header?.type)
        assertEquals(listOf("/news"), tcp?.header?.request?.path)
        assertEquals(listOf("a.com", "b.com"), tcp?.header?.request?.headers?.get("Host"))
    }

    @Test
    fun `builds tls settings and splits alpn`() {
        val tls = proxyOf(
            profile(tls = "tls", sni = "sni.example.com", alpn = "h2,http/1.1", fingerprint = "chrome"),
        ).streamSettings?.tlsSettings

        assertEquals("sni.example.com", tls?.serverName)
        assertEquals(listOf("h2", "http/1.1"), tls?.alpn)
        assertEquals("chrome", tls?.fingerprint)
        assertFalse(tls!!.allowInsecure)
    }

    @Test
    fun `resolves sni from the explicit value then host header then address`() {
        assertEquals(
            "explicit.com",
            proxyOf(profile(tls = "tls", sni = "explicit.com", hostHeader = "header.com"))
                .streamSettings?.tlsSettings?.serverName,
        )
        assertEquals(
            "header.com",
            proxyOf(profile(tls = "tls", hostHeader = "header.com"))
                .streamSettings?.tlsSettings?.serverName,
        )
        assertEquals(
            "example.com",
            proxyOf(profile(tls = "tls")).streamSettings?.tlsSettings?.serverName,
        )
    }

    @Test
    fun `builds reality settings and defaults the fingerprint`() {
        val stream = proxyOf(
            profile(tls = "reality", publicKey = "pubkey123", shortId = "ab12", sni = "real.example.com"),
        ).streamSettings

        assertEquals("reality", stream?.security)
        assertEquals("pubkey123", stream?.realitySettings?.publicKey)
        assertEquals("ab12", stream?.realitySettings?.shortId)
        assertEquals("real.example.com", stream?.realitySettings?.serverName)
        // REALITY only works while imitating a real client, so this cannot be left empty.
        assertEquals("chrome", stream?.realitySettings?.fingerprint)
        assertNull("reality and tls are mutually exclusive", stream?.tlsSettings)
    }

    @Test
    fun `leaves plaintext profiles without any security block`() {
        val stream = proxyOf(profile(tls = "")).streamSettings
        assertEquals("", stream?.security)
        assertNull(stream?.tlsSettings)
        assertNull(stream?.realitySettings)
    }

    @Test
    fun `carries allowInsecure through`() {
        val tls = proxyOf(profile(tls = "tls", allowInsecure = true)).streamSettings?.tlsSettings
        assertTrue(tls!!.allowInsecure)
    }

    @Test
    fun `omits null fields from the json so xray applies its own defaults`() {
        // A blank serverName would disable SNI rather than defaulting, so absent != empty.
        val out = ConfigGenerator.toJson(profile(protocol = Protocol.VLESS, tls = "tls"))

        assertFalse("nulls must not be serialised", out.contains("null"))
        assertFalse("unused transports must be absent", out.contains("kcpSettings"))
        assertFalse(out.contains("realitySettings"))
        assertTrue(out.contains("\"tlsSettings\""))
    }

    @Test
    fun `produces parseable json for every protocol`() {
        val profiles = listOf(
            profile(protocol = Protocol.VMESS, network = "ws", tls = "tls"),
            profile(protocol = Protocol.VLESS, network = "grpc", tls = "reality", publicKey = "k"),
            profile(protocol = Protocol.SHADOWSOCKS, method = "aes-256-gcm", password = "p"),
            profile(protocol = Protocol.TROJAN, password = "p", tls = "tls"),
        )
        for (p in profiles) {
            val json = ConfigGenerator.toJson(p)
            assertNotNull(json)
            assertTrue("expected an outbounds block for ${p.protocol}", json.contains("outbounds"))
            assertTrue("expected the proxy tag for ${p.protocol}", json.contains("\"proxy\""))
        }
    }
}
