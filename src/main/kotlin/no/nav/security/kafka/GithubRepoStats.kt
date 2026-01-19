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
        val identifiers: List<VulnerabilityIdentifier>
    )

    @Serializable
    data class VulnerabilityIdentifier(
        val value: String,
        val type: String
    )

    companion object {
        private val json = Json {
            encodeDefaults = false // Don't encode empty lists
            prettyPrint = false
        }
    }

    fun toJson(): String = json.encodeToString(this)
}
