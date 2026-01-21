package no.nav.security.kafka

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GithubRepoStats(
    val repositoryName: String,
    val naisTeams: List<String> = emptyList(),
    val vulnerabilities: List<VulnerabilityInfo> = emptyList()
) {
    @Serializable
    data class VulnerabilityInfo(
        val severity: String,
        val identifiers: List<VulnerabilityIdentifier>,
        val dependencyScope: String? = null,
        val dependabotUpdatePullRequestUrl: String? = null,
        val publishedAt: String? = null,
        val cvssScore: Double? = null,
        val summary: String? = null,
        val packageEcosystem: String? = null,
        val packageName: String? = null
    )

    @Serializable
    data class VulnerabilityIdentifier(
        val value: String,
        val type: String
    )

    companion object {
        private val json = Json {
            encodeDefaults = false // Don't encode null values and empty lists
            prettyPrint = false
        }
    }

    fun toJson(): String = json.encodeToString(serializer(), this)
}
