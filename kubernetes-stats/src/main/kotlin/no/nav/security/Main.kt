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
    val slack = Slack(httpClient = httpClient(null), slackWebhookUrl = requiredFromEnv("SLACK_WEBHOOK"))
    val kubernetes = Kubernetes()

    val cluster = requiredFromEnv("NAIS_CLUSTER_NAME")

    val list = kubernetes.fetchDeploymentStatus(cluster)
    logger.info("Fetched info about ${list.size} deployments in ${cluster}")

    bq.insert(list).fold(
        { rowCount -> logger.info("Inserted $rowCount rows into BigQuery") },
        { ex -> slack.send(
            channel = "appsec-aktivitet",
            heading = "Kubernetes Deployment Stats",
            msg = "Insert to BigQuery failed: ${ex.localizedMessage}" // ex.message is too long?
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
            header(UserAgent, "NAV IT McBotFace")
        }
    }
}

internal fun requiredFromEnv(name: String) =
    System.getProperty(name)
        ?: System.getenv(name)
        ?: throw RuntimeException("unable to find '$name' in environment")
