package no.nav.security

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.ktor.client.*
import no.nav.security.bigquery.BQNaisTeam
import no.nav.security.bigquery.BQRepoStat
import no.nav.security.inputs.TeamsFilter
import no.nav.security.inputs.TeamsFilterGitHub
import java.net.URI

class NaisApi(httpClient: HttpClient) {
    private val baseUrl = "https://console.nav.cloud.nais.io/query"
    private val client = GraphQLKtorClient(
        url = URI(baseUrl).toURL(),
        httpClient = httpClient
    )

    suspend fun adminsFor(repositories: List<GithubRepository>): List<BQRepoStat> {
        val result = repositories.map {
            BQRepoStat(
                owners = adminFor(it.name),
                lastPush = it.pushedAt,
                repositoryName = it.name,
                vulnerabilityAlertsEnabled = it.hasVulnerabilityAlertsEnabled,
                vulnerabilityCount = it.vulnerabilityAlerts,
                isArchived = it.isArchived,
                productArea = null
            )
        }

        return result
    }

    suspend fun teamStats(): List<BQNaisTeam> {
        return fetchTeamStats()
    }

    private suspend fun adminFor(repoName: String?): List<String> {
        val repoFullName = "navikt/$repoName"
        val ghQuery = AdminsQuery(
            variables = AdminsQuery.Variables(
                filter = TeamsFilter(github = TeamsFilterGitHub(repoName = repoFullName, permissionName = "admin")),
            )
        )
        val response: GraphQLClientResponse<AdminsQuery.Result> = client.execute(ghQuery)

        return response.data?.teams?.nodes?.map { it.slug.toString() } ?: emptyList()
    }

    private tailrec suspend fun fetchTeamStats(offset: Int = 0, teams: List<BQNaisTeam> = emptyList()): List<BQNaisTeam> {
        val ghQuery = TeamStatsQuery(
            variables = TeamStatsQuery.Variables(
                offset = offset
            )
        )
        val response: GraphQLClientResponse<TeamStatsQuery.Result> = client.execute(ghQuery)

        val result = response.data?.teams?.nodes?.map {
            BQNaisTeam(
                naisTeam = it.slug,
                slsaCoverage = it.vulnerabilitiesSummary.coverage.toInt(),
                hasDeployedResources = (it.status.apps.total > 0 || it.status.jobs.total > 0),
                hasGithubRepositories = it.githubRepositories.nodes.isNotEmpty()
            )
        }

        if(response.errors?.isNotEmpty() == true) {
            throw RuntimeException("Error fetching team stats from NAIS API: ${response.errors.toString()}")
        }

        if(result?.isEmpty() == true) {
            return teams
        }

        return if(response.data?.teams?.pageInfo?.hasNextPage == true) {
            fetchTeamStats(offset + 100, result?.plus(teams) ?: emptyList())
        } else {
            result?.plus(teams) ?: emptyList()
        }
    }
}