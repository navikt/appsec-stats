package no.nav.security

import io.fabric8.kubernetes.client.KubernetesClientBuilder

class Kubernetes {
    fun fetchDeploymentStatus(cluster: String): List<DeploymentInfo> {

        // Picks up settings from env var KUBECONFIG
        val kubeClient = KubernetesClientBuilder()
            .build()

        val deployments = kubeClient.apps().deployments().inAnyNamespace().list()

        val deploymentInfoList = deployments.items.map {
            DeploymentInfo(
                cluster = cluster,
                appName = it.metadata.name,
                namespace = it.metadata.namespace,
                repositoryName = it.metadata.annotations["kubernetes.io/change-cause"]!!.substringAfterLast(" "),
                creationTimestamp = it.metadata.creationTimestamp,
                teamName = it.metadata.labels.get("team")!!
            )
        }

        return deploymentInfoList
    }
}

data class DeploymentInfo(
    val appName: String,
    val cluster: String,
    val repositoryName: String,
    val teamName: String,
    val namespace: String,
    val creationTimestamp: String
)