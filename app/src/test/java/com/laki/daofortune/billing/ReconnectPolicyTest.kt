package com.laki.daofortune.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reconnect schedule is the part of the billing wrapper that can be tested
 * without a device, so it is tested here.
 */
class ReconnectPolicyTest {

    @Test
    fun `the first retry waits two seconds`() {
        assertEquals(2_000L, ReconnectPolicy.delayMs(1))
    }

    @Test
    fun `the wait doubles with every attempt`() {
        assertEquals(2_000L, ReconnectPolicy.delayMs(1))
        assertEquals(4_000L, ReconnectPolicy.delayMs(2))
        assertEquals(8_000L, ReconnectPolicy.delayMs(3))
        assertEquals(16_000L, ReconnectPolicy.delayMs(4))
    }

    @Test
    fun `the wait is capped`() {
        assertEquals(ReconnectPolicy.MAX_DELAY_MS, ReconnectPolicy.delayMs(5))
        assertEquals(ReconnectPolicy.MAX_DELAY_MS, ReconnectPolicy.delayMs(50))
    }

    @Test
    fun `a nonsense attempt number is treated as the first`() {
        assertEquals(ReconnectPolicy.BASE_DELAY_MS, ReconnectPolicy.delayMs(0))
        assertEquals(ReconnectPolicy.BASE_DELAY_MS, ReconnectPolicy.delayMs(-3))
    }

    @Test
    fun `retries stop after the last attempt`() {
        assertTrue(ReconnectPolicy.shouldRetry(1))
        assertTrue(ReconnectPolicy.shouldRetry(ReconnectPolicy.MAX_ATTEMPTS))
        assertFalse(ReconnectPolicy.shouldRetry(ReconnectPolicy.MAX_ATTEMPTS + 1))
        assertFalse(ReconnectPolicy.shouldRetry(0))
    }

    @Test
    fun `the whole schedule stays inside a few minutes`() {
        var total = 0L
        for (attempt in 1..ReconnectPolicy.MAX_ATTEMPTS) {
            total += ReconnectPolicy.delayMs(attempt)
        }
        assertTrue("total wait was $total ms", total < 180_000L)
    }
}
