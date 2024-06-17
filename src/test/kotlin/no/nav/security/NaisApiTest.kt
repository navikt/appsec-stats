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

class NaisApiTest {

    @Test
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
            IssueCountRecord(listOf("owner1"), null, "foo", true, 1, false, null),
            IssueCountRecord(listOf("owner2"), null, "bar", true, 1, false, null)
        )

        naisApi.updateRecordsWithDeploymentStatus(repositories)

        assertEquals(false, repositories[0].isDeployed)
        assertEquals(null, repositories[0].deployDate)

        assertEquals(true, repositories[1].isDeployed)
        assertEquals("2024-06-13T09:17:33.396884", repositories[1].deployDate)
    }

    companion object {
        private val mockResponse = """
            {
              "data":{
                "deployments":{
                  "nodes":[
                    {
                      "repository":"appsec/foo",
                      "statuses":[
                        {
                          "created":"2024-06-13T09:17:26.233846Z",
                          "status":"in_progress"
                        },
                        {
                          "created":"2024-06-13T09:16:45.919573Z",
                          "status":"queued"
                        }
                      ]
                    },
                    {
                      "repository":"appsec/bar",
                      "statuses":[
                        {
                          "created":"2024-06-13T09:17:33.396884Z",
                          "status":"success"
                        },
                        {
                          "created":"2024-06-13T09:17:33.396658Z",
                          "status":"in_progress"
                        },
                        {
                          "created":"2024-02-27T12:16:31.073059Z",
                          "status":"in_progress"
                        },
                        {
                          "created":"2023-05-13T09:17:17.328088Z",
                          "status":"success"
                        }
                      ]
                    },
                    {
                      "repository":"appsec/appsec",
                      "statuses":[
                        {
                          "created":"2024-06-13T09:17:18.633794Z",
                          "status":"success"
                        },
                        {
                          "created":"2024-06-13T09:17:07.999223Z",
                          "status":"in_progress"
                        },
                        {
                          "created":"2024-06-13T09:16:46.525771Z",
                          "status":"success"
                        },
                        {
                          "created":"2024-06-13T09:16:46.394207Z",
                          "status":"in_progress"
                        },
                        {
                          "created":"2024-06-13T09:16:42.928174Z",
                          "status":"success"
                        },
                        {
                          "created":"2024-06-13T09:16:42.824505Z",
                          "status":"queued"
                        }
                      ]
                    }
                  ],
                  "pageInfo":{
                    "hasNextPage":false
                  }
                }
              }
            }
        """
    }
}