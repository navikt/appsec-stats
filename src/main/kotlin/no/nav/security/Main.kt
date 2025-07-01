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
import no.nav.security.bigquery.newestDeployment
import no.nav.security.bigquery.toBigQueryFormat
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger("appsec-stats")

fun main(): Unit = runBlocking {
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
