package no.nav.security

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpHeaders.UserAgent
import kotlinx.serialization.Serializable

class GitHub(private val http: HttpClient, private val authToken: String) {

    private val baseUrl = "https://api.github.com/graphql"

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

    private suspend fun fetchTeams(): List<String> {
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
            val response = http.post(baseUrl) {
                header(Authorization, "Bearer $authToken")
                header(UserAgent, "NAV IT Appsec-stats")
                header(ContentType, Json)
                setBody(fetchTeamsReqBody(offset).replace("\n", " "))
            }.body<GraphQlResponse>()
            offset = response.data.organization.teams.pageInfo.endCursor
            teams.plus(response.data.organization.teams.nodes)
        } while (response.data.organization.teams.pageInfo.hasNextPage)

        return teams
    }

    private suspend fun fetchRepositories(): List<Repository> {
        fun fetchRepositoryReqBody(after: String?) = """
             query {
              organization(login: "navikt") {
                repositories(first: 100, after: $after) {
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
                header(Authorization, "Bearer $authToken")
                header(UserAgent, "NAV IT Appsec-stats")
                header(ContentType, Json)
                setBody(fetchRepositoryReqBody(offset).replace("\n", " "))
            }.body<GraphQlResponse>()
            offset = response.data.organization.teams.pageInfo.endCursor
            repositories.addAll(response.data.organization.repositories.nodes)
        } while (response.data.organization.teams.pageInfo.hasNextPage)

        return repositories
    }

    private suspend fun fetchVulnerabilityAlertsForRepo(repoName: String): List<VulnerabilityAlertNode> {
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

        val alerts = mutableListOf<VulnerabilityAlertNode>()
        var offset: String? = null

        do {
            val response = http.post(baseUrl) {
                header(Authorization, "Bearer $authToken")
                header(UserAgent, "NAV IT Appsec-stats")
                header(ContentType, Json)
                setBody(fetchAlertsForTeamquery(repoName, offset).replace("\n", " "))
            }.body<GraphQlResponse>()
            offset = response.data.organization.repository.pageInfo.endCursor
            alerts.addAll(response.data.organization.repository.nodes)
        } while (response.data.organization.repositories.pageInfo.hasNextPage)

        return alerts
    }
}

@Serializable
data class GraphQlResponse(val data: Data)

@Serializable
data class Data(val organization: Organization)

@Serializable
data class Organization(val repositories: RepositoryNodes, val teams: Teams, val repository: VulnerabilityAlert)

@Serializable
data class RepositoryNodes(val nodes: List<Repository>, val pageInfo: PageInfo)

@Serializable
data class Repository(val name: String, val pushedAt: String, val hasVulnerabilityAlertsEnabled: Boolean)

@Serializable
data class Teams(val nodes: List<Team>, val pageInfo: PageInfo)

@Serializable
data class Team(val name: String)

@Serializable
data class VulnerabilityAlert(val nodes: List<VulnerabilityAlertNode>, val pageInfo: PageInfo)

@Serializable
data class VulnerabilityAlertNode(
    val createdAt: String,
    val dependencyScope: String,
    val securityVulnerability: SecurityVulnerability
)

@Serializable
data class PageInfo(val endCursor: String, val startCursor: String, val hasNextPage: Boolean)

@Serializable
data class SecurityVulnerability(val severity: String)
