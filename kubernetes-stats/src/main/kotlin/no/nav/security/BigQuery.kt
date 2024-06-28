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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class BigQuery(projectID: String) {
    private val bq = BigQueryOptions.newBuilder()
        .setProjectId(projectID)
        .build()
        .service

    private val datasetName = "appsec"
    private val tableName = "kubernetes_deployments"
    private val schema =
        Schema.of(
            Field.of("when_collected", StandardSQLTypeName.TIMESTAMP),
            Field.of("creationTimestamp", StandardSQLTypeName.TIMESTAMP),
            Field.of("cluster", StandardSQLTypeName.STRING),
            Field.of("appName", StandardSQLTypeName.STRING),
            Field.of("repositoryName", StandardSQLTypeName.STRING),
            Field.of("teamName", StandardSQLTypeName.STRING),
            Field.of("namespace", StandardSQLTypeName.STRING)
        )

    fun insert(records: List<DeploymentInfo>) = runCatching {
        createOrUpdateTableSchema()
        val now = Instant.now().epochSecond
        val rows = records.map { it ->
            // Kubernetes DateTime format: Fri, 05 Apr 2024 08:42:18 +0200
            val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
            val creationTimestamp = ZonedDateTime.parse(it.creationTimestamp, formatter).toInstant().toString()
            RowToInsert.of(UUID.randomUUID().toString(), mapOf(
                "when_collected" to now,
                "creationTimestamp" to creationTimestamp,
                "appName" to it.repositoryName,
                "repositoryName" to it.repositoryName,
                "teamName" to it.teamName,
                "namespace" to it.namespace
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
