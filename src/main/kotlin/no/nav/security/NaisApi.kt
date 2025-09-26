package no.nav.security

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.serialization.GraphQLClientKotlinxSerializer
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.ktor.client.*
import java.net.URI
import no.nav.security.deploymentsquery.Application
import no.nav.security.deploymentsquery.Job
import no.nav.security.teamstatsquery.RepositoryConnection

class NaisApi(httpClient: HttpClient) {
    private val baseUrl = "https://console.nav.cloud.nais.io/graphql"
    private val client = GraphQLKtorClient(
        url = URI(baseUrl).toURL(),
        httpClient = httpClient,
        serializer = GraphQLClientKotlinxSerializer()
    )

    suspend fun teamStats(): Set<NaisTeam> {
        return fetchAllTeamStats()
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

    private suspend fun fetchAllTeamStats(): Set<NaisTeam> {
        val allTeams = mutableMapOf<String, NaisTeam>()
        var teamCursor: String? = null
        var hasNextPage = true

        while (hasNextPage) {
            val response = executeTeamStatsQuery(teamCursor, null)
            response.data?.teams?.nodes?.forEach { team ->
                val teamRepositories = fetchAllRepositoriesForTeam(team.repositories)
                val existingTeam = allTeams[team.slug]
                val updatedTeam = NaisTeam(
                    naisTeam = team.slug,
                    slsaCoverage = team.vulnerabilitySummary.coverage.toInt(),
                    hasDeployedResources = (team.workloads.pageInfo.totalCount > 0),
                    hasGithubRepositories = teamRepositories.isNotEmpty(),
                    repositories = (existingTeam?.repositories ?: emptyList()) + teamRepositories
                )
                allTeams[team.slug] = updatedTeam
            }
            hasNextPage = response.data?.teams?.pageInfo?.hasNextPage ?: false
            if (hasNextPage) {
                teamCursor = response.data?.teams?.pageInfo?.endCursor
            }
        }
        return allTeams.values.toSet()
    }

    private suspend fun fetchAllRepositoriesForTeam(
        initialRepoConnection: RepositoryConnection
    ): List<String> {
        val repositories = mutableListOf<String>()
        repositories.addAll(initialRepoConnection.nodes.map { it.name.substringAfter("/") })

        var repoCursor: String? = initialRepoConnection.pageInfo.endCursor
        var hasNextRepoPage = initialRepoConnection.pageInfo.hasNextPage

        while (hasNextRepoPage) {
            val response = executeTeamStatsQuery(null, repoCursor)
            val teamNode = response.data?.teams?.nodes?.firstOrNull()
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
        repoCursor: String?
    ): GraphQLClientResponse<TeamStatsQuery.Result> {
        val variables =
            TeamStatsQuery.Variables(teamsCursor = teamCursor, repoCursor = repoCursor)
        val query = TeamStatsQuery(variables)
        val response = client.execute(query)
        if (response.errors?.isNotEmpty() == true) {
            throw RuntimeException(
                "Error fetching team stats from NAIS API (teamCursor: $teamCursor, repoCursor: $repoCursor): ${response.errors}"
            )
        }
        return response
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

        val data = response.data ?: return repos

        // Process vulnerabilities from the current page
        val newRepos = data.teams.nodes.flatMap { team ->
            team.workloads.nodes.mapNotNull { workload ->
                when (workload) {
                    is no.nav.security.repovulnerabilityquery.Application -> {
                        processWorkloadVulnerabilities(workload.name, workload.image, repos)
                    }
                    is no.nav.security.repovulnerabilityquery.Job -> {
                        processWorkloadVulnerabilities(workload.name, workload.image, repos)
                    }
                    else -> null // Skip unknown workload types
                }
            }
        }.toSet()

        // Merge repositories by name, combining vulnerabilities for repositories with the same name
        val updatedRepos = mergeRepositories(repos, newRepos)

        val workloadWithMoreVulns = data.teams.nodes.asSequence()
            .flatMap { team -> team.workloads.nodes.asSequence() }
            .firstOrNull { workload ->
                when (workload) {
                    is no.nav.security.repovulnerabilityquery.Application ->
                        workload.image.vulnerabilities.pageInfo.hasNextPage
                    is no.nav.security.repovulnerabilityquery.Job ->
                        workload.image.vulnerabilities.pageInfo.hasNextPage
                    else -> false
                }
            }

        if (workloadWithMoreVulns != null) {
            val vulnEndCursor = when (workloadWithMoreVulns) {
                is no.nav.security.repovulnerabilityquery.Application ->
                    workloadWithMoreVulns.image.vulnerabilities.pageInfo.endCursor
                is no.nav.security.repovulnerabilityquery.Job ->
                    workloadWithMoreVulns.image.vulnerabilities.pageInfo.endCursor
                else -> null
            }
            val workloadName = when (workloadWithMoreVulns) {
                is no.nav.security.repovulnerabilityquery.Application -> workloadWithMoreVulns.name
                is no.nav.security.repovulnerabilityquery.Job -> workloadWithMoreVulns.name
                else -> "unknown"
            }
            logger.info("Workload '$workloadName' has more vulnerabilities (cursor: $vulnEndCursor). Fetching next page.")
            return fetchRepoVulnerabilities(
                teamCursor = teamCursor,
                workloadCursor = workloadCursor,
                vulnCursor = vulnEndCursor,
                repos = updatedRepos
            )
        }

        val teamWithMoreWorkloads = data.teams.nodes.firstOrNull { team ->
            team.workloads.pageInfo.hasNextPage
        }

        if (teamWithMoreWorkloads != null) {
            logger.info("Team has more workloads (cursor: ${teamWithMoreWorkloads.workloads.pageInfo.endCursor}). Fetching next page.")
            return fetchRepoVulnerabilities(
                teamCursor = teamCursor,
                workloadCursor = teamWithMoreWorkloads.workloads.pageInfo.endCursor,
                vulnCursor = null,
                repos = updatedRepos
            )
        }

        return if (data.teams.pageInfo.hasNextPage) {
            logger.info("More teams available (cursor: ${data.teams.pageInfo.endCursor}). Fetching next page.")
            fetchRepoVulnerabilities(
                teamCursor = data.teams.pageInfo.endCursor,
                workloadCursor = null,
                vulnCursor = null,
                repos = updatedRepos
            )
        } else {
            logger.info("Pagination complete. Total repositories with vulnerabilities: ${updatedRepos.size}")
            updatedRepos
        }
    }

    private suspend fun processWorkloadVulnerabilities(
        workloadName: String,
        image: no.nav.security.repovulnerabilityquery.ContainerImage,
        repos: Set<NaisRepository>
    ): NaisRepository? {
        val repositoryName = workloadName.substringAfter("/")
        val existingVulnerabilities = repos.find { it.name == repositoryName }?.vulnerabilities ?: emptySet()
        val newVulnerabilities = image.vulnerabilities.nodes.map { vuln ->
            NaisVulnerability(
                identifier = vuln.identifier,
                severity = vuln.severity.name,
                suppressed = !vuln.suppression?.state?.name.isNullOrEmpty() // False if suppression state is null or empty
            )
        }.toSet()

        return if (newVulnerabilities.isNotEmpty()) {
            NaisRepository(
                name = repositoryName,
                vulnerabilities = existingVulnerabilities.plus(newVulnerabilities)
            )
        } else {
            // If no new vulnerabilities but we have existing ones, preserve the existing repository
            repos.find { it.name == repositoryName }
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

    private fun mergeRepositories(existingRepos: Set<NaisRepository>, newRepos: Set<NaisRepository>): Set<NaisRepository> {
        val repoMap = (existingRepos + newRepos).groupBy { it.name }
        return repoMap.map { (_, repos) ->
            repos.reduce { acc, repo ->
                acc.copy(vulnerabilities = acc.vulnerabilities + repo.vulnerabilities)
            }
        }.toSet()
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