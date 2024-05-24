package no.nav.security

class RepositoryWithOwner(
    val id: ID?,
    val name: String?,
    val isArchived: Boolean?,
    val pushedAt: DateTime?,
    val hasVulnerabilityAlertsEnabled: Boolean?,
    val vulnerabilityAlerts: Int?,
    val owner: List<String>
) {
    constructor(repository: GithubRepository, owner: List<String>) : this(
        repository.id,
        repository.name,
        repository.isArchived,
        repository.pushedAt,
        repository.hasVulnerabilityAlertsEnabled,
        repository.vulnerabilityAlerts,
        owner
    )
}
