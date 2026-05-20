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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NaisDeploymentsTest {

    private fun environmentsResponse(vararg names: String): String = """
        {
          "data": {
            "environments": {
              "nodes": [
                ${names.joinToString(",\n") { """{ "name": "$it" }""" }}
              ]
            }
          }
        }
    """.trimIndent()

    private data class DeploymentNodeBuilder(
        val repository: String?,
        val createdAt: String = "2024-03-15T12:00:00Z"
    ) {
        fun toJson(): String = """
            {
              "repository": ${if (repository != null) "\"$repository\"" else "null"},
              "createdAt": "$createdAt"
            }
        """.trimIndent()
    }

    private sealed class WorkloadBuilder {
        abstract fun toJson(): String

        data class ApplicationBuilder(
            val deployments: List<DeploymentNodeBuilder> = emptyList()
        ) : WorkloadBuilder() {
            override fun toJson(): String = """
                {
                  "__typename": "Application",
                  "deployments": {
                    "nodes": [
                      ${deployments.joinToString(",\n") { it.toJson() }}
                    ]
                  }
                }
            """.trimIndent()
        }

        data class JobBuilder(
            val deployments: List<DeploymentNodeBuilder> = emptyList()
        ) : WorkloadBuilder() {
            override fun toJson(): String = """
                {
                  "__typename": "Job",
                  "deployments": {
                    "nodes": [
                      ${deployments.joinToString(",\n") { it.toJson() }}
                    ]
                  }
                }
            """.trimIndent()
        }
    }

    private data class DeploymentsResponseBuilder(
        val workloads: List<WorkloadBuilder> = emptyList(),
        val hasNextPage: Boolean = false,
        val endCursor: String? = null
    ) {
        fun toJson(): String = """
            {
              "data": {
                "environment": {
                  "workloads": {
                    "nodes": [
                      ${workloads.joinToString(",\n") { it.toJson() }}
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
    fun `should map all NaisDeployment fields from Application workload`() = runBlocking {
        val responses = listOf(
            environmentsResponse("prod-gcp"),
            DeploymentsResponseBuilder(
                workloads = listOf(
                    WorkloadBuilder.ApplicationBuilder(
                        deployments = listOf(
                            DeploymentNodeBuilder(
                                repository = "navikt/my-service",
                                createdAt = "2024-03-15T12:00:00Z"
                            )
                        )
                    )
                )
            ).toJson()
        )

        val naisApi = NaisApi(createMockHttpClient(responses))
        val result = naisApi.deployments()

        assertEquals(1, result.size)
        val deployment = result.first()
        assertEquals("prod-gcp", deployment.environment)
        assertEquals("navikt/my-service", deployment.repository)
        assertEquals("2024-03-15T12:00:00Z", deployment.createdAt)
    }

    @Test
    fun `should map NaisDeployment from Job workload`() = runBlocking {
        val responses = listOf(
            environmentsResponse("dev-gcp"),
            DeploymentsResponseBuilder(
                workloads = listOf(
                    WorkloadBuilder.JobBuilder(
                        deployments = listOf(
                            DeploymentNodeBuilder(
                                repository = "navikt/my-job",
                                createdAt = "2024-01-10T08:30:00Z"
                            )
                        )
                    )
                )
            ).toJson()
        )

        val naisApi = NaisApi(createMockHttpClient(responses))
        val result = naisApi.deployments()

        assertEquals(1, result.size)
        val deployment = result.first()
        assertEquals("dev-gcp", deployment.environment)
        assertEquals("navikt/my-job", deployment.repository)
        assertEquals("2024-01-10T08:30:00Z", deployment.createdAt)
    }

    @Test
    fun `should skip deployments with null or empty repository`() = runBlocking {
        val responses = listOf(
            environmentsResponse("prod-gcp"),
            DeploymentsResponseBuilder(
                workloads = listOf(
                    WorkloadBuilder.ApplicationBuilder(
                        deployments = listOf(
                            DeploymentNodeBuilder(repository = null),
                            DeploymentNodeBuilder(repository = ""),
                            DeploymentNodeBuilder(repository = "navikt/valid-repo")
                        )
                    )
                )
            ).toJson()
        )

        val naisApi = NaisApi(createMockHttpClient(responses))
        val result = naisApi.deployments()

        assertEquals(1, result.size)
        assertEquals("navikt/valid-repo", result.first().repository)
    }

    @Test
    fun `should collect deployments from multiple environments`() = runBlocking {
        val responses = listOf(
            environmentsResponse("prod-gcp", "dev-gcp"),
            DeploymentsResponseBuilder(
                workloads = listOf(
                    WorkloadBuilder.ApplicationBuilder(
                        deployments = listOf(DeploymentNodeBuilder(repository = "navikt/service-a"))
                    )
                )
            ).toJson(),
            DeploymentsResponseBuilder(
                workloads = listOf(
                    WorkloadBuilder.ApplicationBuilder(
                        deployments = listOf(DeploymentNodeBuilder(repository = "navikt/service-b"))
                    )
                )
            ).toJson()
        )

        val naisApi = NaisApi(createMockHttpClient(responses))
        val result = naisApi.deployments()

        assertEquals(2, result.size)
        assertTrue(result.any { it.environment == "prod-gcp" && it.repository == "navikt/service-a" })
        assertTrue(result.any { it.environment == "dev-gcp" && it.repository == "navikt/service-b" })
    }

    @Test
    fun `should handle workload pagination`() = runBlocking {
        val responses = listOf(
            environmentsResponse("prod-gcp"),
            DeploymentsResponseBuilder(
                workloads = listOf(
                    WorkloadBuilder.ApplicationBuilder(
                        deployments = listOf(DeploymentNodeBuilder(repository = "navikt/service-page-1"))
                    )
                ),
                hasNextPage = true,
                endCursor = "cursor-abc"
            ).toJson(),
            DeploymentsResponseBuilder(
                workloads = listOf(
                    WorkloadBuilder.ApplicationBuilder(
                        deployments = listOf(DeploymentNodeBuilder(repository = "navikt/service-page-2"))
                    )
                )
            ).toJson()
        )

        val naisApi = NaisApi(createMockHttpClient(responses))
        val result = naisApi.deployments()

        assertEquals(2, result.size)
        assertTrue(result.any { it.repository == "navikt/service-page-1" })
        assertTrue(result.any { it.repository == "navikt/service-page-2" })
    }

    @Test
    fun `should handle mixed Application and Job workloads`() = runBlocking {
        val responses = listOf(
            environmentsResponse("prod-gcp"),
            DeploymentsResponseBuilder(
                workloads = listOf(
                    WorkloadBuilder.ApplicationBuilder(
                        deployments = listOf(DeploymentNodeBuilder(repository = "navikt/my-app"))
                    ),
                    WorkloadBuilder.JobBuilder(
                        deployments = listOf(DeploymentNodeBuilder(repository = "navikt/my-job"))
                    )
                )
            ).toJson()
        )

        val naisApi = NaisApi(createMockHttpClient(responses))
        val result = naisApi.deployments()

        assertEquals(2, result.size)
        assertTrue(result.any { it.repository == "navikt/my-app" })
        assertTrue(result.any { it.repository == "navikt/my-job" })
    }

    @Test
    fun `should return empty set when no environments exist`() = runBlocking {
        val responses = listOf(environmentsResponse())

        val naisApi = NaisApi(createMockHttpClient(responses))
        val result = naisApi.deployments()

        assertTrue(result.isEmpty())
    }
}
