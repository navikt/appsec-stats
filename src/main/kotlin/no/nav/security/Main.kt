package no.nav.security

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.UserAgent
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger("appsec-stats")

fun main(): Unit = runBlocking {
    val bq = BigQuery(requiredFromEnv("GCP_TEAM_PROJECT_ID"))
    val github = GitHub(httpClient = httpClient(requiredFromEnv("GITHUB_TOKEN")))
    val naisApi = NaisApi(httpClient = httpClient(requiredFromEnv("NAIS_API_TOKEN")))
    val slack = Slack(httpClient = httpClient(null), slackWebhookUrl = requiredFromEnv("SLACK_WEBHOOK"))
    val teamcatalog = Teamcatalog(httpClient = httpClient(null))

    logger.info("Looking for GitHub repos")
    val githubRepositories = github.fetchOrgRepositories()
    logger.info("Fetched ${githubRepositories.size} repositories from GitHub")

    val repositoryWithOwners = naisApi.adminsFor(githubRepositories)
    logger.info("Fetched ${repositoryWithOwners.size} repo owners from NAIS API")

    teamcatalog.updateRecordsWithProductAreasForTeams(repositoryWithOwners)
    try {
        naisApi.updateRecordsWithDeploymentStatus(repositoryWithOwners)
    } catch (e: Exception) {
        logger.info("Klarte ikke å oppdatere deploy status for repoer: ${e.message}")
    }

    bq.insert(repositoryWithOwners).fold(
        { rowCount -> logger.info("Inserted $rowCount rows into BigQuery") },
        { ex -> slack.send(
            channel = "appsec-aktivitet",
            heading = "GitHub Security Stats",
            msg = "Insert to BigQuery failed: ${ex.message}"
        ) }
    )
}

@OptIn(ExperimentalSerializationApi::class)
internal fun httpClient(authToken: String?) = HttpClient(CIO) {
    expectSuccess = true
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
            authToken?.let { header(HttpHeaders.Authorization, "Bearer $authToken") }
            header(UserAgent, "NAV IT McBotFace")
        }
    }
}

internal fun requiredFromEnv(name: String) =
    System.getProperty(name)
        ?: System.getenv(name)
        ?: throw RuntimeException("unable to find '$name' in environment")
