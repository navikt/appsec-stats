package no.nav.security

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.ktor.client.*
import java.net.URI

class GitHub(
    httpClient: HttpClient,
    baseUrl: String = "https://api.github.com/graphql"
) {

    private val records = mutableListOf<IssueCountRecord>()
    private val client = GraphQLKtorClient(
        url = URI(baseUrl).toURL(),
        httpClient = httpClient
    )

    suspend fun fetchOrgRepositories(repositoryCursor: String? = null, repositoryListe: List<GithubRepository> = emptyList()): List<GithubRepository> {
        val ghQuery = FetchGithubRepositoriesQuery(
            variables = FetchGithubRepositoriesQuery.Variables(
                orgName = "navikt",
                repoEndCursor = repositoryCursor,
            )
        )
        val response: GraphQLClientResponse<FetchGithubRepositoriesQuery.Result> = client.execute(ghQuery)
        response.errors?.let {
            logger.error("Error fetching repository from GitHub: $it")
            throw RuntimeException("Error fetching repository from GitHub: $it")
        }
        if(response.data?.rateLimit?.remaining!! < 100) {
            logger.error("Rate limit is low: ${response.data?.rateLimit?.remaining} (fetchOrgRepositories)")
            throw RuntimeException("Rate limit is low (<100)")
        }

        val oppdatertRepositoryliste = repositoryListe.plus(response.data?.organization?.repositories?.nodes?.mapNotNull {
            if (it != null) GithubRepository(it.id, it.name, it.isArchived, it.pushedAt, it.hasVulnerabilityAlertsEnabled, it.vulnerabilityAlerts?.totalCount ?: 0) else null
        } ?: emptyList())

        val repositoryPageInfo = response.data?.organization?.repositories?.pageInfo
        val nextRepositoryPage = repositoryPageInfo?.endCursor.takeIf { repositoryPageInfo?.hasNextPage ?: false }

//        if (nextRepositoryPage != null) {
//            return fetchTeams(teamCursor = nextRepositoryPage, oppdatertRepositoryliste)
//        }
        return oppdatertRepositoryliste
    }
}

