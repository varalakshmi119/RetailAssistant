package com.retailassistant.core

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Network utility functions for handling retries and network operations
 */
object NetworkUtils {
    
    /**
     * Retry a network operation with exponential backoff
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
                
                // Don't retry on certain errors
                if (!ErrorHandler.isNetworkError(e) || ErrorHandler.isAuthError(e)) {
                    throw e
                }
                
                // Don't delay on the last attempt
                if (attempt < maxRetries - 1) {
                    // Add jitter to prevent thundering herd
                    val jitter = Random.nextLong(0, currentDelay / 4)
                    delay(currentDelay + jitter)
                    currentDelay = (currentDelay * backoffMultiplier).toLong().coerceAtMost(maxDelayMs)
                }
            }
        }
        
        throw lastException ?: Exception("Max retries exceeded")
    }
}