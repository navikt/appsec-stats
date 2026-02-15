package no.nav.security.mocks

import io.ktor.client.*
import no.nav.security.GitHub
import no.nav.security.GithubRepoVulnerabilities
import no.nav.security.GithubRepository

class MockGitHub : GitHub(HttpClient()) {
    var fetchOrgRepositoriesCalled = false
    var fetchRepositoryVulnerabilitiesCalled = false
    
    val mockRepositories = listOf(
        GithubRepository(
            name = "test-repo-one",
            nameWithOwner = "testorg/test-repo-one",
            isArchived = false,
            pushedAt = "2024-01-15T10:00:00Z",
            hasVulnerabilityAlertsEnabled = true,
            vulnerabilityAlerts = 2,
            adminTeams = setOf("team-alpha")
        ),
        GithubRepository(
            name = "test-repo-two",
            nameWithOwner = "testorg/test-repo-two",
            isArchived = false,
            pushedAt = "2024-01-20T14:00:00Z",
            hasVulnerabilityAlertsEnabled = false,
            vulnerabilityAlerts = 0,
            adminTeams = emptySet()
        )
    )
    
    val mockVulnerabilities = listOf(
        GithubRepoVulnerabilities(
            repository = "test-repo-one",
            nameWithOwner = "testorg/test-repo-one",
            vulnerabilities = listOf(
                GithubRepoVulnerabilities.GithubVulnerability(
                    severity = "HIGH",
                    identifier = listOf(
                        GithubRepoVulnerabilities.GithubVulnerability.GithubVulnerabilityIdentifier(
                            value = "CVE-2024-0001",
                            type = "CVE"
                        )
                    ),
                    dependencyScope = "RUNTIME",
                    cvssScore = 7.5,
                    summary = "Test vulnerability",
                    packageEcosystem = "npm",
                    packageName = "test-package"
                )
            )
        )
    )
    
    override suspend fun fetchOrgRepositories(
        repositoryCursor: String?,
        repositoryListe: List<GithubRepository>
    ): List<GithubRepository> {
        fetchOrgRepositoriesCalled = true
        return mockRepositories
    }
    
    override suspend fun fetchRepositoryVulnerabilities(
        repoEndCursor: String?,
        repoStartCursor: String?,
        vulnEndCursor: String?,
        vulnerabilitiesList: List<GithubRepoVulnerabilities>
    ): List<GithubRepoVulnerabilities> {
        fetchRepositoryVulnerabilitiesCalled = true
        return mockVulnerabilities
    }
}
