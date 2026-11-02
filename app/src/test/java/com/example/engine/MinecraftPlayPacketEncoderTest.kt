package com.example.engine

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftPlayPacketEncoderTest {
    @Test fun positionRotationUsesProtocol767PacketAndFieldOrder() {
        val input = DataInputStream(ByteArrayInputStream(
            MinecraftPlayPacketEncoder.positionRotation(1.25, 64.0, -2.5, 90f, -10f, true)
        ))
        assertEquals(0x1B, input.readUnsignedByte())
        assertEquals(1.25, input.readDouble(), 0.0)
        assertEquals(64.0, input.readDouble(), 0.0)
        assertEquals(-2.5, input.readDouble(), 0.0)
        assertEquals(90f, input.readFloat(), 0f)
        assertEquals(-10f, input.readFloat(), 0f)
        assertTrue(input.readBoolean())
        assertEquals(0, input.available())
    }

    @Test fun blockPositionPacksPositiveAndNegativeCoordinates() {
        val packed = MinecraftPlayPacketEncoder.packBlockPosition(-12, 255, 37)
        assertEquals(-12, (packed shr 38).toInt())
        assertEquals(255, (packed and 0xFFFL).toInt())
        assertEquals(37, ((packed shr 12) and 0x3FFFFFFL).toInt())
    }

    @Test fun digPacketContainsStatusPositionFaceAndSequence() {
        val input = DataInputStream(ByteArrayInputStream(
            MinecraftPlayPacketEncoder.dig(2, 4, 65, -7, 1, 9)
        ))
        assertEquals(0x24, input.readUnsignedByte())
        assertEquals(2, input.readUnsignedByte())
        val packed = input.readLong()
        assertEquals(4, (packed shr 38).toInt())
        assertEquals(65, (packed and 0xFFFL).toInt())
        assertEquals(-7, ((packed shr 12) and 0x3FFFFFFL).toInt())
        assertEquals(1, input.readUnsignedByte())
        assertEquals(9, input.readUnsignedByte())
        assertEquals(0, input.available())
    }

    @Test fun placePacketEncodesHandFaceCursorAndSequence() {
        val input = DataInputStream(ByteArrayInputStream(
            MinecraftPlayPacketEncoder.placeBlock(1, 2, 3, 5, .25f, .5f, .75f, 0, 4)
        ))
        assertEquals(0x38, input.readUnsignedByte())
        assertEquals(0, input.readUnsignedByte())
        input.readLong()
        assertEquals(5, input.readUnsignedByte())
        assertEquals(.25f, input.readFloat(), 0f)
        assertEquals(.5f, input.readFloat(), 0f)
        assertEquals(.75f, input.readFloat(), 0f)
        assertFalse(input.readBoolean())
        assertEquals(4, input.readUnsignedByte())
        assertEquals(0, input.available())
    }
}
