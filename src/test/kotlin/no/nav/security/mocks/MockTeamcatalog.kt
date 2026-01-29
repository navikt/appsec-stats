package no.nav.security.mocks

import io.ktor.client.*
import no.nav.security.Teamcatalog
import no.nav.security.bigquery.BQRepoStat

class MockTeamcatalog : Teamcatalog(HttpClient()) {
    var updateRecordsCalled = false
    
    override suspend fun updateRecordsWithProductAreasForTeams(records: List<BQRepoStat>) {
        updateRecordsCalled = true
        records.forEach { it.productArea = "test-product-area" }
    }
}
