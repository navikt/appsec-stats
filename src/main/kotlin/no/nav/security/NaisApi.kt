package no.nav.security

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.File
import kotlinx.serialization.json.Json
import no.nav.security.dto.NaisDeploymentApplication
import no.nav.security.dto.NaisDeploymentJob
import no.nav.security.dto.NaisDeploymentsResponse
import no.nav.security.dto.NaisEnvironmentsResponse
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
    val repositories: List<String>,
)
