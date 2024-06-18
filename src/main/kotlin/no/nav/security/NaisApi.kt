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

    suspend fun adminsFor(repositories: List<GithubRepository>): List<IssueCountRecord> {
        var iterations = 0
        val result = repositories.map {
            iterations++
            if (iterations % 100 == 0) {
                logger.info("Fetched $iterations repo owners from NAIS API")
            }
            IssueCountRecord(
                owners = adminsFor(it.name),
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

    private suspend fun adminsFor(repoName: String?): List<String> {
        val repoFullName = "navikt/$repoName"
        val ghQuery = NaisTeamsFetchAdminsForRepoQuery(
            variables = NaisTeamsFetchAdminsForRepoQuery.Variables(
                filter = TeamsFilter(github = TeamsFilterGitHub(repoFullName, "admin")),
                offset = 0,
                limit = 100
            )
        )
        val response: GraphQLClientResponse<NaisTeamsFetchAdminsForRepoQuery.Result> = client.execute(ghQuery)
        return response.data?.teams?.nodes?.map { it.slug } ?: emptyList()
    }

    suspend fun updateRecordsWithDeploymentStatus(repositories: List<IssueCountRecord>) {
        var deployedApps = 0
        val listOfDeployments = createListOfRepoDeploymentStatus()
        repositories.forEach { record ->
            listOfDeployments.find { deployment -> deployment.repository == record.repositoryName }.let {
                record.isDeployed = true
                record.deployDate = it?.created
                deployedApps++
            }
        }
        logger.info("Found $deployedApps deployed apps")
    }

    private suspend fun createListOfRepoDeploymentStatus(): List<RepoDeploymentStatus> {
        val deployments = mutableListOf<RepoDeploymentStatus>()
        var offset = 0
        do {
            logger.info("looking for deployments at offset $offset")
            var deployOffset = 0
            val ghQuery = NaisTeamsDeploymentsQuery.Variables(
                teamOffset = offset,
                teamLimit = 100,
                deployOffset = deployOffset,
                deployLimit = 100
            )
            var response: GraphQLClientResponse<NaisTeamsDeploymentsQuery.Result> =
                client.execute(NaisTeamsDeploymentsQuery(ghQuery))
            response.data?.teams?.nodes?.map { team ->
                // Check if current team has more than 100 deployments, if so, iterate through all deployments
                if(team.deployments.pageInfo.hasNextPage) {
                    do {
                        response.data?.teams?.nodes?.map { teamLoop ->
                            teamLoop.deployments.nodes.map { deployment ->
                                val created = Instant.parse(deployment.created).atZone(ZoneId.systemDefault()).toLocalDateTime()
                                deployments.add(RepoDeploymentStatus(deployment.repository, created))
                            }
                        }
                        deployOffset += 100
                        response = client.execute(NaisTeamsDeploymentsQuery(ghQuery))
                    } while (response.data?.teams?.pageInfo?.hasNextPage == true)
                // If team has less than 100 deployments, add them to the list
                } else {
                    team.deployments.nodes.map { deployment ->
                        val created = Instant.parse(deployment.created).atZone(ZoneId.systemDefault()).toLocalDateTime()
                        deployments.add(RepoDeploymentStatus(deployment.repository.substringAfter("/"), created))
                    }
                }
            }
            offset += 100
        } while (response.data?.teams?.pageInfo?.hasNextPage == true)
        logger.info("NAIS returned ${deployments.size} deployments")
        return deployments.distinctBy { it.repository }
    }
}

private data class RepoDeploymentStatus(
    val repository: String,
    val created: LocalDateTime
)