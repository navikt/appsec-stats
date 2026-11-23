package no.nav.security

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.headers
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.security.dto.GithubRepositoriesResponse

private val json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

private val fetchGithubRepositoriesQuery =
    """
    query (${'$'}orgName: String!, ${'$'}repoEndCursor: String) {
      rateLimit { remaining limit resetAt }
      organization(login: ${'$'}orgName) {
        repositories(first: 100, after: ${'$'}repoEndCursor, orderBy: { field: CREATED_AT, direction: ASC }) {
          totalCount
          nodes {
            name nameWithOwner isArchived pushedAt hasVulnerabilityAlertsEnabled
            vulnerabilityAlerts(states: OPEN) { totalCount }
          }
          pageInfo { hasNextPage endCursor }
        }
      }
    }
    """.trimIndent()

open class GitHub(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://api.github.com/graphql",
    private val rateLimitHandler: RateLimitHandler = RateLimitHandler(),
) {
    open suspend fun fetchOrgRepositories(
        repositoryCursor: String? = null,
        repositoryListe: List<GithubRepository> = emptyList(),
    ): List<GithubRepository> {
        val response: GithubRepositoriesResponse =
            executeGraphQL(
                query = fetchGithubRepositoriesQuery,
                variables = mapOf("orgName" to "navikt", "repoEndCursor" to repositoryCursor),
            )

        response.errors?.let {
            logger.error("Error fetching repository from GitHub: $it")
            throw RuntimeException("Error fetching repository from GitHub: $it")
        }

        response.data?.rateLimit?.let { rateLimit ->
            rateLimitHandler.checkAndWait(
                remaining = rateLimit.remaining,
                limit = rateLimit.limit,
                resetAt = rateLimit.resetAt,
                operationName = "fetchOrgRepositories",
            )
        }

        val updated =
            repositoryListe + (
                response.data?.organization?.repositories?.nodes?.map {
                    GithubRepository(
                        name = it.name,
                        nameWithOwner = it.nameWithOwner,
                        isArchived = it.isArchived,
                        pushedAt = it.pushedAt,
                        hasVulnerabilityAlertsEnabled = it.hasVulnerabilityAlertsEnabled,
                        vulnerabilityAlerts = it.vulnerabilityAlerts?.totalCount ?: 0,
                    )
                } ?: emptyList()
            )

        val pageInfo =
            response.data
                ?.organization
                ?.repositories
                ?.pageInfo
        val nextCursor = pageInfo?.endCursor.takeIf { pageInfo?.hasNextPage == true }

        return if (nextCursor != null) {
            logger.info("Fetching next page of repositories with cursor: $nextCursor")
            fetchOrgRepositories(repositoryCursor = nextCursor, repositoryListe = updated)
        } else {
            updated
        }
    }

    private suspend inline fun <reified T> executeGraphQL(
        query: String,
        variables: Map<String, String?>,
    ): T {
        val body =
            buildString {
                append("""{"query":""")
                append(json.encodeToString(query))
                append(""","variables":{""")
                variables.entries
                    .filter { it.value != null }
                    .joinTo(this, ",") { (k, v) -> """"$k":${json.encodeToString(v)}""" }
                append("}}")
            }
        val response =
            httpClient
                .post(baseUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
        if (!response.status.isSuccess()) {
            logger.warn("GitHub GraphQL API error: HTTP ${response.status.value} for url=$baseUrl")
            throw IllegalStateException("GitHub GraphQL API error: HTTP ${response.status.value}")
        }
        return json.decodeFromString(response.body())
    }

}

suspend fun List<GithubRepository>.fetchRepositoryAdmins(httpClient: HttpClient): List<GithubRepository> {
    var errorCount = 0
    var successCount = 0

    val result =
        mapIndexed { index, repo ->
            if (index % 100 == 0) logger.info("Fetching admin teams: $index/$size repositories processed")
            try {
                val response =
                    httpClient
                        .get("https://api.github.com/repos/navikt/${repo.name}/teams") {
                            headers {
                                append(HttpHeaders.Accept, "application/vnd.github+json")
                                append("X-GitHub-Api-Version", "2026-03-10")
                            }
                        }
                if (!response.status.isSuccess()) {
                    logger.warn("GitHub API error: HTTP ${response.status.value} fetching teams for ${repo.name}")
                    errorCount++
                    repo
                } else {
                    val teams: List<Team> = response.body()
                    val adminTeams = teams.filter { it.permission == "admin" }.map { it.name }.toSet()
                    successCount++
                    if (adminTeams.isNotEmpty()) repo.copy(adminTeams = adminTeams) else repo
                }
            } catch (e: Exception) {
                errorCount++
                logger.warn("Error fetching teams for ${repo.name}: ${e.message}")
                repo
            }
        }

    logger.info("Fetched admin teams: $successCount successful, $errorCount failed")
    return result
}

@Serializable
data class Team(
    val name: String,
    val permission: String,
)

data class GithubRepository(
    val name: String,
    val nameWithOwner: String,
    val isArchived: Boolean,
    val pushedAt: DateTime?,
    val hasVulnerabilityAlertsEnabled: Boolean,
    val vulnerabilityAlerts: Int,
    val adminTeams: Set<String> = emptySet(),
)

typealias DateTime = String
typealias ID = String
typealias URI = String
