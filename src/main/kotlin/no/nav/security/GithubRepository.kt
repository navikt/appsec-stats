package no.nav.security

class GithubRepository (
    val id: ID,
    val name: String,
    val isArchived: Boolean,
    val pushedAt: DateTime?,
    val hasVulnerabilityAlertsEnabled: Boolean,
    val vulnerabilityAlerts: Int,
)