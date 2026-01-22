package no.nav.security

import kotlinx.coroutines.delay
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Handles GitHub API rate limiting according to best practices.
 * 
 * GitHub GraphQL API includes rate limit information in the response body:
 * - rateLimit.remaining: Points remaining in current window
 * - rateLimit.limit: Total points available per hour
 * - rateLimit.resetAt: ISO 8601 timestamp when the rate limit resets
 * 
 * Best practices:
 * 1. Monitor rate limit status in every response
 * 2. Pause requests when remaining is low
 * 3. Wait until resetAt time when exhausted
 * 4. Add buffer time to prevent race conditions
 */
class RateLimitHandler(
    private val lowThreshold: Int = 500,  // Pause when below this
    private val criticalThreshold: Int = 100  // Minimum before forcing wait
) {
    
    /**
     * Checks rate limit status and delays if necessary.
     * Returns true if should retry the request, false if should throw error.
     */
    suspend fun checkAndWait(
        remaining: Int,
        limit: Int,
        resetAt: String,
        operationName: String
    ): Boolean {
        val resetTime = parseResetTime(resetAt)
        val currentTime = Instant.now()
        val secondsUntilReset = (resetTime.epochSecond - currentTime.epochSecond).coerceAtLeast(0)
        
        val percentageRemaining = (remaining.toDouble() / limit.toDouble()) * 100
        
        logger.info("Rate limit status for $operationName: $remaining/$limit remaining (${percentageRemaining.toInt()}%), resets in ${secondsUntilReset}s")
        
        when {
            // Critical: Very low remaining, must wait for reset
            remaining < criticalThreshold -> {
                val waitTimeMs = calculateWaitTime(secondsUntilReset)
                logger.warn("Rate limit critical for $operationName: $remaining/$limit remaining. Waiting ${waitTimeMs / 1000}s until reset.")
                delay(waitTimeMs)
                return true // Retry after waiting
            }
            
            // Low: Implement backoff to avoid hitting critical threshold
            remaining < lowThreshold -> {
                val backoffMs = calculateBackoff(remaining, limit, secondsUntilReset)
                logger.info("Rate limit low for $operationName: $remaining/$limit remaining. Applying ${backoffMs / 1000}s backoff.")
                delay(backoffMs)
                return false // Continue but with delay
            }
            
            // Healthy: No action needed
            else -> {
                return false
            }
        }
    }
    
    /**
     * Calculates wait time until rate limit reset with buffer and jitter.
     */
    private fun calculateWaitTime(secondsUntilReset: Long): Long {
        val bufferSeconds = 5L // Add buffer to avoid race conditions
        val baseWaitMs = (secondsUntilReset + bufferSeconds) * 1000L
        val jitterMs = Random.nextLong(1000L, 3000L) // 1-3 seconds jitter
        return baseWaitMs + jitterMs
    }
    
    /**
     * Calculates exponential backoff based on how close we are to exhausting rate limit.
     * More aggressive backoff as remaining points decrease.
     */
    private fun calculateBackoff(remaining: Int, limit: Int, secondsUntilReset: Long): Long {
        val percentageRemaining = remaining.toDouble() / limit.toDouble()
        
        // Base delay increases exponentially as we approach the threshold
        // When at 50% capacity: ~2s, at 25%: ~5s, at 10%: ~10s
        val baseDelayMs = when {
            percentageRemaining > 0.5 -> 2000L
            percentageRemaining > 0.25 -> 5000L
            percentageRemaining > 0.10 -> 10000L
            else -> 20000L
        }
        
        // Add jitter to prevent thundering herd
        val jitterMs = Random.nextLong(500L, 1500L)
        
        return min(baseDelayMs + jitterMs, 30000L) // Cap at 30 seconds
    }
    
    /**
     * Parses ISO 8601 timestamp from GitHub API.
     */
    private fun parseResetTime(resetAt: String): Instant {
        return try {
            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(resetAt))
        } catch (e: Exception) {
            logger.error("Failed to parse resetAt timestamp: $resetAt", e)
            // Default to 1 hour from now if parsing fails
            Instant.now().plusSeconds(3600)
        }
    }
    
    /**
     * Handles rate limit with retry logic using exponential backoff.
     * Used when a request fails due to rate limiting.
     */
    suspend fun retryWithBackoff(
        attempt: Int,
        maxAttempts: Int = 3,
        operationName: String
    ): Boolean {
        if (attempt >= maxAttempts) {
            logger.error("Max retry attempts ($maxAttempts) reached for $operationName")
            return false
        }
        
        val baseDelayMs = 60000L // 1 minute base
        val exponentialDelay = (baseDelayMs * 2.0.pow(attempt.toDouble())).toLong()
        val cappedDelay = min(exponentialDelay, 600000L) // Cap at 10 minutes
        val jitter = Random.nextLong(1000L, 5000L)
        val totalDelay = cappedDelay + jitter
        
        logger.info("Retrying $operationName (attempt ${attempt + 1}/$maxAttempts) after ${totalDelay / 1000}s")
        delay(totalDelay)
        return true
    }
}
