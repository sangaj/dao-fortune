package com.laki.daofortune.billing

/**
 * The retry schedule for a lost Play Billing connection.
 *
 * Play Billing drops its connection for ordinary reasons — a flaky mobile
 * connection, the screen going off, Play Store being updated in the background —
 * and the documented behaviour is to call `startConnection` again. Without that,
 * the paywall sits in "failed" until the reader kills the app.
 *
 * This is deliberately **plain arithmetic with no Android or Play types**, so it
 * can be unit tested on the JVM like the rest of the engine.
 */
object ReconnectPolicy {

    /** The first retry waits this long. */
    const val BASE_DELAY_MS = 2_000L

    /** No retry waits longer than this. */
    const val MAX_DELAY_MS = 30_000L

    /**
     * How many consecutive attempts are made before the app stops and asks the
     * reader to try again by hand. Six attempts cover roughly two and a half
     * minutes of bad connectivity, which is longer than most people's patience.
     */
    const val MAX_ATTEMPTS = 6

    /**
     * The wait before attempt number [attempt] (1-based).
     *
     * The delay doubles each time — 2s, 4s, 8s, 16s — and is then held at
     * [MAX_DELAY_MS]. Anything at or below zero is treated as the first attempt.
     */
    fun delayMs(attempt: Int): Long {
        if (attempt <= 1) return BASE_DELAY_MS
        val shift = (attempt - 1).coerceAtMost(16)
        val raw = BASE_DELAY_MS shl shift
        return if (raw <= 0L) MAX_DELAY_MS else minOf(raw, MAX_DELAY_MS)
    }

    /** Whether attempt number [attempt] (1-based) should be made at all. */
    fun shouldRetry(attempt: Int): Boolean = attempt in 1..MAX_ATTEMPTS
}
