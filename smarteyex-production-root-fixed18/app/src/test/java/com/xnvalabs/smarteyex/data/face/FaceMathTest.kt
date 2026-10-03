package com.xnvalabs.smarteyex.data.face

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FaceMathTest {
    private fun vec(vararg head: Float): FloatArray = FloatArray(64) { i -> head.getOrElse(i) { 0f } }

    private val alice = FaceTemplate("a", "Alice", listOf(vec(1f, 0f), vec(0.95f, 0.1f)))
    private val bob = FaceTemplate("b", "Bob", listOf(vec(0f, 1f)))

    @Test
    fun cosineBasics() {
        assertEquals(1f, FaceMath.cosine(vec(1f, 2f), vec(2f, 4f)), 1e-5f)
        assertEquals(0f, FaceMath.cosine(vec(1f, 0f), vec(0f, 1f)), 1e-5f)
        assertEquals(-1f, FaceMath.cosine(vec(1f), FloatArray(3)), 0f)
        assertEquals(-1f, FaceMath.cosine(vec(0f), vec(1f)), 0f)
    }

    @Test
    fun normalizeProducesUnitLength() {
        val n = FaceMath.l2Normalize(vec(3f, 4f))
        assertEquals(1f, FaceMath.cosine(n, n), 1e-5f)
        assertEquals(0.6f, n[0], 1e-5f)
    }

    @Test
    fun matchesKnownPerson() {
        val result = FaceMath.bestMatch(vec(0.98f, 0.05f), listOf(alice, bob))
        assertNotNull(result.person)
        assertEquals("Alice", result.person?.name)
    }

    @Test
    fun rejectsBelowThreshold() {
        val result = FaceMath.bestMatch(vec(0.5f, 0.5f, 1f), listOf(alice, bob))
        assertNull(result.person)
    }

    @Test
    fun rejectsAmbiguousBetweenTwoPeople() {
        val twin = FaceTemplate("t", "Twin", listOf(vec(0.99f, 0.02f)))
        val result = FaceMath.bestMatch(vec(0.99f, 0.05f), listOf(alice, twin))
        assertNull(result.person)
    }

    @Test
    fun emptyGalleryIsUnknown() {
        assertNull(FaceMath.bestMatch(vec(1f), emptyList()).person)
    }

    @Test
    fun encodeDecodeRoundTripAndValidation() {
        val v = vec(0.25f, -0.5f, 0.75f)
        assertArrayEquals(v, FaceMath.decode(FaceMath.encode(v))!!, 0f)
        assertNull(FaceMath.decode(byteArrayOf(1, 2, 3)))
        assertNull(FaceMath.decode(FaceMath.encode(FloatArray(4))))
        assertNull(FaceMath.decode(FaceMath.encode(vec(Float.NaN))))
    }
}
