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
import kotlinx.serialization.json.Json
import no.nav.security.bigquery.BQNaisTeam
import no.nav.security.bigquery.BQRepoStat
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
    val teamcatalog = Teamcatalog(httpClient = httpClient(null))

    logger.info("Looking for GitHub repos...")
    val githubRepositories = github.fetchOrgRepositories()
    logger.info("Fetched ${githubRepositories.size} repositories from GitHub")

    logger.info("Looking for team info in nais graphql...")
    val naisTeams = naisApi.teamStats()
    logger.info("Fetched info about ${naisTeams.size} teams from NAIS")

    val repositoryWithOwners = githubRepositories.map { repo ->
        val githubRepo = githubRepositories.find { it.name == repo.name }
        BQRepoStat(
            owners = naisTeams.find { it.repositories.contains(repo.name) }?.naisTeam?.let { listOf(it) } ?: emptyList(),
            repositoryName = repo.name,
            vulnerabilityAlertsEnabled = githubRepo?.hasVulnerabilityAlertsEnabled ?: false,
            vulnerabilityCount = githubRepo?.vulnerabilityAlerts ?: 0,
            isArchived = githubRepo?.isArchived ?: false,
        )
    }
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
        { ex -> throw ex }
    )

    val bqNaisTeams = naisTeams.map {
        BQNaisTeam(
            naisTeam = it.naisTeam,
            slsaCoverage = it.slsaCoverage,
            hasDeployedResources = it.hasDeployedResources,
            hasGithubRepositories = it.hasGithubRepositories
        )
    }
    bqTeam.insert(bqNaisTeams).fold(
        { rowCount -> logger.info("Inserted $rowCount rows into BigQuery team dataset") },
        { ex -> throw ex }
    )
}

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
