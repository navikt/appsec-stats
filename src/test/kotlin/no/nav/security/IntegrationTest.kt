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
            val mockBqTeam = MockBigQueryTeams()
            val mockBqVuln = MockBigQueryVulnerabilities()

            val deps =
                AppDependencies(
                    github = mockGithub,
                    githubHttpClient = HttpClient(),
                    naisApi = mockNaisApi,
                    teamcatalog = mockTeamcatalog,
                    bqRepo = mockBqRepo,
                    bqTeam = mockBqTeam,
                    bqVulnerabilities = mockBqVuln,
                )

            fetchRepositoryStats(deps)

            assertTrue(mockGithub.fetchOrgRepositoriesCalled)
            assertTrue(mockNaisApi.teamStatsCalled)
            assertTrue(mockNaisApi.deploymentsCalled)
            assertTrue(mockTeamcatalog.updateRecordsCalled)
            assertTrue(mockBqRepo.insertCalled)
            assertTrue(mockBqRepo.fetchDeploymentsCalled)
            assertTrue(mockBqTeam.insertCalled)

            assertEquals(2, mockBqRepo.insertedRecords.size)
            mockBqRepo.insertedRecords.forEach { record ->
                assertFalse(
                    record.repositoryName.contains("/"),
                    "BigQuery repositoryName should not contain org: ${record.repositoryName}",
                )
            }
            assertEquals("test-repo-one", mockBqRepo.insertedRecords[0].repositoryName)
            assertEquals("test-repo-two", mockBqRepo.insertedRecords[1].repositoryName)

            assertEquals(2, mockBqTeam.insertedRecords.size)
            assertTrue(mockBqTeam.insertedRecords.any { it.naisTeam == "team-alpha" })
            assertTrue(mockBqTeam.insertedRecords.any { it.naisTeam == "team-beta" })
        }

    @Test
    fun `fetchVulnerabilities produces correct bigquery output`() =
        runBlocking {
            val mockGithub = MockGitHub()
            val mockNaisApi = MockNaisApi()
            val mockTeamcatalog = MockTeamcatalog()
            val mockBqRepo = MockBigQueryRepos()
            val mockBqTeam = MockBigQueryTeams()
            val mockBqVuln = MockBigQueryVulnerabilities()

            val deps =
                AppDependencies(
                    github = mockGithub,
                    githubHttpClient = HttpClient(),
                    naisApi = mockNaisApi,
                    teamcatalog = mockTeamcatalog,
                    bqRepo = mockBqRepo,
                    bqTeam = mockBqTeam,
                    bqVulnerabilities = mockBqVuln,
                )

            fetchVulnerabilities(deps)

            assertTrue(mockGithub.fetchRepositoryVulnerabilitiesCalled)
            assertTrue(mockNaisApi.repoVulnerabilitiesCalled)
            assertTrue(mockBqVuln.insertCalled)
            assertTrue(mockBqVuln.insertedCount > 0)
        }

    @Test
    fun `fetchRepositoryStats uses name-only format for bigquery`() =
        runBlocking {
            val mockGithub = MockGitHub()
            val mockNaisApi = MockNaisApi()
            val mockTeamcatalog = MockTeamcatalog()
            val mockBqRepo = MockBigQueryRepos()
            val mockBqTeam = MockBigQueryTeams()
            val mockBqVuln = MockBigQueryVulnerabilities()

            val deps =
                AppDependencies(
                    github = mockGithub,
                    githubHttpClient = HttpClient(),
                    naisApi = mockNaisApi,
                    teamcatalog = mockTeamcatalog,
                    bqRepo = mockBqRepo,
                    bqTeam = mockBqTeam,
                    bqVulnerabilities = mockBqVuln,
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
