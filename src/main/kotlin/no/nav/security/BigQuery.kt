package no.nav.security

import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert
import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.StandardSQLTypeName
import com.google.cloud.bigquery.StandardTableDefinition
import com.google.cloud.bigquery.TableDefinition
import com.google.cloud.bigquery.TableId
import com.google.cloud.bigquery.TableInfo
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID


class BigQuery(projectID: String) {
    private val bq = BigQueryOptions.newBuilder()
        .setProjectId(projectID)
        .build()
        .service

    private val datasetName = "appsec_stats"
    private val tableName = "github_security_stats"
    private val schema =
        Schema.of(
            Field.of("when_collected", StandardSQLTypeName.TIMESTAMP),
            Field.of("owners", StandardSQLTypeName.ARRAY),
            Field.of("lastPush", StandardSQLTypeName.DATE),
            Field.of("repositoryName", StandardSQLTypeName.STRING),
            Field.of("vulnerabilityAlertsEnabled", StandardSQLTypeName.BOOL),
            Field.of("vulnerabilityCount", StandardSQLTypeName.INT64),
            Field.of("isArchived", StandardSQLTypeName.BOOL)
        )

    fun insert(records: List<IssueCountRecord>) = runCatching {
        createOrUpdateTableSchema()
        val now = Instant.now().epochSecond
        val rows = records.map {
            RowToInsert.of(UUID.randomUUID().toString(), mapOf(
                "when_collected" to now,
                "owners" to it.owners,
                "lastPush" to it.lastPush,
                "repositoryName" to it.repositoryName,
                "vulnerabilityAlertsEnabled" to it.vulnerabilityAlertsEnabled,
                "vulnerabilityCount" to it.vulnerabilityCount,
                "isArchived" to it.isArchived
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
    val lastPush: DateTime?,
    val repositoryName: String,
    val vulnerabilityAlertsEnabled: Boolean,
    val vulnerabilityCount: Int,
    val isArchived: Boolean
)
