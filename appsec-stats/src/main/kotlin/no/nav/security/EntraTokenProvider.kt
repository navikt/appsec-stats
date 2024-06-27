package no.nav.security

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

class EntraTokenProvider(
    private val scope: String,
    private val client: HttpClient
) {
    private var config: AzureConfig = AzureConfig(
        tokenEndpoint = requiredFromEnv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
        clientId = requiredFromEnv("AZURE_APP_CLIENT_ID"),
        clientSecret = requiredFromEnv("AZURE_APP_CLIENT_SECRET"),
        issuer = requiredFromEnv("AZURE_OPENID_CONFIG_ISSUER")
    )

    suspend fun getClientCredentialToken() =
        getAccessToken("client_id=${config.clientId}&client_secret=${config.clientSecret}&scope=$scope&grant_type=client_credentials")

    private suspend fun getAccessToken(body: String): String {
        val response = client.post(config.tokenEndpoint) {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(body)
        }
        if(response.status.isSuccess()) {
            return response.body<Token>().access_token
        } else {
            throw RuntimeException("Failed to get access token: ${response.status.value}")
        }
    }

    private companion object {
        @Serializable
        private data class AzureConfig(
            val tokenEndpoint: String,
            val clientId: String,
            val clientSecret: String,
            val issuer: String
        )

        @Serializable
        private data class Token(val expires_in: Long, val access_token: String)
    }
}