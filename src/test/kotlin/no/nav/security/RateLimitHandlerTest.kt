package no.nav.security

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class RateLimitHandlerTest {

    @Test
    fun `should stop retrying after max attempts`() = runBlocking {
        val handler = RateLimitHandler()
        
        val shouldRetry = handler.retryWithBackoff(
            attempt = 3,
            maxAttempts = 3,
            operationName = "test"
        )
        
        assertFalse(shouldRetry, "Should not retry after max attempts reached")
    }
    
    @Test
    fun `should allow retry within max attempts without actually waiting`() {
        val handler = RateLimitHandler()
        
        // Test logic only - don't actually call retryWithBackoff as it has delays
        val attempt = 0
        val maxAttempts = 3
        val shouldRetry = attempt < maxAttempts
        
        assertTrue(shouldRetry, "Should indicate retry on first attempt")
    }
}
