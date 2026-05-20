package no.nav.security.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NaisTeamStatsResponse(
    val data: NaisTeamStatsData? = null,
    val errors: List<GraphQLError>? = null
)

@Serializable
data class NaisTeamStatsData(
    val teams: NaisTeamConnection
)

@Serializable
data class NaisTeamConnection(
    val nodes: List<NaisTeamNode>,
    val pageInfo: NaisPageInfoFull
)

@Serializable
data class NaisTeamNode(
    val slug: String,
    val vulnerabilitySummary: NaisVulnerabilitySummary,
    val workloads: NaisWorkloadPageInfoOnly,
    val repositories: NaisRepositoryConnection
)

@Serializable
data class NaisVulnerabilitySummary(
    val coverage: Double
)

@Serializable
data class NaisWorkloadPageInfoOnly(
    val pageInfo: NaisWorkloadPageInfo
)

@Serializable
data class NaisWorkloadPageInfo(
    val totalCount: Int
)

@Serializable
data class NaisRepositoryConnection(
    val nodes: List<NaisRepositoryNode>,
    val pageInfo: NaisPageInfo
)

@Serializable
data class NaisRepositoryNode(
    val name: String
)

@Serializable
data class NaisPageInfo(
    val hasNextPage: Boolean,
    val endCursor: String? = null
)

@Serializable
data class NaisPageInfoFull(
    val hasNextPage: Boolean,
    val startCursor: String? = null,
    val endCursor: String? = null
)

@Serializable
data class NaisEnvironmentsResponse(
    val data: NaisEnvironmentsData? = null,
    val errors: List<GraphQLError>? = null
)

@Serializable
data class NaisEnvironmentsData(
    val environments: NaisEnvironmentConnection
)

@Serializable
data class NaisEnvironmentConnection(
    val nodes: List<NaisEnvironmentNode>
)

@Serializable
data class NaisEnvironmentNode(
    val name: String
)

@Serializable
data class NaisDeploymentsResponse(
    val data: NaisDeploymentsData? = null,
    val errors: List<GraphQLError>? = null
)

@Serializable
data class NaisDeploymentsData(
    val environment: NaisDeploymentsEnvironment? = null
)

@Serializable
data class NaisDeploymentsEnvironment(
    val workloads: NaisDeploymentWorkloadConnection
)

@Serializable
data class NaisDeploymentWorkloadConnection(
    val nodes: List<NaisDeploymentWorkload>,
    val pageInfo: NaisPageInfo
)

@Serializable
sealed class NaisDeploymentWorkload

@Serializable
@SerialName("Application")
data class NaisDeploymentApplication(
    val deployments: NaisDeploymentNodeConnection
) : NaisDeploymentWorkload()

@Serializable
@SerialName("Job")
data class NaisDeploymentJob(
    val deployments: NaisDeploymentNodeConnection
) : NaisDeploymentWorkload()

@Serializable
data class NaisDeploymentNodeConnection(
    val nodes: List<NaisDeploymentNode>
)

@Serializable
data class NaisDeploymentNode(
    val repository: String? = null,
    val createdAt: String? = null
)

@Serializable
data class NaisRepoVulnResponse(
    val data: NaisRepoVulnData? = null,
    val errors: List<GraphQLError>? = null
)

@Serializable
data class NaisRepoVulnData(
    val teams: NaisRepoVulnTeamConnection
)

@Serializable
data class NaisRepoVulnTeamConnection(
    val nodes: List<NaisRepoVulnTeamNode>,
    val pageInfo: NaisPageInfo
)

@Serializable
data class NaisRepoVulnTeamNode(
    val workloads: NaisRepoVulnWorkloadConnection
)

@Serializable
data class NaisRepoVulnWorkloadConnection(
    val nodes: List<NaisRepoVulnWorkload>,
    val pageInfo: NaisPageInfo
)

@Serializable
sealed class NaisRepoVulnWorkload

@Serializable
@SerialName("Application")
data class NaisRepoVulnApplication(
    val name: String,
    val image: NaisContainerImage,
    val deployments: NaisDeploymentNodeConnection
) : NaisRepoVulnWorkload()

@Serializable
@SerialName("Job")
data class NaisRepoVulnJob(
    val name: String,
    val image: NaisContainerImage,
    val deployments: NaisDeploymentNodeConnection
) : NaisRepoVulnWorkload()

@Serializable
data class NaisContainerImage(
    val vulnerabilities: NaisImageVulnerabilityConnection
)

@Serializable
data class NaisImageVulnerabilityConnection(
    val nodes: List<NaisImageVulnerability>,
    val pageInfo: NaisPageInfo
)

@Serializable
data class NaisImageVulnerability(
    val identifier: String,
    val severity: String,
    val suppression: NaisImageVulnerabilitySuppression? = null
)

@Serializable
data class NaisImageVulnerabilitySuppression(
    val state: String
)
