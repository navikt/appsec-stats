package no.nav.security

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NaisTeamStatsTest {

    private data class RepoNodeBuilder(val name: String) {
        fun toJson() = """{ "name": "$name" }"""
    }

    private data class TeamNodeBuilder(
        val slug: String,
        val coverage: Double = 75.0,
        val workloadTotalCount: Int = 3,
        val repositories: List<RepoNodeBuilder> = emptyList(),
        val repoHasNextPage: Boolean = false,
        val repoEndCursor: String? = null
    ) {
        fun toJson(): String = """
            {
              "slug": "$slug",
              "vulnerabilitySummary": {
                "coverage": $coverage
              },
              "workloads": {
                "pageInfo": {
                  "totalCount": $workloadTotalCount
                }
              },
              "repositories": {
                "nodes": [
                  ${repositories.joinToString(",\n") { it.toJson() }}
                ],
                "pageInfo": {
                  "hasNextPage": $repoHasNextPage,
                  "endCursor": ${if (repoEndCursor != null) "\"$repoEndCursor\"" else "null"}
                }
              }
            }
        """.trimIndent()
    }

    private data class TeamStatsResponseBuilder(
        val teams: List<TeamNodeBuilder> = emptyList(),
        val hasNextPage: Boolean = false,
        val startCursor: String? = null,
        val endCursor: String? = null
    ) {
        fun toJson(): String = """
            {
              "data": {
                "teams": {
                  "nodes": [
                    ${teams.joinToString(",\n") { it.toJson() }}
                  ],
                  "pageInfo": {
                    "hasNextPage": $hasNextPage,
                    "startCursor": ${if (startCursor != null) "\"$startCursor\"" else "null"},
                    "endCursor": ${if (endCursor != null) "\"$endCursor\"" else "null"}
                  }
                }
              }
            }
        """.trimIndent()
    }

    private fun createMockHttpClient(responses: List<String>): HttpClient {
        var responseIndex = 0
        return HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; explicitNulls = false })
            }
            engine {
                addHandler {
                    if (responseIndex < responses.size) {
                        val response = responses[responseIndex++]
                        respond(
                            content = ByteReadChannel(response),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    } else {
                        error("No more mock responses available")
                    }
                }
            }
        }
    }

    @Test
    fun `should map all NaisTeam fields correctly`() = runBlocking {
        val response = TeamStatsResponseBuilder(
            teams = listOf(
                TeamNodeBuilder(
                    slug = "team-alpha",
                    coverage = 85.5,
                    workloadTotalCount = 7,
                    repositories = listOf(
                        RepoNodeBuilder("navikt/service-a"),
                        RepoNodeBuilder("navikt/service-b")
                    )
                )
            )
        ).toJson()

        val naisApi = NaisApi(createMockHttpClient(listOf(response)))
        val result = naisApi.teamStats()

        assertEquals(1, result.size)
        val team = result.first()
        assertEquals("team-alpha", team.naisTeam)
        assertEquals(85, team.slsaCoverage)
        assertTrue(team.hasDeployedResources)
        assertTrue(team.hasGithubRepositories)
        assertEquals(2, team.repositories.size)
        assertTrue(team.repositories.contains("service-a"))
        assertTrue(team.repositories.contains("service-b"))
    }

    @Test
    fun `should strip org prefix from repository names`() = runBlocking {
        val response = TeamStatsResponseBuilder(
            teams = listOf(
                TeamNodeBuilder(
                    slug = "team-beta",
                    repositories = listOf(
                        RepoNodeBuilder("navikt/my-app"),
                        RepoNodeBuilder("navikt/another-app")
                    )
                )
            )
        ).toJson()

        val naisApi = NaisApi(createMockHttpClient(listOf(response)))
        val result = naisApi.teamStats()

        val team = result.first()
        assertTrue(team.repositories.contains("my-app"))
        assertTrue(team.repositories.contains("another-app"))
        assertFalse(team.repositories.any { it.contains("/") })
    }

    @Test
    fun `should set hasDeployedResources false when workload count is zero`() = runBlocking {
        val response = TeamStatsResponseBuilder(
            teams = listOf(
                TeamNodeBuilder(
                    slug = "empty-team",
                    workloadTotalCount = 0,
                    repositories = emptyList()
                )
            )
        ).toJson()

        val naisApi = NaisApi(createMockHttpClient(listOf(response)))
        val result = naisApi.teamStats()

        val team = result.first()
        assertFalse(team.hasDeployedResources)
        assertFalse(team.hasGithubRepositories)
        assertTrue(team.repositories.isEmpty())
    }

    @Test
    fun `should truncate coverage to integer`() = runBlocking {
        val response = TeamStatsResponseBuilder(
            teams = listOf(
                TeamNodeBuilder(slug = "team-coverage", coverage = 99.9)
            )
        ).toJson()

        val naisApi = NaisApi(createMockHttpClient(listOf(response)))
        val result = naisApi.teamStats()

        assertEquals(99, result.first().slsaCoverage)
    }

    @Test
    fun `should handle team pagination`() = runBlocking {
        val page1 = TeamStatsResponseBuilder(
            teams = listOf(TeamNodeBuilder(slug = "team-1", repositories = listOf(RepoNodeBuilder("navikt/repo-1")))),
            hasNextPage = true,
            endCursor = "cursor-xyz"
        ).toJson()

        val page2 = TeamStatsResponseBuilder(
            teams = listOf(TeamNodeBuilder(slug = "team-2", repositories = listOf(RepoNodeBuilder("navikt/repo-2"))))
        ).toJson()

        val naisApi = NaisApi(createMockHttpClient(listOf(page1, page2)))
        val result = naisApi.teamStats()

        assertEquals(2, result.size)
        assertTrue(result.any { it.naisTeam == "team-1" })
        assertTrue(result.any { it.naisTeam == "team-2" })
    }

    @Test
    fun `should handle repository pagination within a team`() = runBlocking {
        val page1 = TeamStatsResponseBuilder(
            teams = listOf(
                TeamNodeBuilder(
                    slug = "big-team",
                    repositories = listOf(RepoNodeBuilder("navikt/repo-page-1")),
                    repoHasNextPage = true,
                    repoEndCursor = "repo-cursor-1"
                )
            ),
            hasNextPage = false
        ).toJson()

        val page2 = TeamStatsResponseBuilder(
            teams = listOf(
                TeamNodeBuilder(
                    slug = "big-team",
                    repositories = listOf(RepoNodeBuilder("navikt/repo-page-2")),
                    repoHasNextPage = false
                )
            ),
            hasNextPage = false
        ).toJson()

        val naisApi = NaisApi(createMockHttpClient(listOf(page1, page2)))
        val result = naisApi.teamStats()

        val team = result.find { it.naisTeam == "big-team" }!!
        assertTrue(team.repositories.contains("repo-page-1"))
        assertTrue(team.repositories.contains("repo-page-2"))
    }

    @Test
    fun `should return empty set for empty response`() = runBlocking {
        val response = TeamStatsResponseBuilder().toJson()

        val naisApi = NaisApi(createMockHttpClient(listOf(response)))
        val result = naisApi.teamStats()

        assertTrue(result.isEmpty())
    }
}
