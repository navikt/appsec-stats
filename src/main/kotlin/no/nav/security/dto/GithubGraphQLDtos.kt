package no.nav.security.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubRepositoriesResponse(
    val data: GithubRepositoriesData? = null,
    val errors: List<GraphQLError>? = null
)

@Serializable
data class GithubRepositoriesData(
    val rateLimit: RateLimit? = null,
    val organization: GithubOrganizationRepos? = null
)

@Serializable
data class GithubOrganizationRepos(
    val repositories: GithubRepositoryConnection
)

@Serializable
data class GithubRepositoryConnection(
    val nodes: List<GithubRepositoryNode>,
    val pageInfo: PageInfo
)

@Serializable
data class GithubRepositoryNode(
    val name: String,
    val nameWithOwner: String,
    val isArchived: Boolean,
    val pushedAt: String? = null,
    val hasVulnerabilityAlertsEnabled: Boolean,
    val vulnerabilityAlerts: VulnerabilityAlertCount? = null
)

@Serializable
data class VulnerabilityAlertCount(
    val totalCount: Int
)

@Serializable
data class GithubVulnerabilitiesResponse(
    val data: GithubVulnerabilitiesData? = null,
    val errors: List<GraphQLError>? = null
)

@Serializable
data class GithubVulnerabilitiesData(
    val rateLimit: RateLimit? = null,
    val organization: GithubOrganizationVulns? = null
)

@Serializable
data class GithubOrganizationVulns(
    val repositories: GithubVulnRepositoryConnection
)

@Serializable
data class GithubVulnRepositoryConnection(
    val nodes: List<GithubVulnRepositoryNode>,
    val pageInfo: PageInfoWithStart
)

@Serializable
data class GithubVulnRepositoryNode(
    val name: String,
    val nameWithOwner: String,
    val vulnerabilityAlerts: VulnerabilityAlertConnection? = null
)

@Serializable
data class VulnerabilityAlertConnection(
    val nodes: List<VulnerabilityAlert>,
    val pageInfo: PageInfo
)

@Serializable
data class VulnerabilityAlert(
    val dependencyScope: String? = null,
    val dependabotUpdate: DependabotUpdate? = null,
    val securityAdvisory: SecurityAdvisory? = null,
    val securityVulnerability: SecurityVulnerability? = null
)

@Serializable
data class DependabotUpdate(
    val pullRequest: PullRequest? = null
)

@Serializable
data class PullRequest(
    val permalink: String
)

@Serializable
data class SecurityAdvisory(
    val publishedAt: String? = null,
    val cvss: Cvss? = null,
    val summary: String? = null,
    val identifiers: List<SecurityAdvisoryIdentifier> = emptyList()
)

@Serializable
data class Cvss(
    val score: Double? = null
)

@Serializable
data class SecurityAdvisoryIdentifier(
    @SerialName("value") val value: String,
    val type: String
)

@Serializable
data class SecurityVulnerability(
    val severity: String,
    @SerialName("package") val pkg: SecurityAdvisoryPackage
)

@Serializable
data class SecurityAdvisoryPackage(
    val ecosystem: String,
    val name: String
)

@Serializable
data class RateLimit(
    val remaining: Int,
    val limit: Int,
    val resetAt: String
)

@Serializable
data class PageInfo(
    val hasNextPage: Boolean,
    val endCursor: String? = null
)

@Serializable
data class PageInfoWithStart(
    val hasNextPage: Boolean,
    val endCursor: String? = null,
    val startCursor: String? = null
)

@Serializable
data class GraphQLError(
    val message: String,
    val path: List<String>? = null,
    val locations: List<GraphQLErrorLocation>? = null,
    val extensions: Map<String, String>? = null
)

@Serializable
data class GraphQLErrorLocation(
    val line: Int,
    val column: Int
)
