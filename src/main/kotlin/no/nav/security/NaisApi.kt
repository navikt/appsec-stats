package no.nav.security

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable
import java.lang.RuntimeException

class NaisApi(private val http: HttpClient) {
    private val baseUrl = "https://console.nav.cloud.nais.io/query"

    suspend fun adminsFor(repositories: List<GithubRepository>): List<RepositoryWithOwner> =
        repositories.map { RepositoryWithOwner(it, adminsFor(it.name ?: throw RuntimeException("Repo without name does not COMPUTE!"))) }

    private suspend fun adminsFor(repoName: String?): List<String> {
        val repoFullName = "navikt/$repoName"
        logger.info("Looking for $repoFullName's admins")
        val teams = mutableListOf<Team>()
        val offset = 0
        do {
            val response = performGqlRequest(repoFullName, offset)
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
