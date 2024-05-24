package no.nav.security

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.UserAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger("appsec-stats")

fun main(): Unit = runBlocking {
    val bq = BigQuery(requiredFromEnv("GCP_TEAM_PROJECT_ID"))
    val github = GitHub(httpClient = httpClient(requiredFromEnv("GITHUB_TOKEN")))
    val naisApi = NaisApi(http = httpClient(requiredFromEnv("NAIS_API_TOKEN")))
//    val slack = Slack(
//        httpClient = httpClient(withGithubToken = false),
//        slackWebhookUrl = requiredFromEnv("SLACK_WEBHOOK")
//    )

    try {
        val githubRepositories = github.fetchOrgRepositories()
        logger.info("Fetched ${githubRepositories.size} repositories from GitHub")
        val repositoryWithOwners = naisApi.adminsFor(githubRepositories)
        logger.info("Fetched ${repositoryWithOwners.size} repo owners from NAIS API")
        val rows = bq.insert(repositoryWithOwners)
        if(rows.isSuccess) {
            logger.info("Inserted ${rows.getOrDefault(0)} records into BigQuery")
        } else {
            throw RuntimeException("Error inserting records into BigQuery", rows.exceptionOrNull())
        }

//        slack.send(
//            channel = "appsec-aktivitet",
//            heading = "GitHub Security Stats from appsec-stats job",
//            msg = "Inserted ${rows.getOrNull()} records into BigQuery"
//        )
    } catch (e: Exception) {
        logger.error("Error running appsec-stats: $e")
//        slack.send(
//            channel = "appsec-aktivitet",
//            heading = "Error running appsec-stats",
//            msg = e.message ?: "No error message"
//        )
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal fun httpClient(authToken: String) = HttpClient(CIO) {
    expectSuccess = true
    install(Logging) {
        logger = logger
        level = LogLevel.HEADERS
        sanitizeHeader { header -> header == HttpHeaders.Authorization }
        filter {
            // Log everything except requests containing slack webhook
            !it.host.contains("hooks.slack.com")
        }
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
            header(HttpHeaders.Authorization, "Bearer $authToken")
            header(UserAgent, "NAV IT McBotFace")
        }
    }

}

private fun requiredFromEnv(name: String) =
    System.getProperty(name)
        ?: System.getenv(name)
        ?: throw RuntimeException("unable to find '$name' in environment")
