package no.nav.security

import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.security.kafka.GithubRepoStats
import no.nav.security.mocks.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class IntegrationTest {
    
    @Test
    fun `fetchRepositoryStats produces correct kafka and bigquery output`() = runBlocking {
        val mockGithub = MockGitHub()
        val mockNaisApi = MockNaisApi()
        val mockTeamcatalog = MockTeamcatalog()
        val mockBqRepo = MockBigQueryRepos()
        val mockBqTeam = MockBigQueryTeams()
        val mockBqVuln = MockBigQueryVulnerabilities()
        val mockKafka = MockKafkaProducer()
        
        val deps = AppDependencies(
            github = mockGithub,
            githubHttpClient = HttpClient(),
            naisApi = mockNaisApi,
            teamcatalog = mockTeamcatalog,
            bqRepo = mockBqRepo,
            bqTeam = mockBqTeam,
            bqVulnerabilities = mockBqVuln,
            kafkaProducer = mockKafka
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
            assertFalse(record.repositoryName.contains("/"), 
                "BigQuery repositoryName should not contain org: ${record.repositoryName}")
        }
        assertEquals("test-repo-one", mockBqRepo.insertedRecords[0].repositoryName)
        assertEquals("test-repo-two", mockBqRepo.insertedRecords[1].repositoryName)
        
        assertEquals(2, mockKafka.produceCalled)
        assertEquals(2, mockKafka.producedMessages.size)
        
        mockKafka.producedMessages.forEach { message ->
            val parsed = Json.decodeFromString<GithubRepoStats>(message)
            assertTrue(parsed.repositoryName.contains("/"), 
                "Kafka repositoryName should contain org: ${parsed.repositoryName}")
        }
        
        val firstKafkaMessage = Json.decodeFromString<GithubRepoStats>(mockKafka.producedMessages[0])
        assertEquals("testorg/test-repo-one", firstKafkaMessage.repositoryName)
        assertTrue(firstKafkaMessage.naisTeams.contains("team-alpha"))
        
        assertEquals(2, mockBqTeam.insertedRecords.size)
        assertTrue(mockBqTeam.insertedRecords.any { it.naisTeam == "team-alpha" })
        assertTrue(mockBqTeam.insertedRecords.any { it.naisTeam == "team-beta" })
    }
    
    @Test
    fun `fetchVulnerabilities produces correct kafka and bigquery output`() = runBlocking {
        val mockGithub = MockGitHub()
        val mockNaisApi = MockNaisApi()
        val mockTeamcatalog = MockTeamcatalog()
        val mockBqRepo = MockBigQueryRepos()
        val mockBqTeam = MockBigQueryTeams()
        val mockBqVuln = MockBigQueryVulnerabilities()
        val mockKafka = MockKafkaProducer()
        
        val deps = AppDependencies(
            github = mockGithub,
            githubHttpClient = HttpClient(),
            naisApi = mockNaisApi,
            teamcatalog = mockTeamcatalog,
            bqRepo = mockBqRepo,
            bqTeam = mockBqTeam,
            bqVulnerabilities = mockBqVuln,
            kafkaProducer = mockKafka
        )
        
        fetchVulnerabilities(deps)
        
        assertTrue(mockGithub.fetchRepositoryVulnerabilitiesCalled)
        assertTrue(mockNaisApi.repoVulnerabilitiesCalled)
        assertTrue(mockBqVuln.insertCalled)
        
        assertEquals(1, mockKafka.produceCalled)
        assertEquals(1, mockKafka.producedMessages.size)
        
        val kafkaMessage = Json.decodeFromString<GithubRepoStats>(mockKafka.producedMessages[0])
        assertEquals("testorg/test-repo-one", kafkaMessage.repositoryName)
        assertTrue(kafkaMessage.repositoryName.contains("/"), 
            "Kafka message should use nameWithOwner format")
        assertEquals(1, kafkaMessage.vulnerabilities.size)
        assertEquals("HIGH", kafkaMessage.vulnerabilities[0].severity)
        assertEquals("CVE-2024-0001", kafkaMessage.vulnerabilities[0].identifiers[0].value)
        
        assertTrue(mockBqVuln.insertedCount > 0)
    }
    
    @Test
    fun `verify bigquery and kafka use different repository name formats`() = runBlocking {
        val mockGithub = MockGitHub()
        val mockNaisApi = MockNaisApi()
        val mockTeamcatalog = MockTeamcatalog()
        val mockBqRepo = MockBigQueryRepos()
        val mockBqTeam = MockBigQueryTeams()
        val mockBqVuln = MockBigQueryVulnerabilities()
        val mockKafka = MockKafkaProducer()
        
        val deps = AppDependencies(
            github = mockGithub,
            githubHttpClient = HttpClient(),
            naisApi = mockNaisApi,
            teamcatalog = mockTeamcatalog,
            bqRepo = mockBqRepo,
            bqTeam = mockBqTeam,
            bqVulnerabilities = mockBqVuln,
            kafkaProducer = mockKafka
        )
        
        fetchRepositoryStats(deps)
        
        val bqRepoNames = mockBqRepo.insertedRecords.map { it.repositoryName }
        val kafkaRepoNames = mockKafka.producedMessages.map { 
            Json.decodeFromString<GithubRepoStats>(it).repositoryName 
        }
        
        bqRepoNames.forEach { name ->
            assertFalse(name.contains("/"), "BigQuery should not have org prefix: $name")
        }
        
        kafkaRepoNames.forEach { name ->
            assertTrue(name.contains("/"), "Kafka should have org prefix: $name")
        }
        
        assertTrue(bqRepoNames.contains("test-repo-one"))
        assertTrue(bqRepoNames.contains("test-repo-two"))
        assertTrue(kafkaRepoNames.contains("testorg/test-repo-one"))
        assertTrue(kafkaRepoNames.contains("testorg/test-repo-two"))
    }
}
