package no.nav.security

import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import no.nav.security.mocks.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IntegrationTest {
    @Test
    fun `fetchRepositoryStats produces correct bigquery output`() =
        runBlocking {
            val mockGithub = MockGitHub()
            val mockNaisApi = MockNaisApi()
            val mockTeamcatalog = MockTeamcatalog()
            val mockBqRepo = MockBigQueryRepos()

            val deps =
                AppDependencies(
                    github = mockGithub,
                    githubHttpClient = HttpClient(),
                    naisApi = mockNaisApi,
                    teamcatalog = mockTeamcatalog,
                    bqRepo = mockBqRepo,
                )

            fetchRepositoryStats(deps)

            assertTrue(mockGithub.fetchOrgRepositoriesCalled)
            assertTrue(mockNaisApi.teamStatsCalled)
            assertTrue(mockNaisApi.deploymentsCalled)
            assertTrue(mockTeamcatalog.updateRecordsCalled)
            assertTrue(mockBqRepo.insertCalled)
            assertTrue(mockBqRepo.fetchDeploymentsCalled)

            assertEquals(2, mockBqRepo.insertedRecords.size)
            mockBqRepo.insertedRecords.forEach { record ->
                assertFalse(
                    record.repositoryName.contains("/"),
                    "BigQuery repositoryName should not contain org: ${record.repositoryName}",
                )
            }
            assertEquals("test-repo-one", mockBqRepo.insertedRecords[0].repositoryName)
            assertEquals("test-repo-two", mockBqRepo.insertedRecords[1].repositoryName)
        }

    @Test
    fun `fetchRepositoryStats uses name-only format for bigquery`() =
        runBlocking {
            val mockGithub = MockGitHub()
            val mockNaisApi = MockNaisApi()
            val mockTeamcatalog = MockTeamcatalog()
            val mockBqRepo = MockBigQueryRepos()

            val deps =
                AppDependencies(
                    github = mockGithub,
                    githubHttpClient = HttpClient(),
                    naisApi = mockNaisApi,
                    teamcatalog = mockTeamcatalog,
                    bqRepo = mockBqRepo,
                )

            fetchRepositoryStats(deps)

            val bqRepoNames = mockBqRepo.insertedRecords.map { it.repositoryName }
            bqRepoNames.forEach { name ->
                assertFalse(name.contains("/"), "BigQuery should not have org prefix: $name")
            }
            assertTrue(bqRepoNames.contains("test-repo-one"))
            assertTrue(bqRepoNames.contains("test-repo-two"))
        }
}
