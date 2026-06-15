package no.nav.security

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.*
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import no.nav.security.dto.NaisDeploymentApplication
import no.nav.security.dto.NaisDeploymentJob
import no.nav.security.dto.NaisDeploymentsResponse
import no.nav.security.dto.NaisEnvironmentsResponse
import no.nav.security.dto.NaisRepoVulnApplication
import no.nav.security.dto.NaisRepoVulnJob
import no.nav.security.dto.NaisRepoVulnResponse
import no.nav.security.dto.NaisRepositoryConnection
import no.nav.security.dto.NaisTeamStatsResponse

private val naisJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        classDiscriminator = "__typename"
    }

private val teamStatsQuery =
    """
    query (${'$'}teamsCursor: Cursor, ${'$'}repoCursor: Cursor) {
      teams(first: 10, after: ${'$'}teamsCursor) {
        nodes {
          slug
          vulnerabilitySummary { coverage }
          workloads(first: 1) { pageInfo { totalCount } }
          repositories(first: 100, after: ${'$'}repoCursor) {
            nodes { name }
            pageInfo { hasNextPage endCursor }
          }
        }
        pageInfo { hasNextPage startCursor endCursor }
      }
    }
    """.trimIndent()

private val environmentsQuery =
    """
    query Environments {
      environments { nodes { name } }
    }
    """.trimIndent()

private val deploymentsQuery =
    """
    query (${'$'}environment: String!, ${'$'}workloadCursor: Cursor) {
      environment(name: ${'$'}environment) {
        workloads(first: 100, after: ${'$'}workloadCursor, orderBy: { field: DEPLOYMENT_TIME, direction: DESC }) {
          nodes {
            __typename
            ... on Application { deployments(first: 1) { nodes { repository createdAt } } }
            ... on Job { deployments(first: 1) { nodes { repository createdAt } } }
          }
          pageInfo { hasNextPage endCursor }
        }
      }
    }
    """.trimIndent()

private val repoVulnerabilityQuery =
    """
    query (${'$'}teamsCursor: Cursor, ${'$'}vulnCursor: Cursor, ${'$'}workloadCursor: Cursor) {
      teams(first: 1, after: ${'$'}teamsCursor, filter: {hasWorkloads:true}) {
        pageInfo { hasNextPage startCursor endCursor }
        nodes {
          workloads(first: 5, after: ${'$'}workloadCursor) {
            pageInfo { hasNextPage startCursor endCursor }
            nodes {
              __typename
              ... on Application {
                deployments(first: 1) { nodes { repository } }
                name
                image {
                  vulnerabilities(first: 50, after: ${'$'}vulnCursor) {
                    pageInfo { hasNextPage endCursor }
                    nodes { identifier severity suppression { state } }
                  }
                }
              }
              ... on Job {
                deployments(first: 1) { nodes { repository } }
                name
                image {
                  vulnerabilities(first: 50, after: ${'$'}vulnCursor) {
                    pageInfo { hasNextPage endCursor }
                    nodes { identifier severity suppression { state } }
                  }
                }
              }
            }
          }
        }
      }
    }
    """.trimIndent()

