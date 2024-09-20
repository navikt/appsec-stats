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
import no.nav.security.bigquery.BQNaisTeam
import no.nav.security.bigquery.BigQueryRepos
import no.nav.security.bigquery.BigQueryTeams
import no.nav.security.bigquery.toBigQueryFormat
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger("appsec-stats")

fun main(): Unit = runBlocking {
    val bqRepo = BigQueryRepos(requiredFromEnv("GCP_TEAM_PROJECT_ID"), requiredFromEnv("NAIS_ANALYSE_PROJECT_ID"))
    val bqTeam = BigQueryTeams(requiredFromEnv("GCP_TEAM_PROJECT_ID"))
    val github = GitHub(httpClient = httpClient(requiredFromEnv("GITHUB_TOKEN")))
    val naisApi = NaisApi(httpClient = httpClient(requiredFromEnv("NAIS_API_TOKEN")))
    val slack = Slack(httpClient = httpClient(null), slackWebhookUrl = requiredFromEnv("SLACK_WEBHOOK"))
    val teamcatalog = Teamcatalog(httpClient = httpClient(null))

    logger.info("Looking for GitHub repos...")
    val githubRepositories = github.fetchOrgRepositories()
    logger.info("Fetched ${githubRepositories.size} repositories from GitHub")

    logger.info("Looking for repo owners...")
    val repositoryWithOwners = naisApi.adminsFor(githubRepositories)
    logger.info("Fetched ${repositoryWithOwners.size} repo owners from NAIS API")

    teamcatalog.updateRecordsWithProductAreasForTeams(repositoryWithOwners)

    logger.info("Getting deployments...")
    val deployments = bqRepo.fetchDeployments().getOrThrow()
    logger.info("Fetched ${deployments.size} deployments")
    repositoryWithOwners.forEach { repo ->
        newestDeployment(repo, deployments)?.let { deployment ->
            repo.isDeployed = true
            repo.deployDate = deployment.latestDeploy.toBigQueryFormat()
            repo.deployedTo = deployment.cluster
        }
    }
    bqRepo.insert(repositoryWithOwners).fold(
        { rowCount -> logger.info("Inserted $rowCount rows into BigQuery repo dataset") },
        { ex -> slack.send("Insert to BigQuery Repo dataset failed: ${ex.stackTraceToString()}") }
    )

    logger.info("Looking for team stats...")
    val naisTeamStats = naisApi.teamStats()
    logger.info("Fetched stats for ${naisTeamStats.size} teams")
    bqTeam.insert(naisTeamStats).fold(
        { rowCount -> logger.info("Inserted $rowCount rows into BigQuery team dataset") },
        { ex -> slack.send("Insert to BigQuery Team dataset failed: ${ex.stackTraceToString()}") }
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
