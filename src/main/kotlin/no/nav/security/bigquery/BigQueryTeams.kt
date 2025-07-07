package no.nav.security.bigquery

import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert
import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.StandardSQLTypeName
import com.google.cloud.bigquery.TableId
import java.time.Instant
import java.util.*

class BigQueryTeams(projectID: String) {
    private val bq = BigQueryOptions.newBuilder()
        .setProjectId(projectID)
        .build()
        .service

    private val datasetName = "appsec"
    private val tableName = "github_team_stats"
    private val schema =
        Schema.of(
            Field.of("when_collected", StandardSQLTypeName.TIMESTAMP),
            Field.of("naisTeam", StandardSQLTypeName.STRING),
            Field.of("slsaCoverage", StandardSQLTypeName.INT64),
            Field.of("hasDeployedResources", StandardSQLTypeName.BOOL),
            Field.of("hasGithubRepositories", StandardSQLTypeName.BOOL),
        )

    fun insert(records: List<BQNaisTeam>) = runCatching {
        bq.createOrUpdateTableSchema(datasetName, tableName, schema)
        val now = Instant.now().epochSecond
        val rows = records.map {
            RowToInsert.of(UUID.randomUUID().toString(), mapOf(
                "when_collected" to now,
                "naisTeam" to it.naisTeam,
                "slsaCoverage" to it.slsaCoverage,
                "hasDeployedResources" to it.hasDeployedResources,
                "hasGithubRepositories" to it.hasGithubRepositories
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
}

class BQNaisTeam(
    val naisTeam: String,
    val slsaCoverage: Int,
    val hasDeployedResources: Boolean,
    val hasGithubRepositories: Boolean
)