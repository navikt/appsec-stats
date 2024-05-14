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
        logger.info("Built ${records.size} records from GitHub response")
        fetchDataFromGraphql()
        return records
    }

    private tailrec suspend fun fetchDataFromGraphql(
        teamCursor: String? = null, repoCursor: String? = null, vulnCursor: String? = null
    ): List<IssueCountRecord>? {
        val ghQuery = FetchGithubStatsQuery(
            variables = FetchGithubStatsQuery.Variables(orgName = "navikt", teamCursor = teamCursor, repoCursor = repoCursor, vulnCursor = vulnCursor)
        )
        val response: GraphQLClientResponse<FetchGithubStatsQuery.Result> = client.execute(ghQuery)

        response.errors?.let {
            logger.error("Error fetching data from GitHub: $it")
            throw RuntimeException("Error fetching data from GitHub: $it")
        }

        response.data?.organization?.teams?.nodes?.map { team ->
            team?.repositories?.nodes?.map { repo ->
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
            }
        }

        val lastTeam = response.data?.organization?.teams?.nodes?.lastOrNull()?.name
        val lastRepo = response.data?.organization?.teams?.nodes?.find { it?.name == lastTeam }?.repositories?.nodes?.lastOrNull()?.name

        val haveNextTeamPage = response.fetchTeamsCursor()
        val haveNextRepoPage = lastTeam?.let { response.fetchReposCursor(it) }
        val haveNextVulnPage = lastTeam?.let { lastRepo?.let { response.fetchVulnCursor(lastTeam, lastRepo) } }

        // If any of the cursors are not null, there are more pages to fetch
        // If we have vulnCursor we use that, if not we use repoCursor, if not we use teamCursor
        val nextVulnPage = haveNextVulnPage
        val nextRepoPage = if(haveNextVulnPage == null) haveNextRepoPage else null
        val nextTeamPage = if(haveNextVulnPage == null && haveNextRepoPage == null) haveNextTeamPage else null

        val hasNextPage = nextVulnPage != null || nextRepoPage != null || nextTeamPage != null

        return if(hasNextPage) fetchDataFromGraphql(teamCursor = nextTeamPage, repoCursor = nextRepoPage, vulnCursor = nextVulnPage) else null
    }

    private fun GraphQLClientResponse<FetchGithubStatsQuery.Result>.fetchTeamsCursor(): String? =
        data?.organization?.teams?.pageInfo?.let { page ->
                if(page.hasNextPage) page.endCursor
                else null
            }

    private fun GraphQLClientResponse<FetchGithubStatsQuery.Result>.fetchReposCursor(repo: String): String? =
        data?.organization?.teams?.nodes
            ?.find { it?.name == repo }?.repositories?.pageInfo?.let {page ->
                if(page.hasNextPage) page.endCursor
                else null
            }

    private fun GraphQLClientResponse<FetchGithubStatsQuery.Result>.fetchVulnCursor(
        team: String,
        repo: String
    ): String? =
        data?.organization?.teams?.nodes
            ?.find { it?.name == team }?.repositories?.nodes
            ?.find { it?.name == repo }?.vulnerabilityAlerts?.pageInfo?.let { page ->
                if(page.hasNextPage) page.endCursor
                else null
            }
}