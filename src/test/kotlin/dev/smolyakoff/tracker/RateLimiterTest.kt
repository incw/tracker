package dev.smolyakoff.tracker

import dev.smolyakoff.tracker.api.RateLimiter
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

class RateLimiterTest {

    @Test
    fun testRateLimiterThrottling() = runBlocking {
        // Capacity 2, refill rate 2 tokens per second
        val limiter = RateLimiter(capacity = 2.0, refillRatePerSecond = 2.0)

        // First 2 acquires should be instant
        val initialTime = measureTimeMillis {
            limiter.acquire(1.0)
            limiter.acquire(1.0)
        }
        assertTrue(initialTime < 100, "Initial acquires should be fast, took ${initialTime}ms")

        // 3rd acquire must wait for refill (~500ms)
        val throttledTime = measureTimeMillis {
            limiter.acquire(1.0)
        }
        assertTrue(throttledTime >= 400, "Throttled acquire should take at least 400ms, took ${throttledTime}ms")
    }
}
