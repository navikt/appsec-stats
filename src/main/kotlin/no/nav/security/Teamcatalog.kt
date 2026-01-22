package no.nav.security

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable
import no.nav.security.bigquery.BQRepoStat

open class Teamcatalog(
    val httpClient: HttpClient
) {
    private val baseUrl = "http://team-catalog-backend.org.svc.cluster.local"

    open suspend fun updateRecordsWithProductAreasForTeams(teams: List<BQRepoStat>) {
        val activeProductAreas = httpClient.get { url("$baseUrl/productarea?status=ACTIVE") }
            .body<ProductAreaResponse>()

        // Fetch all teams in active product areas
        val activeTeamsInProductAreas: List<TeamResponse> = activeProductAreas.content.map {
            httpClient.get { url("$baseUrl/team?productAreaId=${it.id}&status=ACTIVE") }
                .body<TeamResponse>()
        }

        var foundPoForRepos = 0
        // For each IssueCountRecord, iterate through the list of owners
        // Find matching naisTeam in the list teams in of product areas
        // Then we find the product area for that team and update the record
        teams.forEach { record ->
            record.owners.forEach { owner ->
                activeTeamsInProductAreas.forEach { teamResponse ->
                    teamResponse.content.forEach { team ->
                        if (team.naisTeams.contains(owner)) {
                            // Update the record with the product area, overwriting any previous value
                            record.productArea = activeProductAreas.content.find { it.id == team.productAreaId }?.name
                            foundPoForRepos++
                        }
                    }
                }
            }
        }

        logger.info("Found product area for $foundPoForRepos repos")
    }

    @Serializable
    internal data class ProductAreaResponse(val content: List<ProductArea>)

    @Serializable
    internal data class ProductArea(val id: String, val name: String)

    @Serializable
    internal data class TeamResponse(val content: List<TeamCatalogTeam>)

    @Serializable
    internal data class TeamCatalogTeam(
        val productAreaId: String,
        val name: String,
        val naisTeams: List<String> = emptyList()
    )
}
