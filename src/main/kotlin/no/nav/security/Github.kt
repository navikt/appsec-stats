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
import no.nav.security.dto.GithubVulnerabilitiesResponse

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

private val fetchGithubVulnerabilitiesQuery =
    """
    query (${'$'}orgName: String!, ${'$'}repoEndCursor: String, ${'$'}repoStartCursor: String, ${'$'}vulnEndCursor: String) {
      rateLimit { remaining limit resetAt }
      organization(login: ${'$'}orgName) {
        repositories(first: 30, isArchived: false, after: ${'$'}repoEndCursor, before: ${'$'}repoStartCursor, orderBy: { field: CREATED_AT, direction: ASC }) {
          nodes {
            name nameWithOwner
            vulnerabilityAlerts(first: 100, after: ${'$'}vulnEndCursor, states: OPEN) {
              nodes {
                dependencyScope
                dependabotUpdate { pullRequest { permalink } }
                securityAdvisory {
                  publishedAt
                  cvss { score }
                  summary
                  identifiers { value type }
                }
                securityVulnerability {
                  severity
                  package { ecosystem name }
                }
              }
              pageInfo { hasNextPage endCursor }
            }
          }
          pageInfo { hasNextPage endCursor startCursor }
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

    open suspend fun fetchRepositoryVulnerabilities(
        repoEndCursor: String? = null,
        repoStartCursor: String? = null,
        vulnEndCursor: String? = null,
        vulnerabilitiesList: List<GithubRepoVulnerabilities> = emptyList(),
    ): List<GithubRepoVulnerabilities> {
        logger.info("Fetching more vulnerabilities, vulnCount: ${vulnerabilitiesList.size}")

        val response: GithubVulnerabilitiesResponse =
            try {
                executeGraphQL(
                    query = fetchGithubVulnerabilitiesQuery,
                    variables =
                        mapOf(
                            "orgName" to "navikt",
                            "repoEndCursor" to repoEndCursor,
                            "repoStartCursor" to repoStartCursor,
                            "vulnEndCursor" to vulnEndCursor,
                        ),
                )
            } catch (e: Exception) {
                logger.error(
                    "Exception during GraphQL execution for vulnerabilities. repoEndCursor=$repoEndCursor, vulnEndCursor=$vulnEndCursor",
                    e,
                )
                throw e
            }

        response.errors?.let { errors ->
            val errorDetails =
                errors
                    .mapIndexed { index, error ->
                        buildString {
                            append("\n  Error ${index + 1}:")
                            append("\n    Message: ${error.message}")
                            error.path?.let { append("\n    Path: $it") }
                            error.locations?.let { append("\n    Locations: $it") }
                            error.extensions?.let { append("\n    Extensions: $it") }
                        }
                    }.joinToString("")
            logger.error("Error fetching vulnerabilities from GitHub (${errors.size} error(s)):$errorDetails")
            throw RuntimeException("Error fetching vulnerabilities from GitHub: ${errors.map { it.message }}")
        }

        response.data?.rateLimit?.let { rateLimit ->
            rateLimitHandler.checkAndWait(
                remaining = rateLimit.remaining,
                limit = rateLimit.limit,
                resetAt = rateLimit.resetAt,
                operationName = "fetchRepositoryVulnerabilities",
            )
        }

        val repositories =
            response.data?.organization?.repositories?.nodes?.mapNotNull { repo ->
                val vulnerabilities =
                    repo.vulnerabilityAlerts?.nodes?.mapNotNull { alert ->
                        alert.securityVulnerability?.let { secVuln ->
                            GithubRepoVulnerabilities.GithubVulnerability(
                                severity = secVuln.severity,
                                identifier =
                                    alert.securityAdvisory?.identifiers?.map { identifier ->
                                        GithubRepoVulnerabilities.GithubVulnerability.GithubVulnerabilityIdentifier(
                                            value = identifier.value,
                                            type = identifier.type,
                                        )
                                    } ?: emptyList(),
                                dependencyScope = alert.dependencyScope,
                                dependabotUpdatePullRequestUrl = alert.dependabotUpdate?.pullRequest?.permalink,
                                publishedAt = alert.securityAdvisory?.publishedAt,
                                cvssScore = alert.securityAdvisory?.cvss?.score,
                                summary = alert.securityAdvisory?.summary,
                                packageEcosystem = secVuln.pkg.ecosystem,
                                packageName = secVuln.pkg.name,
                            )
                        }
                    } ?: emptyList()

                if (vulnerabilities.isNotEmpty()) {
                    GithubRepoVulnerabilities(
                        repository = repo.name,
                        nameWithOwner = repo.nameWithOwner,
                        vulnerabilities = vulnerabilities,
                    )
                } else {
                    null
                }
            } ?: emptyList()

        val updatedVulnerabilitiesList = mergeGithubRepositories(vulnerabilitiesList, repositories)

        val repoWithMoreVulns =
            response.data?.organization?.repositories?.nodes?.find { repo ->
                repo.vulnerabilityAlerts?.pageInfo?.hasNextPage == true
            }

        if (repoWithMoreVulns != null) {
            logger.info("Repository ${repoWithMoreVulns.name} has more vulnerabilities, fetching next page")
            return fetchRepositoryVulnerabilities(
                repoEndCursor = repoEndCursor,
                repoStartCursor = repoStartCursor,
                vulnEndCursor = repoWithMoreVulns.vulnerabilityAlerts?.pageInfo?.endCursor,
                vulnerabilitiesList = updatedVulnerabilitiesList,
            )
        }

        val repoPageInfo =
            response.data
                ?.organization
                ?.repositories
                ?.pageInfo
        val nextRepoPage = repoPageInfo?.endCursor.takeIf { repoPageInfo?.hasNextPage == true }

        return if (nextRepoPage != null) {
            logger.info("Fetching next page of repositories with cursor: $nextRepoPage")
            fetchRepositoryVulnerabilities(
                repoEndCursor = nextRepoPage,
                repoStartCursor = null,
                vulnEndCursor = null,
                vulnerabilitiesList = updatedVulnerabilitiesList,
            )
        } else {
            updatedVulnerabilitiesList
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
            throw IllegalStateException("GitHub GraphQL API error: HTTP ${response.status.value}")
        }
        return json.decodeFromString(response.body())
    }

    private fun mergeGithubRepositories(
        existingRepos: List<GithubRepoVulnerabilities>,
        newRepos: List<GithubRepoVulnerabilities>,
    ): List<GithubRepoVulnerabilities> {
        val repoMap = (existingRepos + newRepos).groupBy { it.repository }
        return repoMap.map { (_, repos) ->
            repos.reduce { acc, repo ->
                acc.copy(vulnerabilities = acc.vulnerabilities + repo.vulnerabilities)
            }
        }
    }
}

suspend fun List<GithubRepository>.fetchRepositoryAdmins(httpClient: HttpClient): List<GithubRepository> {
    var errorCount = 0
    var successCount = 0

    val result =
        map { repo ->
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
                    throw IllegalStateException("GitHub API error ${response.status.value} fetching teams for ${repo.name}")
                }
                val teams: List<Team> = response.body()
                val adminTeams = teams.filter { it.permission == "admin" }.map { it.name }.toSet()

                successCount++
                if (adminTeams.isNotEmpty()) repo.copy(adminTeams = adminTeams) else repo
            } catch (e: Exception) {
                errorCount++
                logger.warn("Error fetching teams for ${repo.name}: ${e.message}")
                repo
            }
        }

    if (errorCount > 0) {
        logger.info("Fetched admin teams: $successCount successful, $errorCount failed")
    }

    return result
}

@Serializable
data class Team(
    val name: String,
    val permission: String,
)

data class GithubRepoVulnerabilities(
    val repository: String,
    val nameWithOwner: String,
    val vulnerabilities: List<GithubVulnerability>,
) {
    data class GithubVulnerability(
        val severity: String,
        val identifier: List<GithubVulnerabilityIdentifier>,
        val dependencyScope: String? = null,
        val dependabotUpdatePullRequestUrl: String? = null,
        val publishedAt: String? = null,
        val cvssScore: Double? = null,
        val summary: String? = null,
        val packageEcosystem: String? = null,
        val packageName: String? = null,
    ) {
        data class GithubVulnerabilityIdentifier(
            val value: String,
            val type: String,
        )
    }
}

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
