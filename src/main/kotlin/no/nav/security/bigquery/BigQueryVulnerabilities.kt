package no.nav.security.bigquery

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
import no.nav.security.NaisRepository
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
            Field.of("source", StandardSQLTypeName.STRING),
            Field.of("repository", StandardSQLTypeName.STRING),
            Field.of("vulnerabilities", StandardSQLTypeName.STRUCT,
                Field.of("identifiers", StandardSQLTypeName.STRING).toBuilder().setMode(Field.Mode.REPEATED).build(),
                Field.of("severity", StandardSQLTypeName.STRING),
                Field.of("suppressed", StandardSQLTypeName.BOOL)
            ).toBuilder().setMode(Field.Mode.REPEATED).build()
        )

    fun insert(records: List<BQRepoVulnerability>) = runCatching {
        createOrUpdateTableSchema()
        val now = Instant.now().epochSecond
        val rows = records.map { repo ->
            RowToInsert.of(UUID.randomUUID().toString(), mapOf(
                "when_collected" to now,
                "source" to repo.source.name,
                "repository" to repo.githubRepository,
                "vulnerabilities" to repo.vulnerabilities.map { vuln ->
                    mapOf(
                        "identifiers" to vuln.identifiers,
                        "severity" to vuln.severity,
                        "suppressed" to vuln.suppressed
                    )
                }
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

data class BQRepoVulnerability(
    val source: BQVulnerabilitySource,
    val githubRepository: String,
    val vulnerabilities: List<BQRepoVulnerabilityDetail>)

data class BQRepoVulnerabilityDetail(
    val identifiers: List<String>,
    val severity: String,
    val suppressed: Boolean
)

enum class BQVulnerabilitySource {NAIS, GITHUB}