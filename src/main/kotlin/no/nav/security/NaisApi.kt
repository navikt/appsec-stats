package no.nav.security

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.serialization.GraphQLClientKotlinxSerializer
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.ktor.client.*
import java.net.URI
import no.nav.security.deploymentsquery.Application
import no.nav.security.deploymentsquery.Job

class NaisApi(httpClient: HttpClient) {
    private val baseUrl = "https://console.nav.cloud.nais.io/graphql"
    private val client = GraphQLKtorClient(
        url = URI(baseUrl).toURL(),
        httpClient = httpClient,
        serializer = GraphQLClientKotlinxSerializer()
    )

    suspend fun teamStats(): Set<NaisTeam> {
        return fetchTeamStats()
    }

    suspend fun deployments(): Set<NaisDeployment> {
        val environments = fetchEnvironments()
        logger.info("Fetched ${environments.size} environment profiles: ${environments.joinToString(", ")}")
        return environments.flatMap { environment ->
            fetchDeployments(environment)
        }.toSet()
    }

    suspend fun repoVulnerabilities(): Set<NaisRepository> {
        return fetchRepoVulnerabilities()
    }

    private tailrec suspend fun fetchTeamStats(
        teamCursor: String? = null,
        repoCursor: String? = null,
        teams: Set<NaisTeam> = emptySet()
    ): Set<NaisTeam> {
        val ghQuery = TeamStatsQuery(
            variables = TeamStatsQuery.Variables(
                teamsCursor = teamCursor,
                repoCursor = repoCursor
            )
        )
        val response: GraphQLClientResponse<TeamStatsQuery.Result> = client.execute(ghQuery)

        val result = response.data?.teams?.nodes?.map { team ->
            val existingTeamRepos = teams.find { it.naisTeam == team.slug }?.repositories ?: emptyList()
            val newTeam = NaisTeam(
                naisTeam = team.slug,
                slsaCoverage = team.vulnerabilitySummary.coverage.toInt(),
                hasDeployedResources = (team.workloads.pageInfo.totalCount > 0),
                hasGithubRepositories = (team.repositories.nodes.isNotEmpty()),
                repositories = team.repositories.nodes.map { it.name.substringAfter("/") }.plus(existingTeamRepos)
            )
            // Fetch next page of repositories if available
            if (team.repositories.pageInfo.hasNextPage) {
                logger.info("Team ${team.slug} has more repositories, fetching next page with cursor ${team.repositories.pageInfo.endCursor}")
                return fetchTeamStats(
                    teamCursor = response.data?.teams?.pageInfo?.startCursor,
                    repoCursor = team.repositories.pageInfo.endCursor,
                    teams = teams.plus(newTeam)
                )
            }
            newTeam
        }?.toSet() ?: emptySet()

        if (response.errors?.isNotEmpty() == true) {
            throw RuntimeException("Error fetching team stats from NAIS API (teamCursor: $teamCursor, repoCursor $repoCursor): ${response.errors.toString()}")
        }

        if (result.isEmpty()) {
            return teams
        }

        return if (response.data?.teams?.pageInfo?.hasNextPage == true) {
            fetchTeamStats(
                teamCursor = response.data?.teams?.pageInfo?.endCursor,
                repoCursor = null,
                teams = teams.plus(result)
            )
        } else {
            teams.plus(result)
        }
    }

