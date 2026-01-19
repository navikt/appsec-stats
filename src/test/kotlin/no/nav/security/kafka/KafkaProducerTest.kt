package no.nav.security.kafka

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class KafkaProducerTest {

    @Test
    fun `test KafkaConfig from environment returns null when env vars are missing`() {
        val config = KafkaConfig.fromEnvironment()
        assertNull(config, "Config should be null when environment variables are not set")
    }

    @Test
    fun `test KafkaConfig creation with valid parameters`() {
        val config = KafkaConfig(
            brokers = "localhost:9092",
            certificatePath = "/path/to/cert",
            privateKeyPath = "/path/to/key",
            caPath = "/path/to/ca",
            credstorePassword = "password",
            keystorePath = "/path/to/keystore",
            truststorePath = "/path/to/truststore",
            topic = "test-topic"
        )

        assertEquals("localhost:9092", config.brokers)
        assertEquals("/path/to/cert", config.certificatePath)
        assertEquals("/path/to/keystore", config.keystorePath)
        assertEquals("/path/to/truststore", config.truststorePath)
    }

    @Test
    fun `test GithubRepoStats JSON serialization with vulnerabilities`() {
        val stats = GithubRepoStats(
            repositoryName = "test-repo",
            naisTeams = listOf("team-a", "team-b"),
            vulnerabilities = listOf(
                GithubRepoStats.VulnerabilityInfo(
                    severity = "HIGH",
                    identifiers = listOf(
                        GithubRepoStats.VulnerabilityIdentifier(
                            value = "CVE-2024-1234",
                            type = "CVE"
                        ),
                        GithubRepoStats.VulnerabilityIdentifier(
                            value = "GHSA-xxxx-yyyy-zzzz",
                            type = "GHSA"
                        )
                    )
                )
            )
        )

        val json = stats.toJson()

        // Verify JSON contains expected data
        assertTrue(json.contains("\"repositoryName\":\"test-repo\""))
        assertTrue(json.contains("\"naisTeams\":[\"team-a\",\"team-b\"]"))
        assertTrue(json.contains("\"severity\":\"HIGH\""))
        assertTrue(json.contains("\"value\":\"CVE-2024-1234\""))
        assertTrue(json.contains("\"type\":\"CVE\""))
    }

    @Test
    fun `test GithubRepoStats JSON serialization with empty lists omits fields`() {
        val stats = GithubRepoStats(
            repositoryName = "test-repo"
        )

        val json = stats.toJson()

        // Verify JSON contains repositoryName but not empty lists
        assertTrue(json.contains("\"repositoryName\":\"test-repo\""))
        assertFalse(json.contains("\"naisTeams\""))
        assertFalse(json.contains("\"vulnerabilities\""))
    }
}
