package com.example.engine

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Packet payload encoder for the explicitly supported Java 1.21.1 (protocol 767)
 * serverbound Play actions. Packet IDs and field order are version-specific.
 */
internal object MinecraftPlayPacketEncoder {
    private const val PLAYER_POSITION_ROTATION = 0x1B
    private const val PLAYER_ACTION = 0x24
    private const val USE_ITEM_ON = 0x38

    fun positionRotation(
        x: Double, y: Double, z: Double,
        yaw: Float, pitch: Float, onGround: Boolean
    ): ByteArray = packet {
        writeVarInt(PLAYER_POSITION_ROTATION)
        writeDouble(x)
        writeDouble(y)
        writeDouble(z)
        writeFloat(yaw)
        writeFloat(pitch)
        writeByte(if (onGround) 1 else 0)
    }

    fun dig(status: Int, x: Int, y: Int, z: Int, face: Int, sequence: Int): ByteArray {
        require(status in 0..6)
        require(face in 0..5)
        require(sequence >= 0)
        return packet {
            writeVarInt(PLAYER_ACTION)
            writeVarInt(status)
            writeLong(packBlockPosition(x, y, z))
            writeByte(face)
            writeVarInt(sequence)
        }
    }

    fun placeBlock(
        x: Int, y: Int, z: Int, face: Int,
        cursorX: Float, cursorY: Float, cursorZ: Float,
        hand: Int, sequence: Int
    ): ByteArray {
        require(face in 0..5)
        require(hand in 0..1)
        require(cursorX in 0f..1f && cursorY in 0f..1f && cursorZ in 0f..1f)
        require(sequence >= 0)
        return packet {
            writeVarInt(USE_ITEM_ON)
            writeVarInt(hand)
            writeLong(packBlockPosition(x, y, z))
            writeVarInt(face)
            writeFloat(cursorX)
            writeFloat(cursorY)
            writeFloat(cursorZ)
            writeByte(0) // insideBlock
            writeVarInt(sequence)
        }
    }

    fun packBlockPosition(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and 0x3FFFFFFL) shl 38) or
            ((z.toLong() and 0x3FFFFFFL) shl 12) or
            (y.toLong() and 0xFFFL)

    private inline fun packet(block: DataOutputStream.() -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { stream -> stream.block() }
        return bytes.toByteArray()
    }

    private fun DataOutputStream.writeVarInt(value: Int) {
        var remaining = value
        while ((remaining and -128) != 0) {
            writeByte((remaining and 0x7F) or 0x80)
            remaining = remaining ushr 7
        }
        writeByte(remaining)
    }
}
