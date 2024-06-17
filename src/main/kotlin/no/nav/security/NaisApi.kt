package no.nav.security

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class NaisApi(private val http: HttpClient) {
    private val baseUrl = "https://console.nav.cloud.nais.io/query"

    suspend fun adminsFor(repositories: List<GithubRepository>): List<IssueCountRecord> {
        var iterations = 0
        val result = repositories.map {
            iterations++
            if (iterations % 100 == 0) {
                logger.info("Fetched $iterations repo owners from NAIS API")
            }
            IssueCountRecord(
                owners = adminsFor(it.name),
                lastPush = it.pushedAt,
                repositoryName = it.name,
                vulnerabilityAlertsEnabled = it.hasVulnerabilityAlertsEnabled,
                vulnerabilityCount = it.vulnerabilityAlerts,
                isArchived = it.isArchived,
                productArea = null
            )
        }

        return result
    }

    suspend fun updateRecordsWithDeploymentStatus(repositories: List<IssueCountRecord>) {
        var deployedApps = 0
        val listOfDeployments = createListOfRepoDeploymentStatus()
        repositories.forEach { record ->
            listOfDeployments.find { deployment -> deployment.repository == record.repositoryName }.let {
                record.isDeployed = it?.deployed ?: false
                record.deployDate = it?.created?.toString()
            }
            if (record.isDeployed) deployedApps++
        }
        logger.info("Found $deployedApps deployed apps")
    }

    private suspend fun adminsFor(repoName: String?): List<String> {
        val repoFullName = "navikt/$repoName"
        val response = fetchTeamWithAdminForRepo(repoFullName, 0)

        return response.data.teams.nodes.map { it.slug }
    }

    private suspend fun fetchTeamWithAdminForRepo(repoFullName: String, offset: Int): TeamsGqlResponse {
        val queryString = """query(${"$"}filter: TeamsFilter, ${"$"}offset: Int, ${"$"}limit: Int) {
                      teams(filter: ${"$"}filter, offset: ${"$"}offset, limit: ${"$"}limit) {
                          nodes {
                              slug 
                          }
                          pageInfo{ 
                              hasNextPage 
                          } 
                      }
                  }"""
        val reqBody = RequestBody(
            queryString.replace("\n", " "),
            Variables(Filter(GitHubFilter(repoFullName, "admin")), offset, 100)
        )

        return http.post(baseUrl) { setBody(reqBody) }.body<TeamsGqlResponse>()
    }

    private suspend fun createListOfRepoDeploymentStatus(): List<RepoDeploymentStatus> {
        val deployments = mutableListOf<RepoDeploymentStatus>()
        var offset = 0
        do {
            logger.info("looking for deployments at offset $offset")
            val response = fetchActiveDeploymentsWithRepo(offset)
            response.data.deployments.nodes.forEach { deployment ->
                deployments.add(
                    RepoDeploymentStatus(
                        deployment.repository.substringAfter("/"),
                        true,
                        Instant.parse(deployment.created.toString()).atZone(ZoneId.systemDefault()).toLocalDateTime()
                    )
                )
            }

            offset += 100
        } while (response.data.deployments.pageInfo.hasNextPage)
        return deployments
    }

    private suspend fun fetchActiveDeploymentsWithRepo(offset: Int): DeployGqlResponse {
        val queryString = """query(${"$"}offset: Int, ${"$"}limit: Int) { 
                      deployments(offset: ${"$"}offset, limit: ${"$"}limit) { 
                        nodes {
                          repository
                          created
                        }
                        pageInfo {
                          hasNextPage
                        }
                      } 
                  }"""
        val reqBody = RequestBody(
            queryString.replace("\n", " "),
            Variables(null, offset, null)
        )

        return http.post(baseUrl) { setBody(reqBody) }.body<DeployGqlResponse>()
    }
}

@Serializable
private data class Variables(val filter: Filter?, val offset: Int, val limit: Int?)

@Serializable
private data class Filter(val github: GitHubFilter)

@Serializable
private data class GitHubFilter(val repoName: String, val permissionName: String)

@Serializable
private data class RequestBody(val query: String, val variables: Variables)

@Serializable
private data class TeamsGqlResponse(val data: TeamsGqlResponseData)

@Serializable
private data class TeamsGqlResponseData(val teams: TeamsResponse)

@Serializable
private data class TeamsResponse(val nodes: List<Team>, val pageInfo: PageInfo)

@Serializable
private data class Team(val slug: String)

@Serializable
private data class DeployGqlResponse(val data: DeployGqlResponseData)

@Serializable
private data class DeployGqlResponseData(val deployments: DeployResponse)

@Serializable
private data class DeployResponse(val nodes: List<Deploy>, val pageInfo: PageInfo)

@Serializable
private data class Deploy(
    val repository: String,
    @Serializable(with = ZonedDateTimeSerializer::class) val created: ZonedDateTime,
)

private data class RepoDeploymentStatus(
    val repository: String,
    val deployed: Boolean,
    val created: LocalDateTime?
)

@Serializable
private data class PageInfo(val hasNextPage: Boolean)

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = ZonedDateTime::class)
object ZonedDateTimeSerializer : KSerializer<ZonedDateTime> {
    private val formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME

    override fun deserialize(decoder: Decoder): ZonedDateTime {
        return ZonedDateTime.parse(decoder.decodeString(), formatter)
    }

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: ZonedDateTime) {
        encoder.encodeString(value.format(formatter))
    }
}