    private tailrec suspend fun fetchRepoVulnerabilities(
        teamCursor: String? = null,
        workloadCursor: String? = null,
        vulnCursor: String? = null,
        repos: Set<NaisRepository> = emptySet()
    ): Set<NaisRepository> {
        val ghQuery = RepoVulnerabilityQuery(
            variables = RepoVulnerabilityQuery.Variables(
                teamsCursor = teamCursor,
                workloadCursor = workloadCursor,
                vulnCursor = vulnCursor
            )
        )
        val response: GraphQLClientResponse<RepoVulnerabilityQuery.Result> = client.execute(ghQuery)
        if (response.errors?.isNotEmpty() == true) {
            throw RuntimeException("Error fetching workloads stats from Nais API: ${response.errors.toString()}")
        }

        val data = response.data
        if (data == null) {
            return repos
        }

        val newRepos = data.teams.nodes.flatMap { team ->
            team.workloads.nodes.mapNotNull { workload ->
                when (workload) {
                    is no.nav.security.repovulnerabilityquery.Application -> {
                        processWorkloadVulnerabilities(workload.name, workload.image, repos, teamCursor, workloadCursor)
                    }
                    is no.nav.security.repovulnerabilityquery.Job -> {
                        processWorkloadVulnerabilities(workload.name, workload.image, repos, teamCursor, workloadCursor)
                    }
                    else -> null // Skip unknown workload types
                }
            }
        }.toSet()

        val updatedRepos = repos.plus(newRepos)

        val currentTeam = data.teams.nodes.firstOrNull()
        if (currentTeam != null && currentTeam.workloads.pageInfo.hasNextPage) {
            return fetchRepoVulnerabilities(
                teamCursor = teamCursor,
                workloadCursor = currentTeam.workloads.pageInfo.endCursor,
                vulnCursor = null,
                repos = updatedRepos
            )
        }

        return if (data.teams.pageInfo.hasNextPage) {
            fetchRepoVulnerabilities(
                teamCursor = data.teams.pageInfo.endCursor,
                workloadCursor = null,
                vulnCursor = null,
                repos = updatedRepos
            )
        } else {
            updatedRepos
        }
    }

    private suspend fun processWorkloadVulnerabilities(
        workloadName: String,
        image: no.nav.security.repovulnerabilityquery.ContainerImage,
        repos: Set<NaisRepository>,
        teamCursor: String?,
        workloadCursor: String?
    ): NaisRepository? {
        val existingVulnerabilities = repos.find { it.name == workloadName.substringAfter("/") }?.vulnerabilities ?: emptySet()
        val vulnerabilities = image.vulnerabilities.nodes.map { vuln ->
            NaisVulnerability(
                identifier = vuln.identifier,
                severity = vuln.severity.name,
                suppressed = vuln.analysisTrail.suppressed
            )
        }.toSet()

        if (image.vulnerabilities.pageInfo.hasNextPage) {
            return null // This will be handled by the recursive call
        }

        return if (vulnerabilities.isNotEmpty()) {
            NaisRepository(
                name = workloadName.substringAfter("/"),
                vulnerabilities = vulnerabilities.plus(existingVulnerabilities)
            )
        } else {
            null // Skip repositories without vulnerabilities
        }
    }

    private suspend fun fetchEnvironments(): Set<String> {
        val ghQuery = Environments()
        val response = client.execute(ghQuery)
        return response.data?.environments?.nodes?.map { it.name }?.toSet() ?: emptySet()
    }

    private tailrec suspend fun fetchDeployments(
        environment: String,
        workloadCursor: String? = null,
        deployments: Set<NaisDeployment> = emptySet()
    ): Set<NaisDeployment> {
        val ghQuery = DeploymentsQuery(
            DeploymentsQuery.Variables(
                environment = environment,
                workloadCursor = workloadCursor
            )
        )
        val response = client.execute(ghQuery)

        if (response.data == null) {
            return deployments
        }

        val newDeployments = response.data?.environment?.workloads?.nodes?.flatMap { workload ->
            when (workload) {
                is Application -> workload.deployments.nodes.mapNotNull { deployment ->
                    deployment.repository?.takeIf { it.isNotEmpty() }?.let { repo ->
                        NaisDeployment(environment = environment, repository = repo, createdAt = deployment.createdAt)
                    }
                }
                is Job -> workload.deployments.nodes.mapNotNull { deployment ->
                    deployment.repository?.takeIf { it.isNotEmpty() }?.let { repo ->
                        NaisDeployment(environment = environment, repository = repo, createdAt = deployment.createdAt)
                    }
                }
                else -> emptyList()
            }
        }?.toSet() ?: emptySet()

        val updatedDeployments = deployments.plus(newDeployments)

        return if (response.data?.environment?.workloads?.pageInfo?.hasNextPage == true) {
            fetchDeployments(
                environment,
                response.data?.environment?.workloads?.pageInfo?.endCursor,
                updatedDeployments
            )
        } else {
            updatedDeployments
        }
    }
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
    val vulnerabilities: Set<NaisVulnerability>
)

data class NaisVulnerability(
    val identifier: String,
    val severity: String,
    val suppressed: Boolean,
)