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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import no.nav.security.bigquery.BQNaisTeam
import no.nav.security.bigquery.BQRepoStat
import no.nav.security.bigquery.BigQueryRepos
import no.nav.security.bigquery.BigQueryTeams
import no.nav.security.bigquery.BigQueryVulnerabilities
import no.nav.security.bigquery.BqDeploymentDto
import no.nav.security.bigquery.toBigQueryFormat
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger("appsec-stats")

fun main(args: Array<String>): Unit = runBlocking {
    try {
        if (args.contains("--fetch-vulnerabilities")) {
            fetchVulnerabilities()
        } else {
            fetchRepositoryStats()
        }
    } catch (e: Exception) {
        logger.error("Application crashed with error: ${e.message}", e)
        throw e
    }
}

private suspend fun fetchVulnerabilities() {
    val bqVulnerabilities = BigQueryVulnerabilities(requiredFromEnv("GCP_TEAM_PROJECT_ID"))
    val appAuth = GitHubAppAuth(
        appId = requiredFromEnv("GITHUB_APP_ID"),
        privateKeyContent = requiredFromEnv("GITHUB_APP_PRIVATE_KEY").replace("\\n", "\n"),
        installationId = requiredFromEnv("GITHUB_APP_INSTALLATION_ID"),
        httpClient = httpClient(null)
    )
    val githubHttpClient = httpClient(authToken = appAuth.getInstallationToken())
    val github = GitHub(httpClient = githubHttpClient)
    val naisApi = NaisApi(httpClient = httpClient(requiredFromEnv("NAIS_API_TOKEN")))

    logger.info("Fetching vulnerability data from Nais API...")
    val naisRepositories = try {
        naisApi.repoVulnerabilities()
    } catch (e: Exception) {
        logger.error("Failed to fetch vulnerabilities from Nais API: ${e.message}", e)
        throw e
    }
    logger.info("Fetched vulnerability data for ${naisRepositories.size} repositories for a total of ${naisRepositories.sumOf { it.vulnerabilities.size }} vulnerabilities from Nais API")

    logger.info("Fetching vulnerability data from GitHub...")
    val githubVulns = github.fetchRepositoryVulnerabilities()
    logger.info("Fetched vulnerabilities for ${githubVulns.size} repositories for a total of ${githubVulns.sumOf { it.vulnerabilities.size }} vulnerabilities")

    logger.info("Combining vulnerabilities from both sources...")
    val vulnerabilityCombiner = VulnerabilityCombiner()
    val allVulnerabilities = vulnerabilityCombiner.combineVulnerabilities(naisRepositories, githubVulns)
    logger.info("Combined vulnerabilities for ${allVulnerabilities.size} repositories")

    bqVulnerabilities.insert(allVulnerabilities).fold(
        { rowCount -> logger.info("Inserted $rowCount rows into BigQuery vulnerabilities dataset") },
        { ex -> throw ex }
    )
    logger.info("Vulnerability data fetched and inserted into BigQuery. Exiting application.")
}

