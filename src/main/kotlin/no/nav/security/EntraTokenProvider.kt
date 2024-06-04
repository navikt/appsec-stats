package no.nav.security

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.net.URI
import java.net.URL

class EntraTokenProvider(
    private val scope: String,
    private val client: HttpClient
) {
    private var config: AzureConfig = AzureConfig(
        tokenEndpoint = URI((requiredFromEnv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"))).toURL(),
        clientId = requiredFromEnv("AZURE_APP_CLIENT_ID"),
        clientSecret = requiredFromEnv("AZURE_APP_CLIENT_SECRET"),
        issuer = requiredFromEnv("AZURE_OPENID_CONFIG_ISSUER"),
        jwks = URI(requiredFromEnv("AZURE_OPENID_CONFIG_JWKS_URI")).toURL()
    )

    suspend fun getClientCredentialToken() =
        getAccessToken("client_id=${config.clientId}&client_secret=${config.clientSecret}&scope=$scope&grant_type=client_credentials")

    private suspend fun getAccessToken(body: String): String {
            return client.post(config.tokenEndpoint) {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(body)
            }.body<Token>().access_token
    }

    private companion object {
        private data class AzureConfig(
            val tokenEndpoint: URL,
            val clientId: String,
            val clientSecret: String,
            val jwks: URL,
            val issuer: String
        )

        private data class Token(val expires_in: Long, val access_token: String)
    }
}