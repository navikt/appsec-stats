package no.nav.security.mocks

import no.nav.security.bigquery.BQRepoStat
import no.nav.security.bigquery.BigQueryRepos
import no.nav.security.bigquery.BqDeploymentDto
import java.time.Instant

class MockBigQueryRepos : BigQueryRepos("test-project", "test-nais-project") {
    var insertCalled = false
    var fetchDeploymentsCalled = false
    val insertedRecords = mutableListOf<BQRepoStat>()
    
    override fun insert(records: List<BQRepoStat>): Result<Int> {
        insertCalled = true
        insertedRecords.addAll(records)
        return Result.success(records.size)
    }
    
    override fun fetchDeployments(): Result<List<BqDeploymentDto>> {
        fetchDeploymentsCalled = true
        return Result.success(listOf(
            BqDeploymentDto(
                platform = "gcp",
                cluster = "prod-gcp",
                namespace = "team-alpha",
                application = "test-repo-one",
                latestDeploy = Instant.parse("2024-01-15T10:00:00Z")
            )
        ))
    }
}
