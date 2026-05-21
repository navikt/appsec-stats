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

}
