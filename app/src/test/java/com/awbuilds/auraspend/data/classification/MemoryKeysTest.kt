package com.awbuilds.auraspend.data.classification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryKeysTest {

    @Test
    fun `normalizes case and collapses whitespace`() {
        assertEquals("swiggy", MemoryKeys.normalize("SWIGGY"))
        assertEquals("swiggy", MemoryKeys.normalize("  SWIGGY   "))
    }

    @Test
    fun `stops at separator keeping left side`() {
        assertEquals("swiggy", MemoryKeys.normalize("Swiggy*Bangalore"))
        assertEquals("swiggy", MemoryKeys.normalize("swiggy@upi"))
    }

    @Test
    fun `drops stop words`() {
        assertEquals("swiggy", MemoryKeys.normalize("SWIGGY ltd india pvt"))
    }

    @Test
    fun `drops digits and reference numbers`() {
        assertNull(MemoryKeys.normalize("123456789012"))
        assertEquals("netflix", MemoryKeys.normalize("NETFLIX.COM 890123"))
    }

    @Test
    fun `keeps ampersand words`() {
        assertEquals("food & dining", MemoryKeys.normalize("Food & Dining"))
    }

    @Test
    fun `null blank or noise only yields null`() {
        assertNull(MemoryKeys.normalize(null))
        assertNull(MemoryKeys.normalize(""))
        assertNull(MemoryKeys.normalize("***"))
        assertNull(MemoryKeys.normalize("@@@ ###"))
    }

    @Test
    fun `keys are capped at 64 chars`() {
        val longKey = MemoryKeys.normalize("word ".repeat(40))
        assertEquals(64, longKey?.length)
    }

    @Test
    fun `fromParsed prefers merchant over note`() {
        assertEquals("swiggy", MemoryKeys.fromParsed("Swiggy", "order food 123"))
        assertEquals("food order", MemoryKeys.fromParsed(null, "Food Order #123"))
        assertNull(MemoryKeys.fromParsed(null, null))
    }

    @Test
    fun `candidates include the full key and a general first token`() {
        assertEquals(
            listOf("swiggy bangalore", "swiggy"),
            MemoryKeys.candidates("SWIGGY BANGALORE")
        )
    }

    @Test
    fun `candidates for a single token do not duplicate it`() {
        assertEquals(listOf("swiggy"), MemoryKeys.candidates("SWIGGY"))
    }
}
