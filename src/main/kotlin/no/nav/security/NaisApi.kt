package no.nav.security

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.ktor.client.*
import no.nav.security.inputs.TeamsFilter
import no.nav.security.inputs.TeamsFilterGitHub
import java.net.URI

class NaisApi(httpClient: HttpClient) {
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
                logger.info("Fetched info about $iterations repos from NAIS API.")
            }
            IssueCountRecord(
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

    private suspend fun adminFor(repoName: String?): List<String> {
        val repoFullName = "navikt/$repoName"
        val ghQuery = NaisTeamsFetchAdminsQuery(
            variables = NaisTeamsFetchAdminsQuery.Variables(
                filter = TeamsFilter(github = TeamsFilterGitHub(repoName = repoFullName, permissionName = "admin")),
            )
        )
        val response: GraphQLClientResponse<NaisTeamsFetchAdminsQuery.Result> = client.execute(ghQuery)

        return response.data?.teams?.nodes?.map { it.slug.toString() } ?: emptyList()
    }
}