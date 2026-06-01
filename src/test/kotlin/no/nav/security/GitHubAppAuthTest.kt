package no.nav.security

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringWriter
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Instant
import java.time.format.DateTimeFormatter

private val testKeyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

private fun testPrivateKeyPem(): String {
    val writer = StringWriter()
    JcaPEMWriter(writer).use { it.writeObject(testKeyPair) }
    return writer.toString()
}

class GitHubAppAuthTest {
    private val futureExpiry = DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(3600))
    private val expiredExpiry = DateTimeFormatter.ISO_INSTANT.format(Instant.now().minusSeconds(3600))
    private val almostExpiredExpiry = DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(60))

    private fun mockClient(vararg tokens: Pair<String, String>): Pair<HttpClient, () -> Int> {
        var callCount = 0
        val responses = tokens.toList()
        val client =
            HttpClient(
                MockEngine { _ ->
                    val (token, expiry) = responses[callCount++]
                    respond(
                        content = ByteReadChannel("""{"token":"$token","expires_at":"$expiry"}"""),
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return client to { callCount }
    }

    private fun auth(client: HttpClient) =
        GitHubAppAuth(
            appId = "app-id",
            privateKeyContent = testPrivateKeyPem(),
            installationId = "install-id",
            httpClient = client,
        )

    @Test
    fun `returns cached token when still valid`() =
        runBlocking {
            val (client, callCount) = mockClient("token-1" to futureExpiry, "token-2" to futureExpiry)
            val appAuth = auth(client)

            val first = appAuth.getInstallationToken()
            val second = appAuth.getInstallationToken()

            assertEquals("token-1", first)
            assertEquals("token-1", second)
            assertEquals(1, callCount())
        }

    @Test
    fun `refreshes token when expired`() =
        runBlocking {
            val (client, callCount) = mockClient("token-1" to expiredExpiry, "token-2" to futureExpiry)
            val appAuth = auth(client)

            val first = appAuth.getInstallationToken()
            val second = appAuth.getInstallationToken()

            assertEquals("token-1", first)
            assertEquals("token-2", second)
            assertEquals(2, callCount())
        }

    @Test
    fun `refreshes token when within 5 minute buffer`() =
        runBlocking {
            val (client, callCount) = mockClient("token-1" to almostExpiredExpiry, "token-2" to futureExpiry)
            val appAuth = auth(client)

            val first = appAuth.getInstallationToken()
            val second = appAuth.getInstallationToken()

            assertEquals("token-1", first)
            assertEquals("token-2", second)
            assertEquals(2, callCount())
        }
}
