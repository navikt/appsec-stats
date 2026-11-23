package no.nav.security.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
data class GraphQLError(
    val message: String,
    val path: List<JsonElement>? = null,
    val locations: List<GraphQLErrorLocation>? = null,
    val extensions: Map<String, JsonElement>? = null
)

@Serializable
data class GraphQLErrorLocation(
    val line: Int,
    val column: Int
)
