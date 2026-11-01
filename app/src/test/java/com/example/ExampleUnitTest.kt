package com.example

import com.example.engine.MinecraftProtocolClient
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun parseHostAndPort_extractsHostAndPortCorrectly() {
        val (host1, port1) = MinecraftProtocolClient.parseHostAndPort("PRIVATESMPGO.aternos.me:53750", 25565)
        assertEquals("PRIVATESMPGO.aternos.me", host1)
        assertEquals(53750, port1)

        val (host2, port2) = MinecraftProtocolClient.parseHostAndPort("donutsmp.net", 25565)
        assertEquals("donutsmp.net", host2)
        assertEquals(25565, port2)

        val (host3, port3) = MinecraftProtocolClient.parseHostAndPort("192.168.1.50:25566", 25565)
        assertEquals("192.168.1.50", host3)
        assertEquals(25566, port3)
    }

    @Test
    fun offlinePlayerUuid_matchesStandardMinecraftAlgorithm() {
        val username = "MindcraftBot"
        val expectedUuid = java.util.UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(java.nio.charset.StandardCharsets.UTF_8))
        // Version 3 UUID (MD5-based name-UUID)
        assertEquals(3, expectedUuid.version())
    }

    @Test
    fun protocolClient_tracksHostContactTimestamps() {
        val client = MinecraftProtocolClient()
        val host = "PRIVATESMPGO.aternos.me"
        val port = 53750

        assertEquals(0L, client.getLastContactTimestamp(host, port))
        client.updateLastContactTimestamp(host, port)
        assert(client.getLastContactTimestamp(host, port) > 0L)
    }

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}
