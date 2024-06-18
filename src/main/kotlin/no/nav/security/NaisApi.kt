package no.nav.security

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.ktor.client.*
import no.nav.security.inputs.TeamsFilter
import no.nav.security.inputs.TeamsFilterGitHub
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class NaisApi(private val httpClient: HttpClient) {
    private val baseUrl = "https://console.nav.cloud.nais.io/query"
    private val client = GraphQLKtorClient(
        url = URI(baseUrl).toURL(),
        httpClient = httpClient
    )

    suspend fun adminAndDeployInfoFor(repositories: List<GithubRepository>): List<IssueCountRecord> {
        var iterations = 0
        val result = repositories.map {
            iterations++
            if (iterations % 100 == 0) {
                logger.info("Fetched $iterations repo owners from NAIS API")
            }
            val naisInfo = fetchAdminAndDeployInfoFor(it.name)
            IssueCountRecord(
                owners = naisInfo.admins,
                lastPush = it.pushedAt,
                repositoryName = it.name,
                vulnerabilityAlertsEnabled = it.hasVulnerabilityAlertsEnabled,
                vulnerabilityCount = it.vulnerabilityAlerts,
                isArchived = it.isArchived,
                productArea = null,
                isDeployed = naisInfo.isDeployed,
                deployDate = naisInfo.deployDate
            )
        }

        return result
    }

    private data class NaisRepoInfo(
        val admins: List<String>,
        val isDeployed: Boolean,
        val isDeployedToProd: Boolean,
        val deployDate: LocalDateTime?
    )

    private suspend fun fetchAdminAndDeployInfoFor(repoName: String?): NaisRepoInfo {
        val repoFullName = "navikt/$repoName"
        val ghQuery = NaisTeamsFetchAdminsAndDeploysQuery(
            variables = NaisTeamsFetchAdminsAndDeploysQuery.Variables(
                filter = TeamsFilter(github = TeamsFilterGitHub(repoName = repoFullName, permissionName = "admin")),
                offset = 0,
                limit = 100,
                deployLimit = 100,
                deployOffset = 0
            )
        )
        val response: GraphQLClientResponse<NaisTeamsFetchAdminsAndDeploysQuery.Result> = client.execute(ghQuery)
        val teamsList = response.data?.teams?.nodes ?: emptyList()
        val isDeployed = teamsList.map { team -> team.deployments.nodes.isNotEmpty() }.any { it }
        val isDeployedToProd = teamsList.map { team -> team.deployments.nodes.any { it.env.contains("prod") } }.isNotEmpty()
        val owners = teamsList.map { it.slug.toString() }
        // Loop through teams, map created date from all deployments and fetch the latest one.
        val deployDate = teamsList.map { team ->
            team.deployments.nodes.map { it.created }
        }.flatten().maxOrNull()?.let { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }

        return NaisRepoInfo(
            admins = owners,
            isDeployedToProd = isDeployedToProd,
            isDeployed = isDeployed,
            deployDate = deployDate
        )

    }
}