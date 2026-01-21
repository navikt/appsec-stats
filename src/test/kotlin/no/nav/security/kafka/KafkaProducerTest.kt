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

    @Test
    fun `test GithubRepoStats JSON serialization with all vulnerability fields`() {
        val stats = GithubRepoStats(
            repositoryName = "navikt/comprehensive-test-repo",
            naisTeams = listOf("security-team"),
            vulnerabilities = listOf(
                GithubRepoStats.VulnerabilityInfo(
                    severity = "CRITICAL",
                    identifiers = listOf(
                        GithubRepoStats.VulnerabilityIdentifier(
                            value = "CVE-2024-9999",
                            type = "CVE"
                        ),
                        GithubRepoStats.VulnerabilityIdentifier(
                            value = "GHSA-abcd-efgh-ijkl",
                            type = "GHSA"
                        )
                    ),
                    dependencyScope = "RUNTIME",
                    dependabotUpdatePullRequestUrl = "https://github.com/org/repo/pull/42",
                    publishedAt = "2024-01-15T10:30:00Z",
                    cvssScore = 9.8,
                    summary = "Critical vulnerability in dependency",
                    packageEcosystem = "NPM",
                    packageName = "vulnerable-package"
                ),
                GithubRepoStats.VulnerabilityInfo(
                    severity = "MODERATE",
                    identifiers = listOf(
                        GithubRepoStats.VulnerabilityIdentifier(
                            value = "CVE-2024-1111",
                            type = "CVE"
                        )
                    ),
                    dependencyScope = "DEVELOPMENT",
                    dependabotUpdatePullRequestUrl = null, // No Dependabot PR
                    publishedAt = "2024-02-20T14:00:00Z",
                    cvssScore = 5.3,
                    summary = "Moderate severity issue",
                    packageEcosystem = "MAVEN",
                    packageName = "com.example:test-lib"
                )
            )
        )

        val json = stats.toJson()

        // Verify repository and team info
        assertTrue(json.contains("\"repositoryName\":\"navikt/comprehensive-test-repo\""))
        assertTrue(json.contains("\"naisTeams\":[\"security-team\"]"))

        // Verify first vulnerability - all fields present
        assertTrue(json.contains("\"severity\":\"CRITICAL\""))
        assertTrue(json.contains("\"dependencyScope\":\"RUNTIME\""))
        assertTrue(json.contains("\"dependabotUpdatePullRequestUrl\":\"https://github.com/org/repo/pull/42\""))
        assertTrue(json.contains("\"publishedAt\":\"2024-01-15T10:30:00Z\""))
        assertTrue(json.contains("\"cvssScore\":9.8"))
        assertTrue(json.contains("\"summary\":\"Critical vulnerability in dependency\""))
        assertTrue(json.contains("\"packageEcosystem\":\"NPM\""))
        assertTrue(json.contains("\"packageName\":\"vulnerable-package\""))

        // Verify identifiers for first vulnerability
        assertTrue(json.contains("\"value\":\"CVE-2024-9999\""))
        assertTrue(json.contains("\"type\":\"CVE\""))
        assertTrue(json.contains("\"value\":\"GHSA-abcd-efgh-ijkl\""))
        assertTrue(json.contains("\"type\":\"GHSA\""))

        // Verify second vulnerability fields
        assertTrue(json.contains("\"severity\":\"MODERATE\""))
        assertTrue(json.contains("\"dependencyScope\":\"DEVELOPMENT\""))
        assertTrue(json.contains("\"cvssScore\":5.3"))
        assertTrue(json.contains("\"packageEcosystem\":\"MAVEN\""))
        assertTrue(json.contains("\"packageName\":\"com.example:test-lib\""))

        // Verify null dependabotUpdatePullRequestUrl is omitted (encodeDefaults = false)
        // The second vulnerability shouldn't have this field in JSON
        val jsonLines = json.lines()
        val moderateVulnSection = json.substringAfter("\"MODERATE\"").substringBefore("\"severity\":\"CRITICAL\"")
        assertFalse(moderateVulnSection.contains("\"dependabotUpdatePullRequestUrl\""),
            "Null dependabotUpdatePullRequestUrl should be omitted from JSON")
    }
}
