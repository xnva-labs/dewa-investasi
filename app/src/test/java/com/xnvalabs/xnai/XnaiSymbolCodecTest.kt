package com.xnvalabs.xnai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class XnaiSymbolCodecTest {
    @Test
    fun alphabetRoundTripsAllMagnitudes() {
        val rnd = Random(7)
        val values = longArrayOf(0, 1, 31, 32, 1023, 1_000_000_000_000L, Long.MAX_VALUE) + LongArray(500) { rnd.nextLong(Long.MAX_VALUE) }
        assertTrue(SymbolAlphabet.decode(SymbolAlphabet.encode(values)).contentEquals(values))
    }

    @Test
    fun codecIsLosslessForMixedContent() {
        val codec = SymbolCodec(InMemorySymbolBackend())
        val samples = listOf(
            "", "  ", "0", "007", "123456789012345678", "1234567890123456789",
            "deadbeefdeadbeefdeadbeef", "ABC abc Abc aBc ß", "x😀y", "a".repeat(100), "-".repeat(200),
            "{\"type\":\"question\",\"id\":\"q-3f9a1c0be2d44a7f8c11e0aa\",\"question\":\"Apa itu gaya?\",\"createdAt\":1765432100000}"
        )
        repeat(5) { samples.forEach { assertEquals(it, codec.decodeString(codec.encodeToString(it))) } }
    }

    @Test
    fun recurringThoughtsBecomeSmallerCompositeSymbols() {
        val codec = SymbolCodec(InMemorySymbolBackend())
        val text = "Apa bukti yang dapat memverifikasi atau membatasi pengetahuan tentang gaya dan percepatan?"
        val first = codec.encode(text).size
        repeat(6) { codec.encode(text) }
        val later = codec.encode(text)
        assertTrue("composite symbols should shorten repeated thoughts", later.size < first)
        assertEquals(text, codec.decode(later))
    }
}