open class NaisApi(
    private val httpClient: HttpClient,
    private val pathToToken: String? = null,
) {
    private val baseUrl = "https://console.nav.cloud.nais.io/graphql"

    open suspend fun teamStats(): Set<NaisTeam> = fetchAllTeamStats()

    open suspend fun deployments(): Set<NaisDeployment> {
        val environments = fetchEnvironments()
        logger.info("Fetched ${environments.size} environment profiles: ${environments.joinToString(", ")}")
        return environments
            .flatMap { environment ->
                fetchDeployments(environment)
            }.toSet()
    }

    open suspend fun repoVulnerabilities(): Set<NaisRepository> = fetchRepoVulnerabilities()

    private suspend fun fetchAllTeamStats(): Set<NaisTeam> {
        val allTeams = mutableMapOf<String, NaisTeam>()
        var teamCursor: String? = null
        var hasNextPage = true

        while (hasNextPage) {
            val response = executeTeamStatsQuery(teamCursor, null)
            response.data?.teams?.nodes?.forEach { team ->
                val teamRepositories = fetchAllRepositoriesForTeam(team.repositories)
                val existingTeam = allTeams[team.slug]
                allTeams[team.slug] =
                    NaisTeam(
                        naisTeam = team.slug,
                        slsaCoverage = team.vulnerabilitySummary.coverage.toInt(),
                        hasDeployedResources = (team.workloads.pageInfo.totalCount > 0),
                        hasGithubRepositories = teamRepositories.isNotEmpty(),
                        repositories = (existingTeam?.repositories ?: emptyList()) + teamRepositories,
                    )
            }
            hasNextPage = response.data
                ?.teams
                ?.pageInfo
                ?.hasNextPage ?: false
            if (hasNextPage) {
                teamCursor =
                    response.data
                        ?.teams
                        ?.pageInfo
                        ?.endCursor
            }
        }
        return allTeams.values.toSet()
    }

    private suspend fun fetchAllRepositoriesForTeam(initialRepoConnection: NaisRepositoryConnection): List<String> {
        val repositories = mutableListOf<String>()
        repositories.addAll(initialRepoConnection.nodes.map { it.name.substringAfter("/") })

        var repoCursor: String? = initialRepoConnection.pageInfo.endCursor
        var hasNextRepoPage = initialRepoConnection.pageInfo.hasNextPage

        while (hasNextRepoPage) {
            val response = executeTeamStatsQuery(null, repoCursor)
            val teamNode =
                response.data
                    ?.teams
                    ?.nodes
                    ?.firstOrNull()
            if (teamNode != null) {
                repositories.addAll(teamNode.repositories.nodes.map { it.name.substringAfter("/") })
                hasNextRepoPage = teamNode.repositories.pageInfo.hasNextPage
                if (hasNextRepoPage) {
                    repoCursor = teamNode.repositories.pageInfo.endCursor
                }
            } else {
                hasNextRepoPage = false
            }
        }
        return repositories
    }

    private suspend fun executeTeamStatsQuery(
        teamCursor: String?,
        repoCursor: String?,
    ): NaisTeamStatsResponse {
        val response =
            executeGraphQL<NaisTeamStatsResponse>(
                query = teamStatsQuery,
                variables = mapOf("teamsCursor" to teamCursor, "repoCursor" to repoCursor),
            )
        if (response.errors?.isNotEmpty() == true) {
            throw RuntimeException(
                "Error fetching team stats from NAIS API (teamCursor: $teamCursor, repoCursor: $repoCursor): ${response.errors}",
            )
        }
        return response
    }

    private suspend fun fetchEnvironments(): Set<String> {
        val response =
            executeGraphQL<NaisEnvironmentsResponse>(
                query = environmentsQuery,
                variables = emptyMap(),
            )
        return response.data
            ?.environments
            ?.nodes
            ?.map { it.name }
            ?.toSet() ?: emptySet()
    }

    private tailrec suspend fun fetchDeployments(
        environment: String,
        workloadCursor: String? = null,
        deployments: Set<NaisDeployment> = emptySet(),
    ): Set<NaisDeployment> {
        val response =
            executeGraphQL<NaisDeploymentsResponse>(
                query = deploymentsQuery,
                variables = mapOf("environment" to environment, "workloadCursor" to workloadCursor),
            )

        if (response.data == null) return deployments

        val newDeployments =
            response.data.environment
                ?.workloads
                ?.nodes
                ?.flatMap { workload ->
                    when (workload) {
                        is NaisDeploymentApplication -> {
                            workload.deployments.nodes.mapNotNull { deployment ->
                                val repo = deployment.repository?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                                val createdAt = deployment.createdAt ?: return@mapNotNull null
                                NaisDeployment(environment = environment, repository = repo, createdAt = createdAt)
                            }
                        }

                        is NaisDeploymentJob -> {
                            workload.deployments.nodes.mapNotNull { deployment ->
                                val repo = deployment.repository?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                                val createdAt = deployment.createdAt ?: return@mapNotNull null
                                NaisDeployment(environment = environment, repository = repo, createdAt = createdAt)
                            }
                        }
                    }
                }?.toSet() ?: emptySet()

        val updatedDeployments = deployments + newDeployments

        return if (response.data.environment
                ?.workloads
                ?.pageInfo
                ?.hasNextPage == true
        ) {
            fetchDeployments(
                environment,
                response.data.environment.workloads.pageInfo.endCursor,
                updatedDeployments,
            )
        } else {
            updatedDeployments
        }
    }

    private tailrec suspend fun fetchRepoVulnerabilities(
        teamCursor: String? = null,
        workloadCursor: String? = null,
        vulnCursor: String? = null,
        repos: Set<NaisRepository> = emptySet(),
    ): Set<NaisRepository> {
        logger.info("Fetching repo vulnerabilities (teamCursor: $teamCursor, workloadCursor: $workloadCursor, vulnCursor: $vulnCursor)")
        val response = executeWithRetry(teamCursor, workloadCursor, vulnCursor)

        if (response.errors?.isNotEmpty() == true) {
            logger.error(
                "GraphQL errors in fetchRepoVulnerabilities (teamCursor: $teamCursor, workloadCursor: $workloadCursor, vulnCursor: $vulnCursor)",
            )
            response.errors.forEachIndexed { index, error ->
                try {
                    logger.error("Error[$index]: message='${error.message}', path=${error.path}, locations=${error.locations}")
                } catch (e: Exception) {
                    logger.error("Error[$index]: Failed to serialize error details: ${e.message}")
                }
            }
            logger.error("Stopping processing. Resume with: teamCursor=$teamCursor, workloadCursor=$workloadCursor, vulnCursor=$vulnCursor")
            throw RuntimeException(
                "Error fetching workloads stats from Nais API at teamCursor=$teamCursor, workloadCursor=$workloadCursor, vulnCursor=$vulnCursor. Check logs for error details.",
            )
        }

        val data = response.data ?: return repos

        val newRepos =
            data.teams.nodes
                .flatMap { team ->
                    team.workloads.nodes.mapNotNull { workload ->
                        when (workload) {
                            is NaisRepoVulnApplication -> processWorkloadVulnerabilities(workload.name, workload.image, repos)
                            is NaisRepoVulnJob -> processWorkloadVulnerabilities(workload.name, workload.image, repos)
                        }
                    }
                }.toSet()

        val updatedRepos = mergeRepositories(repos, newRepos)

        val workloadWithMoreVulns =
            data.teams.nodes
                .asSequence()
                .flatMap { team -> team.workloads.nodes.asSequence() }
                .firstOrNull { workload ->
                    when (workload) {
                        is NaisRepoVulnApplication -> workload.image.vulnerabilities.pageInfo.hasNextPage
                        is NaisRepoVulnJob -> workload.image.vulnerabilities.pageInfo.hasNextPage
                    }
                }

        if (workloadWithMoreVulns != null) {
            val vulnEndCursor =
                when (workloadWithMoreVulns) {
                    is NaisRepoVulnApplication -> workloadWithMoreVulns.image.vulnerabilities.pageInfo.endCursor
                    is NaisRepoVulnJob -> workloadWithMoreVulns.image.vulnerabilities.pageInfo.endCursor
                }
            val workloadName =
                when (workloadWithMoreVulns) {
                    is NaisRepoVulnApplication -> workloadWithMoreVulns.name
                    is NaisRepoVulnJob -> workloadWithMoreVulns.name
                }
            logger.info("Workload '$workloadName' has more vulnerabilities (cursor: $vulnEndCursor). Fetching next page.")
            return fetchRepoVulnerabilities(
                teamCursor = teamCursor,
                workloadCursor = workloadCursor,
                vulnCursor = vulnEndCursor,
                repos = updatedRepos,
            )
        }

        val teamWithMoreWorkloads =
            data.teams.nodes.firstOrNull { team ->
                team.workloads.pageInfo.hasNextPage
            }

        if (teamWithMoreWorkloads != null) {
            logger.info("Team has more workloads (cursor: ${teamWithMoreWorkloads.workloads.pageInfo.endCursor}). Fetching next page.")
            return fetchRepoVulnerabilities(
                teamCursor = teamCursor,
                workloadCursor = teamWithMoreWorkloads.workloads.pageInfo.endCursor,
                vulnCursor = null,
                repos = updatedRepos,
            )
        }

        return if (data.teams.pageInfo.hasNextPage) {
            logger.info("More teams available (cursor: ${data.teams.pageInfo.endCursor}). Fetching next page.")
            fetchRepoVulnerabilities(
                teamCursor = data.teams.pageInfo.endCursor,
                workloadCursor = null,
                vulnCursor = null,
                repos = updatedRepos,
            )
        } else {
            logger.info("Pagination complete. Total repositories with vulnerabilities: ${updatedRepos.size}")
            updatedRepos
        }
    }

    private suspend fun executeWithRetry(
        teamCursor: String?,
        workloadCursor: String?,
        vulnCursor: String?,
        maxRetries: Int = 3,
    ): NaisRepoVulnResponse {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return executeGraphQL(
                    query = repoVulnerabilityQuery,
                    variables =
                        mapOf(
                            "teamsCursor" to teamCursor,
                            "workloadCursor" to workloadCursor,
                            "vulnCursor" to vulnCursor,
                        ),
                )
            } catch (e: HttpRequestTimeoutException) {
                lastException = e
                val delayMs = 10000L * (attempt + 1)
                logger.warn(
                    "Timeout on attempt ${attempt + 1}/$maxRetries for RepoVulnerabilityQuery (teamCursor: $teamCursor, workloadCursor: $workloadCursor, vulnCursor: $vulnCursor). Retrying in ${delayMs}ms.",
                )
                delay(delayMs)
            } catch (e: Exception) {
                logger.error(
                    "Exception executing RepoVulnerabilityQuery (teamCursor: $teamCursor, workloadCursor: $workloadCursor, vulnCursor: $vulnCursor): ${e.message}",
                    e,
                )
                throw RuntimeException(
                    "Error executing RepoVulnerabilityQuery (teamCursor: $teamCursor, workloadCursor: $workloadCursor, vulnCursor: $vulnCursor): ${e.message}",
                    e,
                )
            }
        }
        throw RuntimeException(
            "Error executing RepoVulnerabilityQuery after $maxRetries attempts (teamCursor: $teamCursor, workloadCursor: $workloadCursor, vulnCursor: $vulnCursor): ${lastException?.message}",
            lastException,
        )
    }

    private fun processWorkloadVulnerabilities(
        workloadName: String,
        image: no.nav.security.dto.NaisContainerImage,
        repos: Set<NaisRepository>,
    ): NaisRepository? {
        val repositoryName = workloadName.substringAfter("/")
        val existingVulnerabilities = repos.find { it.name == repositoryName }?.vulnerabilities ?: emptySet()
        val newVulnerabilities =
            image.vulnerabilities.nodes
                .map { vuln ->
                    NaisVulnerability(
                        identifier = vuln.identifier,
                        severity = vuln.severity,
                        suppressed = vuln.suppression != null,
                    )
                }.toSet()

        return if (newVulnerabilities.isNotEmpty()) {
            NaisRepository(
                name = repositoryName,
                vulnerabilities = existingVulnerabilities + newVulnerabilities,
            )
        } else {
            repos.find { it.name == repositoryName }
        }
    }

    private fun mergeRepositories(
        existingRepos: Set<NaisRepository>,
        newRepos: Set<NaisRepository>,
    ): Set<NaisRepository> {
        val repoMap = (existingRepos + newRepos).groupBy { it.name }
        return repoMap
            .map { (_, repos) ->
                repos.reduce { acc, repo ->
                    acc.copy(vulnerabilities = acc.vulnerabilities + repo.vulnerabilities)
                }
            }.toSet()
    }

    private suspend inline fun <reified T> executeGraphQL(
        query: String,
        variables: Map<String, String?>,
    ): T {
        val body =
            buildString {
                append("""{"query":""")
                append(naisJson.encodeToString(query))
                append(""","variables":{""")
                variables.entries
                    .filter { it.value != null }
                    .joinTo(this, ",") { (k, v) -> """"$k":${naisJson.encodeToString(v)}""" }
                append("}}")
            }
        val response =
            httpClient.post(baseUrl) {
                contentType(ContentType.Application.Json)
                bearerAuth(readAuthToken())
                setBody(body)
            }
        if (!response.status.isSuccess()) {
            logger.warn("NAIS GraphQL API error: HTTP ${response.status.value} for url=$baseUrl")
            throw IllegalStateException("NAIS GraphQL API error: HTTP ${response.status.value}")
        }
        return naisJson.decodeFromString(response.body())
    }

    private fun readAuthToken() = pathToToken?.let {
        File(it).readText(Charsets.UTF_8)
    } ?: "not for real"
}

data class NaisDeployment(
    val environment: String,
    val repository: String,
    val createdAt: String,
)

data class NaisTeam(
    val naisTeam: String,
    val slsaCoverage: Int,
    val hasDeployedResources: Boolean,
    val hasGithubRepositories: Boolean,
    val repositories: List<String>,
)

data class NaisRepository(
    val name: String,
    val vulnerabilities: Set<NaisVulnerability>,
)

data class NaisVulnerability(
    val identifier: String,
    val severity: String,
    val suppressed: Boolean,
)
