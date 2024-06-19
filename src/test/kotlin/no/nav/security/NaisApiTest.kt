package no.nav.security

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class NaisApiTest {

    @Test
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun `updateRecordsWithDeploymentStatus should update deployment status for repositories`() = runBlocking {
        val httpClient = HttpClient(MockEngine) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(json = Json {
                    explicitNulls = false
                    ignoreUnknownKeys = true
                })
            }
            defaultRequest {
                headers {
                    header(HttpHeaders.Accept, ContentType.Application.Json)
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    header(HttpHeaders.UserAgent, "NAV IT McBotFace")
                }
            }
            engine {
                addHandler { request ->
                    when (request.url.fullPath) {
                        "/query" -> {
                            respond(
                                mockResponse, headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                            )
                        }
                        else -> error("Unhandled ${request.url.fullPath}")
                    }
                }
            }
        }

        val naisApi = NaisApi(httpClient)
        val repositories = listOf(
            GithubRepository(id = "1", name = "appsec", isArchived = false, pushedAt = null, hasVulnerabilityAlertsEnabled = false, vulnerabilityAlerts = 0),
            GithubRepository(id = "2", name = "foo", isArchived = false, pushedAt = null, hasVulnerabilityAlertsEnabled = false, vulnerabilityAlerts = 0),
            GithubRepository(id = "3", name = "bar", isArchived = false, pushedAt = null, hasVulnerabilityAlertsEnabled = false, vulnerabilityAlerts = 0),
        )

        val repos = naisApi.adminAndDeployInfoFor(repositories)

        assertEquals(3, repos.size)

        assertEquals(4, repos[0].owners.size) // Query only returns teams with admin access to repo
        assertEquals("appsec", repos[0].repositoryName)

        assertEquals("foo", repos[1].repositoryName)
    }

    companion object {
        private val mockResponse = """
            {
              "data": {
                "teams": {
                  "nodes": [
                    {
                      "slug": "foo",
                      "deployments": {
                        "nodes": [],
                        "pageInfo": {
                          "hasNextPage": false
                        }
                      }
                    },
                    {
                      "slug": "appsec",
                      "deployments": {
                        "nodes": [
                          {
                            "created": "2024-01-24T14:42:33.66081Z",
                            "repository": "navikt/appsec"
                            "env": "prod-abc"
                          },
                          {
                            "created": "2023-01-23T14:42:33.063166Z",
                            "repository": "navikt/appsec"
                            "env": "prod-abc"
                          }
                        ],
                        "pageInfo": {
                          "hasNextPage": false
                        }
                      }
                    },
                    {
                      "slug": "bar",
                      "deployments": {
                        "nodes": [],
                        "pageInfo": {
                          "hasNextPage": false
                        }
                      }
                    },
                    {
                      "slug": "meepmeep",
                      "deployments": {
                        "nodes": [],
                        "pageInfo": {
                          "hasNextPage": false
                        }
                      }
                    }
                  ],
                  "pageInfo": {
                    "hasNextPage": false
                  }
                }
              }
            }
        """
    }
}