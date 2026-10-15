package com.awbuilds.auraspend.data.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `a pin round-trips through its salt and hash`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("1234", salt)
        assertTrue(PinHasher.verify("1234", salt, hash))
    }

    @Test
    fun `a wrong pin does not verify`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("1234", salt)
        assertFalse(PinHasher.verify("9999", salt, hash))
        assertFalse(PinHasher.verify("123", salt, hash))
    }

    @Test
    fun `salts differ so the same pin hashes differently`() {
        val saltA = PinHasher.newSalt()
        val saltB = PinHasher.newSalt()
        assertFalse(saltA == saltB)
        assertFalse(PinHasher.hash("1234", saltA) == PinHasher.hash("1234", saltB))
    }

    @Test
    fun `a tampered hash never verifies`() {
        val salt = PinHasher.newSalt()
        assertFalse(PinHasher.verify("1234", salt, "QUFB"))
        assertFalse(PinHasher.verify("1234", salt, ""))
        assertFalse(PinHasher.verify("1234", "", PinHasher.hash("1234", salt)))
    }
}
