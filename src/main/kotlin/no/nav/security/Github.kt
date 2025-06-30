package no.nav.security

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import java.net.URI

class GitHub(
    private val httpClient: HttpClient,
    baseUrl: String = "https://api.github.com/graphql"
) {

    private val graphQlClient = GraphQLKtorClient(
        url = URI(baseUrl).toURL(),
        httpClient = httpClient
    )

    suspend fun fetchOrgRepositories(
        repositoryCursor: String? = null,
        repositoryListe: List<GithubRepository> = emptyList()
    ): List<GithubRepository> {
        val ghQuery = FetchGithubRepositoriesQuery(
            variables = FetchGithubRepositoriesQuery.Variables(
                orgName = "navikt",
                repoEndCursor = repositoryCursor,
            )
        )
        val response: GraphQLClientResponse<FetchGithubRepositoriesQuery.Result> = graphQlClient.execute(ghQuery)
        response.errors?.let {
            logger.error("Error fetching repository from GitHub: $it")
            throw RuntimeException("Error fetching repository from GitHub: $it")
        }
        if (response.data?.rateLimit?.remaining!! < 100) {
            logger.error("Rate limit is low: ${response.data?.rateLimit?.remaining} (fetchOrgRepositories)")
            throw RuntimeException("Rate limit is low (<100)")
        }

        val oppdatertRepositoryliste =
            repositoryListe.plus(response.data?.organization?.repositories?.nodes?.mapNotNull {
                if (it != null) GithubRepository(
                    it.name,
                    it.isArchived,
                    it.pushedAt,
                    it.hasVulnerabilityAlertsEnabled,
                    it.vulnerabilityAlerts?.totalCount ?: 0
                ) else null
            } ?: emptyList())

        val repositoryPageInfo = response.data?.organization?.repositories?.pageInfo
        val nextRepositoryPage = repositoryPageInfo?.endCursor.takeIf { repositoryPageInfo?.hasNextPage ?: false }

        if (nextRepositoryPage != null) {
            return fetchOrgRepositories(repositoryCursor = nextRepositoryPage, oppdatertRepositoryliste)
        }
        return oppdatertRepositoryliste
    }
}

fun List<GithubRepository>.fetchRepositoryAdmins(httpClient: HttpClient, concurrencyLevel: Int = 10): List<GithubRepository> {
    val coroutineScope = CoroutineScope(Dispatchers.IO)

    return chunked(100) // Process repositories in chunks of 100
        .flatMap { chunk ->
            chunk.map { repo ->
                coroutineScope.async {
                    try {
                        val response = httpClient
                            .get("https://api.github.com/repos/navikt/${repo.name}/teams") {
                                headers {
                                    append(HttpHeaders.Accept, "application/vnd.github+json")
                                    append("X-GitHub-Api-Version", "2022-11-28")
                                }
                            }
                        val teams: List<Team> = response.body()
                        val adminTeams = teams.filter { it.permission == "admin" }.map { it.name }.toSet()

                        if (adminTeams.isNotEmpty()) {
                            repo.copy(adminTeams = adminTeams)
                        } else {
                            repo
                        }
                    } catch (e: Exception) {
                        logger.error("Error fetching admin teams for repository ${repo.name}: ${e.message}")
                        repo
                    }
                }
            }.windowed(concurrencyLevel, concurrencyLevel, true) { windowOfDeferred ->
                // Process each window of deferred values concurrently
                runBlocking {
                    windowOfDeferred.awaitAll()
                }
            }.flatten() // Flatten the results of each window
        }
}

@Serializable
data class Team(
    val name: String,
    val permission: String
)

data class GithubRepository(
    val name: String,
    val isArchived: Boolean,
    val pushedAt: DateTime?,
    val hasVulnerabilityAlertsEnabled: Boolean,
    val vulnerabilityAlerts: Int,
    val adminTeams: Set<String> = emptySet()
)

typealias DateTime = String
typealias ID = String // Needed for graphql shenanigans (?)