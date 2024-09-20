package no.nav.security

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.security.bigquery.BQRepoStat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TeamcatalogTest {

    @Test
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
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
                                            Teamcatalog.ProductArea("2", "PO Platform"),
                                            Teamcatalog.ProductArea("3", "PO Test"),
                                            Teamcatalog.ProductArea("4", "PO Devs")
                                        )
                                    )
                                ), headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                            )
                        }
                        "/team?productAreaId=1&status=ACTIVE" -> {
                            respond(
                                Json.encodeToString(
                                    Teamcatalog.TeamResponse(
                                        listOf(Teamcatalog.TeamCatalogTeam(productAreaId = "1", name ="The Best AppSec", naisTeams = listOf("appsec")))
                                    )
                                ), headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                            )
                        }
                        "/team?productAreaId=2&status=ACTIVE" -> {
                            respond(
                                Json.encodeToString(
                                    Teamcatalog.TeamResponse(
                                        listOf(Teamcatalog.TeamCatalogTeam(productAreaId = "2", name ="NAIS Team", naisTeams = listOf("nais")))
                                    )
                                ), headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                            )
                        }
                        "/team?productAreaId=3&status=ACTIVE" -> {
                            respond(
                                Json.encodeToString(
                                    Teamcatalog.TeamResponse(
                                        listOf(Teamcatalog.TeamCatalogTeam(productAreaId = "3", name ="A-Team", naisTeams = emptyList()))
                                    )
                                ), headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                            )
                        }
                        "/team?productAreaId=4&status=ACTIVE" -> {
                            respond(
                                Json.encodeToString(
                                    Teamcatalog.TeamResponse(
                                        listOf(Teamcatalog.TeamCatalogTeam(productAreaId = "4", name ="B-Team", naisTeams = listOf("appsec")))
                                    )
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
            BQRepoStat(listOf("appsec"), "2022-01-01", "repo1", true, 1, false, null),
            BQRepoStat(listOf("nais"), "2022-01-01", "repo2", true, 1, false, null),
            BQRepoStat(emptyList(), "2022-01-01", "repo3", true, 1, false, null)
        )

        teamcatalog.updateRecordsWithProductAreasForTeams(teams)

        assertEquals("PO Devs", teams[0].productArea)
        assertEquals("PO Platform", teams[1].productArea)
        assertNull(teams[2].productArea)
    }
}