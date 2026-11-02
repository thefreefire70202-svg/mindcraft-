package com.example.engine

import org.junit.Assert.*
import org.junit.Test

class Protocol767BlockUpdateDecoderTest {
    @Test fun decodesPositiveCoordinatesAndStateId() {
        val update = Protocol767BlockUpdateDecoder.decode(payload(12, 64, 31, 300))
        assertEquals(BlockPos(12, 64, 31), update.position)
        assertEquals(300, update.blockStateId)
    }

    @Test fun decodesNegativeCoordinates() {
        val update = Protocol767BlockUpdateDecoder.decode(payload(-12, -64, -31, 0))
        assertEquals(BlockPos(-12, -64, -31), update.position)
        assertEquals(0, update.blockStateId)
    }

    @Test fun rejectsTruncatedPayload() {
        try {
            Protocol767BlockUpdateDecoder.decode(byteArrayOf(0, 1, 2))
            fail("Expected truncated payload rejection")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun rejectsTrailingPayloadBytes() {
        try {
            Protocol767BlockUpdateDecoder.decode(payload(1, 2, 3, 4) + byteArrayOf(9))
            fail("Expected trailing byte rejection")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun storeConfirmsOnlyServerReportedAir() {
        val store = ConfirmedBlockStateStore()
        val position = BlockPos(4, 5, 6)
        assertFalse(store.confirmsAir(position))
        store.apply(ServerBlockUpdate(position, 42))
        assertFalse(store.confirmsAir(position))
        store.apply(ServerBlockUpdate(position, 0))
        assertTrue(store.confirmsAir(position))
    }

    private fun payload(x: Int, y: Int, z: Int, stateId: Int): ByteArray {
        val packed = ((x.toLong() and 0x3FFFFFFL) shl 38) or
            ((z.toLong() and 0x3FFFFFFL) shl 12) or (y.toLong() and 0xFFFL)
        val out = ArrayList<Byte>()
        for (shift in 56 downTo 0 step 8) out += ((packed ushr shift) and 0xFF).toByte()
        var value = stateId
        do {
            var part = value and 0x7F
            value = value ushr 7
            if (value != 0) part = part or 0x80
            out += part.toByte()
        } while (value != 0)
        return out.toByteArray()
    }
}