private suspend fun fetchRepositoryStats() {
    val bqRepo = BigQueryRepos(requiredFromEnv("GCP_TEAM_PROJECT_ID"), requiredFromEnv("NAIS_ANALYSE_PROJECT_ID"))
    val bqTeam = BigQueryTeams(requiredFromEnv("GCP_TEAM_PROJECT_ID"))

    val appAuth = GitHubAppAuth(
        appId = requiredFromEnv("GITHUB_APP_ID"),
        privateKeyContent = requiredFromEnv("GITHUB_APP_PRIVATE_KEY").replace("\\n", "\n"),
        installationId = requiredFromEnv("GITHUB_APP_INSTALLATION_ID"),
        httpClient = httpClient(null)
    )
    // Installation token is valid for 60 minutes.
    // https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/authenticating-as-a-github-app-installation
    val githubHttpClient = httpClient(authToken = appAuth.getInstallationToken())

    val github = GitHub(httpClient = githubHttpClient)
    val naisApi = NaisApi(httpClient = httpClient(requiredFromEnv("NAIS_API_TOKEN")))
    val teamcatalog = Teamcatalog(httpClient = httpClient(null))

    logger.info("Looking for GitHub repos...")
    val githubRepositories = github.fetchOrgRepositories()
    logger.info("Fetched ${githubRepositories.size} repositories from GitHub")

    logger.info("Fetching repository admins from GitHub...")
    val repositoriesWithAdmins = githubRepositories.fetchRepositoryAdmins(httpClient = githubHttpClient)
    logger.info("Fetched admin teams for ${repositoriesWithAdmins.count { it.adminTeams.isNotEmpty() }} repositories")

    logger.info("Looking for team info in nais graphql...")
    val naisTeams = naisApi.teamStats()
    logger.info("Fetched ${naisTeams.size} teams from NAIS with a total of ${naisTeams.sumOf { it.repositories.size }} repositories")

    val repositoriesWithOwners = repositoriesWithAdmins.map { repo ->
        BQRepoStat(
            // Add owners from naisTeams AND github repository admins
            owners = (naisTeams.filter { it.repositories.contains(repo.name) }.map { it.naisTeam } + repo.adminTeams).distinct(),
            repositoryName = repo.name,
            vulnerabilityAlertsEnabled = repo.hasVulnerabilityAlertsEnabled,
            vulnerabilityCount = repo.vulnerabilityAlerts,
            isArchived = repo.isArchived,
            lastPush = repo.pushedAt
        )
    }
    logger.info("Found ${repositoriesWithOwners.count { it.owners.isNotEmpty() }} repositories with owners and ${repositoriesWithOwners.count { it.owners.isEmpty() }} repositories with no owners")

    teamcatalog.updateRecordsWithProductAreasForTeams(repositoriesWithOwners)

    logger.info("Looking for deployments in dev_rapid...")
    val bqDeployments = bqRepo.fetchDeployments().getOrThrow()
    logger.info("Fetched ${bqDeployments.size} deployments")

    repositoriesWithOwners.forEach { repo ->
        newestDeployment(repo, bqDeployments)?.let { deployment ->
            repo.isDeployed = true
            repo.deployDate = deployment.latestDeploy.toBigQueryFormat()
            repo.deployedTo = deployment.cluster
        }
    }
    logger.info("Found deployments for ${repositoriesWithOwners.count { it.isDeployed }} repositories from dev_rapid")

    logger.info("Fetching deployments from Nais API...")
    val naisDeployments = naisApi.deployments()
    logger.info("Found ${naisDeployments.size} deployments in Nais API")
    repositoriesWithOwners.forEach { repo ->
        naisDeployments.firstOrNull { it.repository == repo.repositoryName }?.let { deployment ->
            repo.isDeployed = true
            repo.deployDate = deployment.createdAt
            repo.deployedTo = deployment.environment
        }
    }
    logger.info("Found deployments for ${repositoriesWithOwners.count { it.isDeployed }} repositories after Nais API.")

    bqRepo.insert(repositoriesWithOwners).fold(
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
    logger.info("Found ${bqNaisTeams.count { it.slsaCoverage == 0 && it.hasDeployedResources }} teams with 0 slsaCoverage and deployed resources")
    bqTeam.insert(bqNaisTeams).fold(
        { rowCount -> logger.info("Inserted $rowCount rows into BigQuery team dataset") },
        { ex -> throw ex }
    )
}

fun newestDeployment(repo: BQRepoStat, deployments: List<BqDeploymentDto>): BqDeploymentDto? =
    deployments
        .filter { it.platform.isNotBlank() }
        .filter { it.application == repo.repositoryName }
        .maxByOrNull { it.latestDeploy }

internal fun httpClient(authToken: String?) = HttpClient(CIO) {
    expectSuccess = false
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 3)
        retryOnException(maxRetries = 3, retryOnTimeout = true)
        exponentialDelay(base = 2.0, maxDelayMs = 30000)

        // GitHub-specific retry condition
        // https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api
        retryIf { request, response ->
            val isRateLimit = (response.status.value == 403 || response.status.value == 429) &&
                    (response.headers["x-ratelimit-limit"] == "0" ||
                     response.headers.contains("retry-after"))

            val isServerError = response.status.value >= 500
            isRateLimit || isServerError
        }

        // GitHub-specific delay handling
        delayMillis { retry ->
            val response = response
            when {
                // Primary rate limit: wait until reset time
                response?.headers?.get("x-ratelimit-remaining") == "0" -> {
                    val resetTime = response.headers["x-ratelimit-reset"]?.toLongOrNull()
                    if (resetTime != null) {
                        val currentTime = System.currentTimeMillis() / 1000
                        val waitTime = (resetTime - currentTime + 5) * 1000 // Add 5s buffer
                        max(waitTime, 60000L) // Minimum 1 minute
                    } else {
                        300000L // Default 5 minutes
                    }
                }
                // Secondary rate limit with retry-after
                response?.headers?.get("retry-after") != null -> {
                    val retryAfter = response.headers["retry-after"]?.toLongOrNull() ?: 60L
                    (retryAfter + 1) * 1000L // Add 1s buffer
                }
                // Secondary rate limit without retry-after: exponential backoff starting at 1 minute
                response?.status?.value == 403 || response?.status?.value == 429 -> {
                    val baseWait = 60000L // 1 minute
                    val exponentialWait = (baseWait * 2.0.pow(retry.toDouble())).toLong()
                    min(exponentialWait, 600000L) // Cap at 10 minutes
                }
                // Standard exponential backoff for other errors
                else -> (2000L * (retry + 1))
            }
        }
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
            header(UserAgent, "appsec-stats")
        }
    }
}

internal fun requiredFromEnv(name: String) =
    System.getProperty(name)
        ?: System.getenv(name)
        ?: throw RuntimeException("unable to find '$name' in environment")
