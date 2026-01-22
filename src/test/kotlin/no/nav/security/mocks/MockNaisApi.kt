package no.nav.security.mocks

import io.ktor.client.*
import no.nav.security.NaisApi
import no.nav.security.NaisDeployment
import no.nav.security.NaisRepository
import no.nav.security.NaisTeam
import no.nav.security.NaisVulnerability

class MockNaisApi : NaisApi(HttpClient()) {
    var teamStatsCalled = false
    var repoVulnerabilitiesCalled = false
    var deploymentsCalled = false
    
    val mockTeams = setOf(
        NaisTeam(
            naisTeam = "team-alpha",
            repositories = listOf("test-repo-one"),
            slsaCoverage = 100,
            hasDeployedResources = true,
            hasGithubRepositories = true
        ),
        NaisTeam(
            naisTeam = "team-beta",
            repositories = listOf("test-repo-two"),
            slsaCoverage = 50,
            hasDeployedResources = false,
            hasGithubRepositories = true
        )
    )
    
    val mockNaisVulnerabilities = setOf(
        NaisRepository(
            name = "test-repo-one",
            vulnerabilities = setOf(
                NaisVulnerability(
                    identifier = "NAIS-001",
                    severity = "MEDIUM",
                    suppressed = false
                )
            )
        )
    )
    
    override suspend fun teamStats(): Set<NaisTeam> {
        teamStatsCalled = true
        return mockTeams
    }
    
    override suspend fun repoVulnerabilities(): Set<NaisRepository> {
        repoVulnerabilitiesCalled = true
        return mockNaisVulnerabilities
    }
    
    override suspend fun deployments(): Set<NaisDeployment> {
        deploymentsCalled = true
        return setOf(
            NaisDeployment(
                repository = "test-repo-one",
                environment = "production",
                createdAt = "2024-01-20T10:00:00Z"
            )
        )
    }
}
