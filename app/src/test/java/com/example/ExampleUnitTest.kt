package com.example

import org.junit.Assert.*
import org.junit.Test
import java.util.regex.Pattern

class ExampleUnitTest {
    private val keyPattern = Pattern.compile("^atp-key-user-[0-9]{6}$")

    @Test
    fun testValidAccessKeyFormat() {
        val validKey = "atp-key-user-123456"
        assertTrue(keyPattern.matcher(validKey).matches())
    }

    @Test
    fun testInvalidAccessKeyFormats() {
        assertFalse(keyPattern.matcher("atp-key-user-12345").matches()) // 5 digits
        assertFalse(keyPattern.matcher("atp-key-user-1234567").matches()) // 7 digits
        assertFalse(keyPattern.matcher("atp-key-user-abcdef").matches()) // non-numeric
        assertFalse(keyPattern.matcher("vip-key-user-123456").matches()) // invalid prefix
        assertFalse(keyPattern.matcher("atp-key-admin-123456").matches()) // admin key not allowed
    }

    @Test
    fun test24HourExpirationDuration() {
        val now = 1000000L
        val expectedExpiresAt = now + (24 * 60 * 60 * 1000L)
        assertEquals(86400000L, expectedExpiresAt - now)
    }
}
