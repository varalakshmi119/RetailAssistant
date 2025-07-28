package com.retailassistant.core

import kotlinx.coroutines.delay
import kotlin.random.Random

object NetworkUtils {
    /**
     * Retries a suspend function with exponential backoff and jitter.
     * Skips retries for non-recoverable errors like authentication failures.
     */
    suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        maxDelayMs: Long = 10000,
        backoffMultiplier: Double = 2.0,
        operation: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e

                if (!ErrorHandler.isNetworkError(e) || ErrorHandler.isAuthError(e)) {
                    throw e // Don't retry on non-network or auth errors
                }

                if (attempt < maxRetries - 1) {
                    val jitter = Random.nextLong(0, currentDelay / 4)
                    delay(currentDelay + jitter)
                    currentDelay = (currentDelay * backoffMultiplier).toLong().coerceAtMost(maxDelayMs)
                }
            }
        }
        throw lastException ?: Exception("Max retries exceeded without a specific exception.")
    }
}
