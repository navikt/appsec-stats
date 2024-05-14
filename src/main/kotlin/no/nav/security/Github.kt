package no.nav.security

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.ktor.client.*
import java.net.URI

class GitHub(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://api.github.com/graphql"
) {

    val client = GraphQLKtorClient(
        url = URI(baseUrl).toURL(),
        httpClient = httpClient
    )

    suspend fun fetchStatsForBigQuery(): List<IssueCountRecord> {
        val ghQuery = FetchGithubStatsQuery(variables = FetchGithubStatsQuery.Variables(orgName = "navikt"))
        val response: GraphQLClientResponse<FetchGithubStatsQuery.Result> = client.execute(ghQuery)
        response.errors?.let {
            logger.error("Error fetching data from GitHub: $it")
            throw RuntimeException("Error fetching data from GitHub: $it")
        }

        val records = mutableListOf<IssueCountRecord>()

        response.data?.organization?.teams?.nodes?.map { team ->
            team?.repositories?.nodes?.map { repo ->
                repo?.let {
                    if(!it.isArchived) {
                        records.add(
                            IssueCountRecord(
                                teamName = team.name,
                                lastPush = repo.pushedAt.toString(),
                                repositoryName = repo.name,
                                vulnerabilityAlertsEnabled = repo.hasVulnerabilityAlertsEnabled,
                                vulnerabilityCount = repo.vulnerabilityAlerts?.nodes?.size?: 0
                            )
                        )
                    }
                }
            }
        }

        logger.info("Fetched ${records.size} records from GitHub")

        return records
    }
}