package no.nav.security

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.ktor.client.*
import no.nav.security.inputs.TeamsFilter
import no.nav.security.inputs.TeamsFilterGitHub
import no.nav.security.naisteamsfetchadminsanddeploysquery.Deployment
import no.nav.security.naisteamsfetchadminsanddeploysquery.Team
import java.net.URI
import java.time.Instant
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
                logger.info("Fetched info about $iterations repos from NAIS API.")
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
        val deployDate: String?
    )

    private suspend fun fetchAdminAndDeployInfoFor(repoName: String?): NaisRepoInfo {
        val repoFullName = "navikt/$repoName"
        var deployOffset = 0
        val ghQuery = NaisTeamsFetchAdminsAndDeploysQuery(
            variables = NaisTeamsFetchAdminsAndDeploysQuery.Variables(
                filter = TeamsFilter(github = TeamsFilterGitHub(repoName = repoFullName, permissionName = "admin")),
                deployLimit = 100,
                deployOffset = deployOffset
            )
        )
        val response: GraphQLClientResponse<NaisTeamsFetchAdminsAndDeploysQuery.Result> = client.execute(ghQuery)

        val teamsList: List<Team> = response.data?.teams?.nodes ?: emptyList()
        val owners: List<String> = teamsList.map { it.slug }

        val listOfDeploymentForRepo: MutableList<Deployment> = mutableListOf()
        var isDeployedToProd = false
        var deployDateTime: String? = null

        // Loop through deployments for each team to find deployment for current repository
        teamsList.forEach { team ->
            deployOffset = 0
            do {
                // Eww: reuse response for first iteration
                val teamResponse = if(deployOffset == 0) response else {
                    client.execute(NaisTeamsFetchAdminsAndDeploysQuery(
                        variables = NaisTeamsFetchAdminsAndDeploysQuery.Variables(
                            filter = TeamsFilter(github = TeamsFilterGitHub(repoName = repoFullName, permissionName = "admin")),
                            deployLimit = 100,
                            deployOffset = deployOffset
                        )
                    ))
                }
                teamResponse.data?.teams?.nodes?.map {
                    it.deployments.nodes.filter { deploy -> deploy.repository.contains(repoFullName) }
                }?.flatten()?.let {
                    listOfDeploymentForRepo.addAll(it)
                }

                isDeployedToProd = listOfDeploymentForRepo.any { it.env.contains("prod") }
                deployDateTime = listOfDeploymentForRepo.maxByOrNull { it.created }
                    ?.let { Instant.parse(it.created).atZone(ZoneId.systemDefault()).toLocalDateTime().toString() }
                deployOffset += 100
                val hasNextPage = teamResponse.data?.teams?.nodes?.find { it.slug == team.slug }?.deployments?.pageInfo?.hasNextPage == true
            } while (hasNextPage)
        }

        return NaisRepoInfo(
            admins = owners,
            isDeployedToProd = isDeployedToProd,
            isDeployed = listOfDeploymentForRepo.isNotEmpty(),
            deployDate = deployDateTime
        )
    }
}