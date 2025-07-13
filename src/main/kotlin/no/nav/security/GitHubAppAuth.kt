package no.nav.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.io.StringReader
import java.security.interfaces.RSAPrivateKey
import java.time.Instant
import java.util.*

class GitHubAppAuth(
    private val appId: String,
    private val privateKeyContent: String,
    private val installationId: String,
    private val httpClient: HttpClient
) {
    private val privateKey: RSAPrivateKey = loadPrivateKey(privateKeyContent)

    suspend fun getInstallationToken(): String {
        val jwt = JWT.create()
            .withIssuer(appId)
            .withIssuedAt(Date.from(Instant.now().minusSeconds(60)))
            .withExpiresAt(Date.from(Instant.now().plusSeconds(600)))
            .sign(Algorithm.RSA256(null, privateKey))

        val response = httpClient.post("https://api.github.com/app/installations/$installationId/access_tokens") {
            headers {
                append(HttpHeaders.Accept, "application/vnd.github+json")
                append(HttpHeaders.Authorization, "Bearer $jwt")
                append("X-GitHub-Api-Version", "2022-11-28")
            }
        }.body<InstallationTokenResponse>()

        logger.info("Fetched installation token for GitHub App with ID $appId and installation ID $installationId, expires at: (${response.expires_at})")

        return response.token
    }

    private fun loadPrivateKey(pemContent: String): RSAPrivateKey {
        val pemParser = PEMParser(StringReader(pemContent))
        val keyPair = pemParser.readObject() as PEMKeyPair
        return JcaPEMKeyConverter().getKeyPair(keyPair).private as RSAPrivateKey
    }

    @Serializable
    private data class InstallationTokenResponse(val token: String, val expires_at: String)
}

