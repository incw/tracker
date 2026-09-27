package dev.smolyakoff.tracker.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import kotlin.math.min

class RateLimiter(
    private val capacity: Double = 8.0,
    private val refillRatePerSecond: Double = 8.0
) {
    private val logger = LoggerFactory.getLogger(RateLimiter::class.java)
    private val mutex = Mutex()
    private var tokens: Double = capacity
    private var lastRefillTimestamp: Long = System.currentTimeMillis()

    suspend fun acquire(tokensRequested: Double = 1.0) {
        var waitTimeMs = 0L

        mutex.withLock {
            val now = System.currentTimeMillis()
            val elapsedSeconds = (now - lastRefillTimestamp) / 1000.0
            tokens = min(capacity, tokens + elapsedSeconds * refillRatePerSecond)
            lastRefillTimestamp = now

            if (tokens < tokensRequested) {
                val neededTokens = tokensRequested - tokens
                waitTimeMs = (neededTokens / refillRatePerSecond * 1000.0).toLong() + 10L
                tokens = 0.0
                lastRefillTimestamp = now + waitTimeMs
            } else {
                tokens -= tokensRequested
            }
        }

        if (waitTimeMs > 0) {
            logger.debug("Rate limit throttling: delaying for {} ms", waitTimeMs)
            delay(waitTimeMs)
        }
    }
}
