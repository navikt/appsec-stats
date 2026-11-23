package no.nav.security.mocks

import io.ktor.client.*
import no.nav.security.NaisApi
import no.nav.security.NaisDeployment
import no.nav.security.NaisTeam

class MockNaisApi : NaisApi(HttpClient()) {
    var teamStatsCalled = false
    var deploymentsCalled = false
    
    val mockTeams = setOf(
        NaisTeam(
            naisTeam = "team-alpha",
            repositories = listOf("test-repo-one")
        ),
        NaisTeam(
            naisTeam = "team-beta",
            repositories = listOf("test-repo-two")
        )
    )
    
    override suspend fun teamStats(): Set<NaisTeam> {
        teamStatsCalled = true
        return mockTeams
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
