package no.nav.security

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class GitHub(private val http: HttpClient, private val authToken: String) {

    private val baseUrl = "https://api.github.com"

    suspend fun reposWithSecretAlerts(org: String): List<RepoWithSecret> =
        http.get("$baseUrl/orgs/$org/secret-scanning/alerts?state=open&per_page=100") {
            header(HttpHeaders.Authorization, "Bearer $authToken")
            header(HttpHeaders.UserAgent, "NAV IT McBotFace")
            header(HttpHeaders.Accept, "application/vnd.github.v3+json")
        }.body<List<SecretAlertsResponse>>().map { resp ->
            RepoWithSecret(resp.repository.fullName, resp.secretTypeDisplayName)
        }

}
/*
 * Fetch list of teams  https://docs.github.com/en/rest/teams/teams?apiVersion=2022-11-28#list-teams
 *  Fetch list of repositories for team https://docs.github.com/en/rest/teams/teams?apiVersion=2022-11-28#list-team-repositories
 *  Filter archived repos
 *      Fetch vulnerabilities
 *          Open?
 *          Severity
 *          Age?
 *      Dependabot security update status
 *          Enabled?
 *      Code scanning status
 *          Enabled?
 */
// Fetch

@Serializable
data class SecretAlertsResponse(
    val repository: Repository,
    @SerialName("secret_type_display_name") val secretTypeDisplayName: String
)

@Serializable
data class Repository(
    @SerialName("full_name") val fullName: String
)

data class RepoWithSecret(val fullName: String, val secretType: String) {
    fun org() = fullName.substringBefore('/')
    fun name() = fullName.substringAfter('/')
}