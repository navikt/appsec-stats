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
            IssueCountRecord(listOf("owner1"), null, "foo", true, 1, false, null),
            IssueCountRecord(listOf("owner2"), null, "bar", true, 1, false, null),
            IssueCountRecord(listOf("owner3"), null, "appsec", true, 1, false, null)
        )

        naisApi.updateRecordsWithDeploymentStatus(repositories)

        assertEquals(3, repositories.size)

        assertEquals(false, repositories[0].isDeployed)
        assertEquals(null, repositories[0].deployDate)
        assertEquals("foo", repositories[0].repositoryName)

        assertEquals(true, repositories[1].isDeployed)
        val expectedDate = Instant.parse("2024-06-13T09:17:33.396658Z").atZone(ZoneId.systemDefault()).toLocalDateTime().toString()
        assertEquals(expectedDate, repositories[1].deployDate)
        assertEquals("bar", repositories[1].repositoryName)

        assertEquals(true, repositories[2].isDeployed)
        assertEquals("appsec", repositories[2].repositoryName)
    }

    companion object {
        private val mockResponse = """
            {
              "data":{
                "deployments":{
                  "nodes":[
                    {
                      "repository":"appsec/bar",
                      "created":"2024-06-13T09:17:33.396658Z"
                    },
                    {
                      "repository":"appsec/appsec",
                      "created":"2024-06-13T09:17:18.633794Z"
                    },
                    {
                      "repository":"appsec/definitelynotappsec",
                      "created":"2024-06-13T09:16:42.824505Z"
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