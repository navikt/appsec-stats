package no.nav.security.mocks

import no.nav.security.bigquery.BQNaisTeam
import no.nav.security.bigquery.BigQueryTeams

class MockBigQueryTeams : BigQueryTeams("test-project") {
    var insertCalled = false
    val insertedRecords = mutableListOf<BQNaisTeam>()
    
    override fun insert(records: List<BQNaisTeam>): Result<Int> {
        insertCalled = true
        insertedRecords.addAll(records)
        return Result.success(records.size)
    }
}
