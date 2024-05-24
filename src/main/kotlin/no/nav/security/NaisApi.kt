package no.nav.security

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

class NaisApi(private val http: HttpClient) {
    private val baseUrl = "https://console.nav.cloud.nais.io/query"

    suspend fun adminsFor(repositories: List<GithubRepository>): List<IssueCountRecord> =
        repositories.map {IssueCountRecord(adminsFor(it.name), it.pushedAt, it.name, it.hasVulnerabilityAlertsEnabled, it.vulnerabilityAlerts, it.isArchived)}

    private suspend fun adminsFor(repoName: String?): List<String> {
        val repoFullName = "navikt/$repoName"
        val teams = mutableListOf<Team>()
        do {
            logger.info("looking for $repoName's owners")
            val response = performGqlRequest(repoFullName, 0) // there will never be more than one page
            teams += response.data.teams.nodes
        } while (response.data.teams.pageInfo.hasNextPage)

        return teams.map { it.slug }
    }

    private suspend fun performGqlRequest(repoFullName: String, offset: Int): GqlResponse {
        val queryString = """query(${"$"}filter: TeamsFilter, ${"$"}offset: Int, ${"$"}limit: Int) { 
                      teams(filter: ${"$"}filter, offset: ${"$"}offset, limit: ${"$"}limit) { 
                          nodes { 
                              slug 
                              slackChannel 
                          } 
                          pageInfo{ 
                              hasNextPage 
                          } 
                      } 
                  } """
        val reqBody = RequestBody(queryString.replace("\n", " "),
            Variables(Filter(GitHubFilter(repoFullName, "admin")), offset, 100)
        )

        return http.post(baseUrl) { setBody(reqBody) }.body<GqlResponse>()
    }
}

@Serializable
data class Variables(val filter: Filter, val offset: Int, val limit: Int)

@Serializable
data class Filter(val github: GitHubFilter)

@Serializable
data class GitHubFilter(val repoName: String, val permissionName: String)

@Serializable
data class RequestBody(val query: String, val variables: Variables)

@Serializable
data class GqlResponse(val data: GqlResponseData)

@Serializable
data class GqlResponseData(val teams: GqlResponseTeams)

@Serializable
data class GqlResponseTeams(val nodes: List<Team>, val pageInfo: PageInfo)

@Serializable
data class Team(val slug: String, val slackChannel: String)

@Serializable
data class PageInfo(val hasNextPage: Boolean)
