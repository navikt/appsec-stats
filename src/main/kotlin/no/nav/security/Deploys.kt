package no.nav.security

fun newestDeployment(record: IssueCountRecord, deployments: List<Deployment>): Deployment? =
    deployments
        .filter { it.platform.isNotBlank() }
        .filter { it.application == record.repositoryName }
        .maxByOrNull { it.latestDeploy }