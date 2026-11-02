package com.example.engine

import java.io.ByteArrayInputStream
import java.io.EOFException

/**
 * Decoder for the protocol-767 clientbound Block Update payload:
 * packed block position (Long), followed by a VarInt block-state ID.
 *
 * This is deliberately independent from the socket loop so packet layout,
 * signed coordinate extraction, and malformed-payload handling are testable.
 * It does not infer block names or collision properties from numeric state IDs.
 */
data class ServerBlockUpdate(
    val position: BlockPos,
    val blockStateId: Int
)

object Protocol767BlockUpdateDecoder {
    fun decode(payload: ByteArray): ServerBlockUpdate {
        require(payload.size >= 9) { "Block Update payload is truncated." }
        val input = ByteArrayInputStream(payload)
        var packed = 0L
        repeat(8) {
            val next = input.read()
            if (next < 0) throw EOFException("Missing packed block position.")
            packed = (packed shl 8) or next.toLong()
        }

        val x = signExtend(packed ushr 38, 26)
        val z = signExtend((packed ushr 12) and 0x3FFFFFFL, 26)
        val y = signExtend(packed and 0xFFFL, 12)
        val stateId = readVarInt(input)
        require(input.available() == 0) { "Unexpected trailing bytes in Block Update payload." }
        require(stateId >= 0) { "Negative block-state ID." }
        return ServerBlockUpdate(BlockPos(x, y, z), stateId)
    }

    private fun signExtend(value: Long, bits: Int): Int {
        val shift = 64 - bits
        return (value shl shift shr shift).toInt()
    }

    private fun readVarInt(input: ByteArrayInputStream): Int {
        var result = 0
        var shift = 0
        repeat(5) {
            val byte = input.read()
            if (byte < 0) throw EOFException("Truncated block-state VarInt.")
            result = result or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
        }
        throw IllegalArgumentException("Block-state VarInt exceeds five bytes.")
    }
}

/**
 * Stores only server-reported block states. State ID 0 is the protocol's
 * air state; no other numeric ID is guessed to be solid/passable.
 */
class ConfirmedBlockStateStore {
    private val states = HashMap<BlockPos, Int>()

    @Synchronized
    fun apply(update: ServerBlockUpdate) {
        states[update.position] = update.blockStateId
    }

    @Synchronized
    fun stateIdAt(position: BlockPos): Int? = states[position]

    @Synchronized
    fun confirmsAir(position: BlockPos): Boolean = states[position] == 0

    @Synchronized
    fun snapshot(): Map<BlockPos, Int> = states.toMap()

    @Synchronized
    fun clear() = states.clear()
}
