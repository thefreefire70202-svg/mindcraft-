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
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}
