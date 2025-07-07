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

class BigQueryVulnerabilities(projectID: String) {
    private val bq = BigQueryOptions.newBuilder()
        .setProjectId(projectID)
        .build()
        .service

    private val datasetName = "appsec"
    private val tableName = "github_repo_vulnerability_stats"
    private val schema =
        Schema.of(
            Field.of("when_collected", StandardSQLTypeName.TIMESTAMP),
            Field.of("repository", StandardSQLTypeName.STRING),
            Field.of("vulnerabilityCount", StandardSQLTypeName.INT64)
        )

    fun insert(records: List<BQRepoVulnerabilities>) = runCatching {
        bq.createOrUpdateTableSchema(datasetName, tableName, schema)
        val now = Instant.now().epochSecond
        val rows = records.map { repo ->
            RowToInsert.of(UUID.randomUUID().toString(), mapOf(
                "when_collected" to now,
                "repository" to repo.githubRepository,
                "vulnerabilityCount" to repo.vulnerabilities
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

data class BQRepoVulnerabilities(
    val githubRepository: String,
    val vulnerabilities: Int
)
