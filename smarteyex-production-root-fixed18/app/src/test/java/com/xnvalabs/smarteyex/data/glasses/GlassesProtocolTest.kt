package com.xnvalabs.smarteyex.data.glasses

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassesProtocolTest {
    private fun u32(v: Long): ByteArray = ByteArray(4) { i -> ((v shr (8 * i)) and 0xFF).toByte() }

    private fun chunk(seq: Int, data: ByteArray): ByteArray =
        byteArrayOf((seq and 0xFF).toByte(), ((seq shr 8) and 0xFF).toByte()) + data

    @Test
    fun commandsEncodeOpcodes() {
        assertArrayEquals(byteArrayOf(0x01), GlassesProtocol.ping())
        assertArrayEquals(byteArrayOf(0x03), GlassesProtocol.capture())
        assertArrayEquals(byteArrayOf(0x04, 1), GlassesProtocol.setLed(true))
        assertArrayEquals(byteArrayOf(0x04, 0), GlassesProtocol.setLed(false))
    }

    @Test
    fun parsesStatusFlags() {
        val event = GlassesProtocol.parseEvent(byteArrayOf(0x82.toByte(), 87, 0b0101, 1))
        val status = (event as GlassesEvent.Status).status
        assertEquals(87, status.batteryPercent)
        assertTrue(status.cameraReady)
        assertTrue(!status.micReady)
        assertTrue(status.charging)
        assertTrue(!status.ledOn)
        assertEquals(1, status.protocolVersion)
    }

    @Test
    fun rejectsTruncatedOrUnknownEvents() {
        assertNull(GlassesProtocol.parseEvent(byteArrayOf()))
        assertNull(GlassesProtocol.parseEvent(byteArrayOf(0x82.toByte(), 50)))
        assertNull(GlassesProtocol.parseEvent(byteArrayOf(0x7F, 1, 2, 3, 4, 5)))
        assertNull(GlassesProtocol.parseEvent(byteArrayOf(0x83.toByte(), 1) + u32(0)))
        assertNull(GlassesProtocol.parseEvent(byteArrayOf(0x83.toByte(), 1) + u32(GlassesProtocol.MAX_FRAME_BYTES + 1L)))
    }

    @Test
    fun assemblesFrameWithValidCrc() {
        val jpeg = ByteArray(100) { (it * 7).toByte() }
        val assembler = FrameAssembler()
        assembler.begin(5, jpeg.size)
        assembler.chunk(chunk(0, jpeg.copyOfRange(0, 60)))
        assembler.chunk(chunk(1, jpeg.copyOfRange(60, 100)))
        val result = assembler.end(5, GlassesProtocol.crc32(jpeg))
        assertTrue(result is FrameAssembler.Result.Complete)
        assertArrayEquals(jpeg, (result as FrameAssembler.Result.Complete).bytes)
    }

    @Test
    fun rejectsCorruptMissingOrShortFrames() {
        val jpeg = ByteArray(40) { it.toByte() }

        val badCrc = FrameAssembler()
        badCrc.begin(1, jpeg.size)
        badCrc.chunk(chunk(0, jpeg))
        assertTrue(badCrc.end(1, GlassesProtocol.crc32(jpeg) + 1) is FrameAssembler.Result.Failed)

        val skipped = FrameAssembler()
        skipped.begin(1, jpeg.size)
        skipped.chunk(chunk(0, jpeg.copyOfRange(0, 20)))
        skipped.chunk(chunk(2, jpeg.copyOfRange(20, 40)))
        assertTrue(skipped.end(1, GlassesProtocol.crc32(jpeg)) is FrameAssembler.Result.Failed)

        val short = FrameAssembler()
        short.begin(1, jpeg.size)
        short.chunk(chunk(0, jpeg.copyOfRange(0, 20)))
        assertTrue(short.end(1, GlassesProtocol.crc32(jpeg)) is FrameAssembler.Result.Failed)

        val wrongId = FrameAssembler()
        wrongId.begin(1, jpeg.size)
        wrongId.chunk(chunk(0, jpeg))
        assertTrue(wrongId.end(2, GlassesProtocol.crc32(jpeg)) is FrameAssembler.Result.Failed)

        val noBegin = FrameAssembler()
        noBegin.chunk(chunk(0, jpeg))
        assertTrue(noBegin.end(1, GlassesProtocol.crc32(jpeg)) is FrameAssembler.Result.Failed)
    }

    @Test
    fun rejectsOverflowingChunks() {
        val assembler = FrameAssembler()
        assembler.begin(1, 10)
        assembler.chunk(chunk(0, ByteArray(11)))
        assertTrue(assembler.end(1, 0) is FrameAssembler.Result.Failed)
    }
}
