package no.nav.security.bigquery

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.StandardTableDefinition
import com.google.cloud.bigquery.TableDefinition
import com.google.cloud.bigquery.TableId
import com.google.cloud.bigquery.TableInfo
import java.time.Instant
import java.time.ZoneId

fun BigQuery.createOrUpdateTableSchema(
    datasetName: String,
    tableName: String,
    schema: Schema
) {
    val tableId = TableId.of(datasetName, tableName)
    val table = this.getTable(tableId)
    val tableExists = table != null
    val tableDefinition: TableDefinition = StandardTableDefinition.of(schema)
    val tableInfo = TableInfo.newBuilder(tableId, tableDefinition).build()

    if (tableExists) {
        this.update(tableInfo)
    } else {
        this.create(tableInfo)
    }
}

fun Instant.toBigQueryFormat() = this.atZone(ZoneId.systemDefault()).toLocalDate().toString()