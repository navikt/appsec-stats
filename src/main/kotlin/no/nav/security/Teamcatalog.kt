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

