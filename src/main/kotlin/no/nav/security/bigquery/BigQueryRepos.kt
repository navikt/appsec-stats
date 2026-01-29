package no.nav.security.bigquery

import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert
import com.google.cloud.bigquery.JobInfo
import com.google.cloud.bigquery.QueryJobConfiguration
import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.StandardSQLTypeName
import com.google.cloud.bigquery.TableId
import no.nav.security.logger
import java.time.Instant
import java.util.UUID

open class BigQueryRepos(projectID: String, naisAnalyseProjectId: String) {
    private val bq = BigQueryOptions.newBuilder()
        .setProjectId(projectID)
        .build()
        .service

    private val datasetName = "appsec"
    private val tableName = "github_repo_stats"
    private val schema =
        Schema.of(
            Field.of("when_collected", StandardSQLTypeName.TIMESTAMP),
            Field.newBuilder("owners", StandardSQLTypeName.STRING).setMode(Field.Mode.REPEATED).build(),
            Field.of("lastPush", StandardSQLTypeName.DATE),
            Field.of("repositoryName", StandardSQLTypeName.STRING),
            Field.of("vulnerabilityAlertsEnabled", StandardSQLTypeName.BOOL),
            Field.of("vulnerabilityCount", StandardSQLTypeName.INT64),
            Field.of("isArchived", StandardSQLTypeName.BOOL),
            Field.of("productArea", StandardSQLTypeName.STRING),
            Field.of("isDeployed", StandardSQLTypeName.BOOL),
            Field.of("deployDateTime", StandardSQLTypeName.DATETIME),
            Field.of("deployedTo", StandardSQLTypeName.STRING)
        )

    private val deploymentQuery = """SELECT platform,cluster,namespace,application,max(deployTime) as latest_deploy
            FROM `$naisAnalyseProjectId.deploys.from_devrapid_unique`
            group by cluster, application, namespace, platform
    """

    open fun insert(records: List<BQRepoStat>) = runCatching {
        bq.createOrUpdateTableSchema(datasetName, tableName, schema)
        val now = Instant.now().epochSecond
        val rows = records.map { it ->
            // Github DateTime format: 2024-01-31T12:06:05Z
            val lastPush = Instant.parse(it.lastPush).toBigQueryFormat()
            RowToInsert.of(UUID.randomUUID().toString(), mapOf(
                "when_collected" to now,
                "owners" to it.owners,
                "lastPush" to lastPush,
                "repositoryName" to it.repositoryName,
                "vulnerabilityAlertsEnabled" to it.vulnerabilityAlertsEnabled,
                "vulnerabilityCount" to it.vulnerabilityCount,
                "isArchived" to it.isArchived,
                "productArea" to it.productArea,
                "isDeployed" to it.isDeployed,
                "deployDateTime" to it.deployDate,
                "deployedTo" to it.deployedTo
            ))
        }

        val response = bq.insertAll(
            InsertAllRequest.newBuilder(TableId.of(datasetName, tableName))
                .setRows(rows).build()
        )
        if (response.hasErrors()) {
            throw RuntimeException(response.insertErrors.map { it.value.toString() }.joinToString())
        }
        records.size
    }

    open fun fetchDeployments(): Result<List<BqDeploymentDto>> = runCatching {
        val queryConfig = QueryJobConfiguration
            .newBuilder(deploymentQuery).build()
        val job = bq.create(JobInfo.newBuilder(queryConfig).build()).waitFor()
        if (job == null || job.status.error != null) {
            throw Exception("BigQuery: ${job?.status?.error ?: "unknown error"}")
        }
        val result = job.getQueryResults()
        result.iterateAll().mapNotNull { row ->
            try {
                val platform = row["platform"]?.stringValue
                val cluster = row["cluster"]?.stringValue
                val namespace = row["namespace"]?.stringValue
                val application = row["application"]?.stringValue
                val latestDeploy = row["latest_deploy"]?.timestampInstant

                // Filter out entries with null or empty fields
                if (platform.isNullOrEmpty() || cluster.isNullOrEmpty() ||
                    namespace.isNullOrEmpty() || application.isNullOrEmpty() || latestDeploy == null) {
                    logger.warn("Ignoring deployment entry with null/empty fields: platform=$platform, cluster=$cluster, namespace=$namespace, application=$application, latestDeploy=$latestDeploy")
                    null
                } else {
                    BqDeploymentDto(
                        platform,
                        cluster.substringAfterLast("-"), // Not interested in dev/prod. Only fss/gcp.
                        namespace,
                        application,
                        latestDeploy
                    )
                }
            } catch (e: Exception) {
                logger.warn("Error processing deployment row: ${e.message}", e)
                null
            }
        }
    }
}

class BQRepoStat(
    val owners: List<String>,
    val lastPush: String? = null,
    val repositoryName: String,
    val vulnerabilityAlertsEnabled: Boolean,
    val vulnerabilityCount: Int,
    val isArchived: Boolean,
    var productArea: String? = null,
    var isDeployed: Boolean = false,
    var deployDate: String? = null,
    var deployedTo: String? = null
)

data class BqDeploymentDto(
    val platform: String,
    val cluster: String,
    val namespace: String,
    val application: String,
    val latestDeploy: Instant
)
