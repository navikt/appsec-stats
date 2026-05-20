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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitHubRepositoriesTest {

    private data class RepositoryNodeBuilder(
        val name: String,
        val nameWithOwner: String = "navikt/$name",
        val isArchived: Boolean = false,
        val pushedAt: String? = "2024-03-15T12:00:00Z",
        val hasVulnerabilityAlertsEnabled: Boolean = true,
        val vulnerabilityAlertsTotalCount: Int = 0
    ) {
        fun toJson(): String = """
            {
              "name": "$name",
              "nameWithOwner": "$nameWithOwner",
              "isArchived": $isArchived,
              "pushedAt": ${if (pushedAt != null) "\"$pushedAt\"" else "null"},
              "hasVulnerabilityAlertsEnabled": $hasVulnerabilityAlertsEnabled,
              "vulnerabilityAlerts": {
                "totalCount": $vulnerabilityAlertsTotalCount
              }
            }
        """.trimIndent()
    }

    private data class RepositoriesResponseBuilder(
        val nodes: List<RepositoryNodeBuilder> = emptyList(),
        val hasNextPage: Boolean = false,
        val endCursor: String? = null
    ) {
        fun toJson(): String = """
            {
              "data": {
                "rateLimit": {
                  "remaining": 4999,
                  "limit": 5000,
                  "resetAt": "2024-03-15T13:00:00Z"
                },
                "organization": {
                  "repositories": {
                    "totalCount": ${nodes.size},
                    "nodes": [
                      ${nodes.joinToString(",\n") { it.toJson() }}
                    ],
                    "pageInfo": {
                      "hasNextPage": $hasNextPage,
                      "endCursor": ${if (endCursor != null) "\"$endCursor\"" else "null"}
                    }
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
    fun `should map all repository fields correctly`() = runBlocking {
        val response = RepositoriesResponseBuilder(
            nodes = listOf(
                RepositoryNodeBuilder(
                    name = "my-service",
                    nameWithOwner = "navikt/my-service",
                    isArchived = false,
                    pushedAt = "2024-03-15T12:00:00Z",
                    hasVulnerabilityAlertsEnabled = true,
                    vulnerabilityAlertsTotalCount = 5
                )
            )
        ).toJson()

        val github = GitHub(createMockHttpClient(listOf(response)))
        val result = github.fetchOrgRepositories()

        assertEquals(1, result.size)
        val repo = result[0]
        assertEquals("my-service", repo.name)
        assertEquals("navikt/my-service", repo.nameWithOwner)
        assertFalse(repo.isArchived)
        assertEquals("2024-03-15T12:00:00Z", repo.pushedAt)
        assertTrue(repo.hasVulnerabilityAlertsEnabled)
        assertEquals(5, repo.vulnerabilityAlerts)
    }

    @Test
    fun `should map archived repository correctly`() = runBlocking {
        val response = RepositoriesResponseBuilder(
            nodes = listOf(
                RepositoryNodeBuilder(
                    name = "old-service",
                    isArchived = true,
                    hasVulnerabilityAlertsEnabled = false,
                    vulnerabilityAlertsTotalCount = 0
                )
            )
        ).toJson()

        val github = GitHub(createMockHttpClient(listOf(response)))
        val result = github.fetchOrgRepositories()

        assertEquals(1, result.size)
        val repo = result[0]
        assertEquals("old-service", repo.name)
        assertTrue(repo.isArchived)
        assertFalse(repo.hasVulnerabilityAlertsEnabled)
        assertEquals(0, repo.vulnerabilityAlerts)
    }

    @Test
    fun `should map null pushedAt correctly`() = runBlocking {
        val response = RepositoriesResponseBuilder(
            nodes = listOf(
                RepositoryNodeBuilder(name = "never-pushed", pushedAt = null)
            )
        ).toJson()

        val github = GitHub(createMockHttpClient(listOf(response)))
        val result = github.fetchOrgRepositories()

        assertEquals(1, result.size)
        assertNull(result[0].pushedAt)
    }

    @Test
    fun `should handle repository pagination`() = runBlocking {
        val page1 = RepositoriesResponseBuilder(
            nodes = listOf(RepositoryNodeBuilder(name = "repo-page-1")),
            hasNextPage = true,
            endCursor = "cursor-abc"
        ).toJson()

        val page2 = RepositoriesResponseBuilder(
            nodes = listOf(RepositoryNodeBuilder(name = "repo-page-2"))
        ).toJson()

        val github = GitHub(createMockHttpClient(listOf(page1, page2)))
        val result = github.fetchOrgRepositories()

        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "repo-page-1" })
        assertTrue(result.any { it.name == "repo-page-2" })
    }

    @Test
    fun `should return empty list for empty response`() = runBlocking {
        val response = RepositoriesResponseBuilder().toJson()

        val github = GitHub(createMockHttpClient(listOf(response)))
        val result = github.fetchOrgRepositories()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `should preserve nameWithOwner distinct from name`() = runBlocking {
        val response = RepositoriesResponseBuilder(
            nodes = listOf(
                RepositoryNodeBuilder(
                    name = "appsec-stats",
                    nameWithOwner = "navikt/appsec-stats"
                )
            )
        ).toJson()

        val github = GitHub(createMockHttpClient(listOf(response)))
        val result = github.fetchOrgRepositories()

        assertEquals(1, result.size)
        assertEquals("appsec-stats", result[0].name)
        assertEquals("navikt/appsec-stats", result[0].nameWithOwner)
    }

    @Test
    fun `should map multiple repositories in a single response`() = runBlocking {
        val response = RepositoriesResponseBuilder(
            nodes = listOf(
                RepositoryNodeBuilder(name = "service-a", vulnerabilityAlertsTotalCount = 3),
                RepositoryNodeBuilder(name = "service-b", isArchived = true, vulnerabilityAlertsTotalCount = 0),
                RepositoryNodeBuilder(name = "service-c", hasVulnerabilityAlertsEnabled = false, vulnerabilityAlertsTotalCount = 1)
            )
        ).toJson()

        val github = GitHub(createMockHttpClient(listOf(response)))
        val result = github.fetchOrgRepositories()

        assertEquals(3, result.size)
        assertEquals(3, result.find { it.name == "service-a" }?.vulnerabilityAlerts)
        assertTrue(result.find { it.name == "service-b" }?.isArchived == true)
        assertFalse(result.find { it.name == "service-c" }?.hasVulnerabilityAlertsEnabled == true)
    }
}
