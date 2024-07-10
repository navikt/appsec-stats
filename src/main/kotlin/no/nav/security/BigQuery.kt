package no.nav.security

import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert
import com.google.cloud.bigquery.JobInfo
import com.google.cloud.bigquery.QueryJobConfiguration
import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.StandardSQLTypeName
import com.google.cloud.bigquery.StandardTableDefinition
import com.google.cloud.bigquery.TableDefinition
import com.google.cloud.bigquery.TableId
import com.google.cloud.bigquery.TableInfo
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class BigQuery(projectID: String, naisAnalyseProjectId: String) {
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
            Field.of("deployDateTime", StandardSQLTypeName.DATETIME)
        )

    private val deploymentQuery = """SELECT cluster,namespace,application,max(deployTime) as latest_deploy
            FROM `$naisAnalyseProjectId.deploys.from_devrapid_unique`
            group by cluster, application, namespace
    """

    fun insert(records: List<IssueCountRecord>) = runCatching {
        createOrUpdateTableSchema()
        val now = Instant.now().epochSecond
        val rows = records.map { it ->
            // Github DateTime format: 2024-01-31T12:06:05Z
            val lastPush = Instant.parse(it.lastPush).atZone(ZoneId.systemDefault()).toLocalDate().toString()
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
                "deployDateTime" to it.deployDate
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

    fun fetchDeployments(): Result<List<Deployment>> = runCatching {
        val queryConfig = QueryJobConfiguration
            .newBuilder(deploymentQuery).build()
        val job = bq.create(JobInfo.newBuilder(queryConfig).build()).waitFor()
        if (job == null || job.status.error != null) {
            throw Exception("BigQuery: ${job?.status?.error ?: "unknown error"}")
        }
        val result = job.getQueryResults()
        result.iterateAll().map { row ->
            Deployment(row["cluster"].stringValue,
                row["namespace"].stringValue,
                row["application"].stringValue,
                row["latest_deploy"].timestampInstant
            )
        }
    }

    private fun createOrUpdateTableSchema() {
        val tableId = TableId.of(datasetName, tableName)
        val table = bq.getTable(tableId)
        val tableExists = table != null
        val tableDefinition: TableDefinition = StandardTableDefinition.of(schema)
        val tableInfo = TableInfo.newBuilder(tableId, tableDefinition).build()

        if (tableExists) {
            bq.update(tableInfo)
        } else {
            bq.create(tableInfo)
        }
    }
}

class IssueCountRecord(
    val owners: List<String>,
    val lastPush: String?,
    val repositoryName: String,
    val vulnerabilityAlertsEnabled: Boolean,
    val vulnerabilityCount: Int,
    val isArchived: Boolean,
    var productArea: String?,
    var isDeployed: Boolean = false,
    var deployDate: String? = null
)

data class Deployment(
    val cluster: String,
    val namespace: String,
    val application: String,
    val latestDeploy: Instant
)
