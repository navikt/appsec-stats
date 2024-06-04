package no.nav.security

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TeamcatalogTest {

    @Test
    fun `updateRecordsWithProductAreasForTeams should update teams with product areas`() = runBlocking {
        val httpClient = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(json = Json {
                    explicitNulls = false
                    ignoreUnknownKeys = true
                })
            }
            engine {
                addHandler { request ->
                    when (request.url.fullPath) {
                        "/productarea?status=ACTIVE" -> {
                            respond(
                                Json.encodeToString(
                                    Teamcatalog.ProductAreaResponse(
                                        listOf(
                                            Teamcatalog.ProductArea("1", "PO AppSec"),
                                            Teamcatalog.ProductArea("2", "PO Platform")
                                        )
                                    )
                                ), headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                            )
                        }
                        "/productArea/1" -> {
                            respond(
                                Json.encodeToString(
                                    Teamcatalog.TeamResponse(id= "1", name ="The Best AppSec", naisTeams = listOf("appsec"))
                                ), headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                            )
                        }
                        "/productArea/2" -> {
                            respond(
                                Json.encodeToString(
                                    Teamcatalog.TeamResponse(id = "2", name = "NAIS Team", naisTeams = listOf("nais"))
                                ), headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                            )
                        }
                        else -> error("Unhandled ${request.url.fullPath}")
                    }
                }
            }
        }

        val teamcatalog = Teamcatalog(httpClient)
        val teams = listOf(
            IssueCountRecord(listOf("appsec"), "2022-01-01", "repo1", true, 1, false, null),
            IssueCountRecord(listOf("nais"), "2022-01-01", "repo2", true, 1, false, null)
        )

        teamcatalog.updateRecordsWithProductAreasForTeams(teams)

        assertEquals("PO AppSec", teams[0].productArea)
        assertEquals("PO Platform", teams[1].productArea)
    }
}