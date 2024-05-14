package no.nav.security

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.ktor.client.*
import java.net.URI

class GitHub(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://api.github.com/graphql"
) {

    private val records = mutableListOf<IssueCountRecord>()
    private val client = GraphQLKtorClient(
        url = URI(baseUrl).toURL(),
        httpClient = httpClient
    )

    suspend fun fetchStatsForBigQuery(): List<IssueCountRecord> {
        fetchDataFromGraphql()
        logger.info("Built ${records.size} records from GitHub response")
        return records
    }

    private tailrec suspend fun fetchDataFromGraphql(
        teamCursor: String? = null, repoCursor: String? = null, vulnCursor: String? = null
    ) {
        val ghQuery = FetchGithubStatsQuery(
            variables = FetchGithubStatsQuery.Variables(
                orgName = "navikt",
                teamCursor = teamCursor,
                repoCursor = repoCursor,
                vulnCursor = vulnCursor
            )
        )
        val response: GraphQLClientResponse<FetchGithubStatsQuery.Result> = client.execute(ghQuery)
        response.errors?.let {
            logger.error("Error fetching data from GitHub: $it")
            throw RuntimeException("Error fetching data from GitHub: $it")
        }

        if(response.data?.rateLimit?.remaining!! < 100) {
            logger.error("Rate limit is low: ${response.data?.rateLimit?.remaining}")
            throw RuntimeException("Rate limit is low (<100)")
        }

        response.data?.organization?.teams?.nodes?.forEach { team ->
            team?.repositories?.nodes?.forEach { repo ->
                repo?.let {
                    records.add(
                        IssueCountRecord(
                            teamName = team.name,
                            lastPush = repo.pushedAt.toString(),
                            repositoryName = repo.name,
                            vulnerabilityAlertsEnabled = repo.hasVulnerabilityAlertsEnabled,
                            vulnerabilityCount = repo.vulnerabilityAlerts?.nodes?.size ?: 0,
                            isArchived = repo.isArchived
                        )
                    )
                }

                val vulnPageInfo = repo?.vulnerabilityAlerts?.pageInfo
                val nextVulnPage = vulnPageInfo?.endCursor.takeIf { vulnPageInfo?.hasNextPage ?: false }

                if (nextVulnPage != null) {
                    return fetchDataFromGraphql(
                        teamCursor = teamCursor,
                        repoCursor = repoCursor,
                        vulnCursor = nextVulnPage
                    )
                }
            }

            val repoPageInfo = team?.repositories?.pageInfo
            val nextRepoPage = repoPageInfo?.endCursor.takeIf { repoPageInfo?.hasNextPage ?: false }

            if (nextRepoPage != null) {
                return fetchDataFromGraphql(teamCursor = teamCursor, repoCursor = nextRepoPage)
            }
        }

        val teamPageInfo = response.data?.organization?.teams?.pageInfo
        val nextTeamPage = teamPageInfo?.endCursor.takeIf { teamPageInfo?.hasNextPage ?: false }

        if (nextTeamPage != null) {
            fetchDataFromGraphql(teamCursor = nextTeamPage)
        }
    }
}