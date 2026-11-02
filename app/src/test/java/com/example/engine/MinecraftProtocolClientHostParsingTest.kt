package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MinecraftProtocolClientHostParsingTest {
    @Test
    fun parsesHostnameWithDefaultPort() {
        assertEquals(
            "play.example.net" to 25565,
            MinecraftProtocolClient.parseHostAndPort("play.example.net")
        )
    }

    @Test
    fun parsesHostnameWithExplicitPort() {
        assertEquals(
            "play.example.net" to 25570,
            MinecraftProtocolClient.parseHostAndPort("play.example.net:25570")
        )
    }

    @Test
    fun parsesIpv4WithExplicitPort() {
        assertEquals(
            "192.168.1.20" to 25566,
            MinecraftProtocolClient.parseHostAndPort("192.168.1.20:25566")
        )
    }

    @Test
    fun parsesBracketedIpv6WithExplicitPort() {
        assertEquals(
            "2001:db8::1" to 25567,
            MinecraftProtocolClient.parseHostAndPort("[2001:db8::1]:25567")
        )
    }

    @Test
    fun parsesRawIpv6UsingDefaultPort() {
        assertEquals(
            "2001:db8::1" to 25565,
            MinecraftProtocolClient.parseHostAndPort("2001:db8::1")
        )
    }

    @Test
    fun rejectsInvalidExplicitPort() {
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftProtocolClient.parseHostAndPort("play.example.net:70000")
        }
    }

    @Test
    fun rejectsMissingExplicitPort() {
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftProtocolClient.parseHostAndPort("play.example.net:not-a-port")
        }
    }
}
