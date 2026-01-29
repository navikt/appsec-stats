package no.nav.security.mocks

import no.nav.security.bigquery.BQRepoVulnerabilities
import no.nav.security.bigquery.BigQueryVulnerabilities

class MockBigQueryVulnerabilities : BigQueryVulnerabilities("test-project") {
    var insertCalled = false
    var insertedCount = 0
    
    override fun insert(records: List<BQRepoVulnerabilities>): Result<Int> {
        insertCalled = true
        insertedCount = records.size
        return Result.success(records.size)
    }
}
