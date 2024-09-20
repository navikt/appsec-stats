package no.nav.security

import no.nav.security.bigquery.BQRepoStat
import no.nav.security.bigquery.Deployment

fun newestDeployment(record: BQRepoStat, deployments: List<Deployment>): Deployment? =
    deployments
        .filter { it.platform.isNotBlank() }
        .filter { it.application == record.repositoryName }
        .maxByOrNull { it.latestDeploy }