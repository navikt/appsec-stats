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
    val repositories: NaisRepositoryConnection
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
