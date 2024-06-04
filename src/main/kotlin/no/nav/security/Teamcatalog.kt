package no.nav.security

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

class Teamcatalog(
    val httpClient: HttpClient
) {

    private val baseUrl = "https://teamkatalog-api.intern.nav.no"

    suspend fun updateRecordsWithProductAreasForTeams(teams: List<IssueCountRecord>) {
        val activeProductAreas = httpClient.get { url("$baseUrl/productarea?status=ACTIVE") }
            .body<ProductAreaResponse>()

        // Fetch all teams in active product areas and return list of product areas
        val listOfProductAreasWithNaisTeams: List<TeamResponse> = activeProductAreas.content.map {
            httpClient.get { url("$baseUrl/productArea/${it.id}") }
                .body<TeamResponse>()
        }

        var foundTeams = 0
        // Iterate through teams and find the product area for each naisTeam
        teams.map { team ->
            val productArea = listOfProductAreasWithNaisTeams.find {
                it.naisTeams.any { naisTeam -> team.owners.contains(naisTeam) }
            }
            team.productArea = productArea?.name?.also { foundTeams++ }
        }
        logger.info("Found product area for $foundTeams teams")
    }

    @Serializable
    private data class ProductAreaResponse(val content: List<ProductArea>)

    @Serializable
    private data class ProductArea(val id: String, val name: String)

    @Serializable
    private data class TeamResponse(val name: String, val naisTeams: List<String>)
}

