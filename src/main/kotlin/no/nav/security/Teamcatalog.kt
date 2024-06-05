package no.nav.security

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class Teamcatalog(
    //val httpClient: HttpClient
) {
    private val baseUrl = "http://team-catalog-backend.org.svc.cluster.local"
    private val httpClient = tcHttpClient()

    suspend fun updateRecordsWithProductAreasForTeams(teams: List<IssueCountRecord>) {
        val activeProductAreas = httpClient.get { url("$baseUrl/productarea?status=ACTIVE") }
            .body<ProductAreaResponse>()

        // Fetch all teams in active product areas and return list of product areas
        val listOfProductAreasWithNaisTeams: List<TeamResponse> = activeProductAreas.content.map {
            httpClient.get { url("$baseUrl/productarea/${it.id}") }
                .body<TeamResponse>()
        }

        var foundTeams = 0
        // Iterate through teams and find the product area for each naisTeam
        teams.map { team ->
            // Find the product area for the team if the team has a naisTeam
            listOfProductAreasWithNaisTeams.find { productAreas ->
                productAreas.naisTeams.any { naisTeam -> team.owners.contains(naisTeam) }
            }?.let { result ->
                // Find the product area with the same id as the id from last find
                activeProductAreas.content.find { po ->
                    po.id == result.id
                }?.let { productArea ->
                    // Set the product area for the team
                    team.productArea = productArea.name
                    foundTeams++
                }
            }
        }
        logger.info("Found product area for $foundTeams teams")
    }

    @Serializable
    internal data class ProductAreaResponse(val content: List<ProductArea>)

    @Serializable
    internal data class ProductArea(val id: String, val name: String)

    @Serializable
    internal data class TeamResponse(val id: String, val name: String, val naisTeams: List<String>)
}

private fun tcHttpClient() = HttpClient(CIO) {
    expectSuccess = true
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 5)
        exponentialDelay()
    }
    install(ContentNegotiation) {
        json(json = Json {
            explicitNulls = false
            ignoreUnknownKeys = true
        })
    }

    defaultRequest {
        headers {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.UserAgent, "NAV IT McBotFace")
        }
    }
}