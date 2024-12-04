package no.nav.security

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.ktor.client.*
import java.net.URI

class NaisApi(httpClient: HttpClient) {
    private val baseUrl = "https://console.nav.cloud.nais.io/graphql"
    private val client = GraphQLKtorClient(
        url = URI(baseUrl).toURL(),
        httpClient = httpClient
    )

    suspend fun teamStats(): Set<NaisTeam> {
        return fetchTeamStats()
    }

    private tailrec suspend fun fetchTeamStats(
        teamCursor: String? = null,
        repoCursor: String? = null,
        teams: Set<NaisTeam> = emptySet()
    ): Set<NaisTeam> {
        val ghQuery = TeamStatsQuery(
            variables = TeamStatsQuery.Variables(
                teamsCursor = teamCursor,
                repoCursor = repoCursor
            )
        )
        val response: GraphQLClientResponse<TeamStatsQuery.Result> = client.execute(ghQuery)

        val result = response.data?.teams?.nodes?.map { team ->
            val teamRepos = team.repositories.nodes.map { repo -> repo.name }
            val newTeam = NaisTeam(
                naisTeam = team.slug,
                slsaCoverage = team.vulnerabilitySummary.coverage.toInt(),
                hasDeployedResources = (team.inventoryCounts.applications.total > 0 || team.inventoryCounts.jobs.total > 0),
                hasGithubRepositories = (team.repositories.nodes.isNotEmpty()),
                repositories = teams.find { it.naisTeam == team.slug }?.repositories?.plus(teamRepos) ?: teamRepos
            )
            // Fetch next page of repositories if available
            if(team.repositories.pageInfo.hasNextPage) {
                logger.info("Team ${team.slug} has more repositories, fetching next page with cursor ${team.repositories.pageInfo.endCursor}")
                return fetchTeamStats(
                    teamCursor = response.data?.teams?.pageInfo?.startCursor,
                    repoCursor = team.repositories.pageInfo.endCursor,
                    teams = teams.plus(newTeam)
                )
            }
            newTeam
        }?.toSet() ?: emptySet()

        if(response.errors?.isNotEmpty() == true) {
            throw RuntimeException("Error fetching team stats from NAIS API: ${response.errors.toString()}")
        }

        if(result.isEmpty()) {
            return teams
        }

        return if(response.data?.teams?.pageInfo?.hasNextPage == true) {
            fetchTeamStats(
                teamCursor = response.data?.teams?.pageInfo?.endCursor,
                repoCursor = null,
                teams = teams.plus(result)
            )
        } else {
            teams.plus(result)
        }
    }
}

data class NaisTeam(
    val naisTeam: String,
    val slsaCoverage: Int,
    val hasDeployedResources: Boolean,
    val hasGithubRepositories: Boolean,
    val repositories: List<String>
)