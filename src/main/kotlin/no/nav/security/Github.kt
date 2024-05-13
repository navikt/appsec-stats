package no.nav.security

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Accept
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpHeaders.UserAgent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

class GitHub(
    private val http: HttpClient,
    private val authToken: String,
    private val baseUrl: String = "https://api.github.com/graphql"
) {

    suspend fun fetchStatsForBigQuery(): List<IssueCountRecord> {
        val records = mutableListOf<IssueCountRecord>()
        val teams = fetchTeams()
        val teamRepos = mutableMapOf<String, List<Repository>>()
        teams.forEach { team ->
            teamRepos[team] = fetchRepositories()
        }

        teamRepos.forEach { (team, repository) ->
            repository.forEach { repo ->
                records.plus(
                    IssueCountRecord(
                        teamName = team,
                        lastPush = repository.last().pushedAt,
                        repositoryName = repository.last().name,
                        vulnerabilityAlertsEnabled = repository.last().hasVulnerabilityAlertsEnabled,
                        vulnerabilityCount = fetchVulnerabilityAlertsForRepo(repo.name).size
                    )
                )
            }
        }

        return records
    }

    internal suspend fun fetchTeams(): List<String> {
        fun fetchTeamsReqBody(after: String?) = """
             query {
                organization(login: "navikt") {
                    teams(first: 100, after: $after) {
                        nodes {
                            name
                        }
                        pageInfo {
                            endCursor
                            startCursor
                            hasNextPage
                        }
                    }
                }
             }
        """.trimIndent()

        val teams = mutableListOf<String>()
        var offset: String? = null

        do {
            val reqBody = fetchTeamsReqBody(offset).replace("\n", " ")
            logger.info("Request body for fetch teams: $reqBody")
            val response = http.post(baseUrl) {
                setBody(reqBody)
            }.body<GraphQlResponse>()
            offset = response.data.organization.teams?.pageInfo?.endCursor
            teams.plus(response.data.organization.teams?.nodes)
            logger.info("Fetched ${teams.size} teams, offset: $offset")
        } while (false) // response.data.organization.teams?.pageInfo?.hasNextPage == true

        return teams
    }

    internal suspend fun fetchRepositories(): List<Repository> {
        fun fetchRepositoryReqBody(after: String?) = """
             query {
              organization(login: "navikt") {
                repositories(first: 100, isArchived: false, after: $after) {
                  nodes {
                    name
                    pushedAt
                    hasVulnerabilityAlertsEnabled
                  }
                  pageInfo {
                    endCursor
                    startCursor
                    hasNextPage
                  }
                }
              }
            }
        """.trimIndent()

        val repositories = mutableListOf<Repository>()
        var offset: String? = null

        do {
            val response = http.post(baseUrl) {
                setBody(fetchRepositoryReqBody(offset).replace("\n", " "))
            }.body<GraphQlResponse>()
            offset = response.data.organization.teams?.pageInfo?.endCursor
            response.data.organization.repositories?.nodes?.let { repositories.addAll(it) }
        } while (false) // response.data.organization.teams?.pageInfo?.hasNextPage == true

        return repositories
    }

    internal suspend fun fetchVulnerabilityAlertsForRepo(repoName: String): List<VulnerabilityAlertNode.VulnerabilityAlertEntry> {
        fun fetchAlertsForTeamquery(repoName: String, after: String?) = """
            query {
              organization(login: "navikt") {
                repository(name: "$repoName") {
                  vulnerabilityAlerts(states: OPEN, first: 100, after: $after) {
                    nodes {
                      createdAt
                      dependencyScope
                      securityVulnerability {
                        severity
                      }
                    }
                    pageInfo {
                      endCursor
                      startCursor
                      hasNextPage
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val alerts = mutableListOf<VulnerabilityAlertNode.VulnerabilityAlertEntry>()
        var offset: String? = null

        do {
            val response = http.post(baseUrl) {
                setBody(fetchAlertsForTeamquery(repoName, offset).replace("\n", " "))
            }.body<GraphQlResponse>()
            offset = response.data.organization.repository?.vulnerabilityAlerts?.pageInfo?.endCursor
            response.data.organization.repository?.vulnerabilityAlerts?.nodes?.let { alerts.addAll(it) }
        } while (false) // response.data.organization.repositories?.pageInfo?.hasNextPage == true

        return alerts
    }

    internal companion object {
        @Serializable
        data class GraphQlResponse(val data: Data)

        @Serializable
        data class Data(val organization: Organization)

        @Serializable
        data class Organization(val repositories: RepositoryNodes?, val teams: Teams?, val repository: VulnerabilityAlert?)

        @Serializable
        data class RepositoryNodes(val nodes: List<Repository>, val pageInfo: PageInfo)

        @Serializable
        data class Repository(val name: String, val pushedAt: String, val hasVulnerabilityAlertsEnabled: Boolean)

        @Serializable
        data class Teams(val nodes: List<Team>, val pageInfo: PageInfo)

        @Serializable
        data class Team(val name: String)

        @Serializable
        data class VulnerabilityAlert(val vulnerabilityAlerts: VulnerabilityAlertNode)

        @Serializable
        data class VulnerabilityAlertNode(
            val nodes: List<VulnerabilityAlertEntry>,
            val pageInfo: PageInfo
        ) {
            @Serializable
            data class VulnerabilityAlertEntry(
                val createdAt: String,
                val dependencyScope: String,
                val securityVulnerability: SecurityVulnerability
            )

        }

        @Serializable
        data class PageInfo(val endCursor: String, val startCursor: String, val hasNextPage: Boolean)

        @Serializable
        data class SecurityVulnerability(val severity: String)
    }
}