package no.nav.security

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger("appsec-stats")

fun main() = runBlocking {
    val bq = BigQuery(requiredFromEnv("GCP_TEAM_PROJECT_ID"))
    val github = GitHub(httpClient())

    val githubStats = github.fetchStatsForBigQuery()

    logger.info("Fetched ${githubStats.size} records from GitHub")
    val rows = bq.insert(githubStats)
    logger.info("Inserted $rows records into BigQuery")
}

@OptIn(ExperimentalSerializationApi::class)
internal fun httpClient() = HttpClient(CIO) {
    expectSuccess = true
    install(Logging) {
        logger = logger
        level = LogLevel.HEADERS
        sanitizeHeader { header -> header == HttpHeaders.Authorization }
    }
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 5)
        exponentialDelay()
    }
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
            header(HttpHeaders.Authorization, "Bearer ${requiredFromEnv("GITHUB_TOKEN")}")
        }
    }
}

private fun requiredFromEnv(name: String) =
    System.getProperty(name)
        ?: System.getenv(name)
        ?: throw RuntimeException("unable to find '$name' in environment")
