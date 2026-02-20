package io.minicloud.controlplane.orchestration

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class HelmKubernetesOrchestrator : KubernetesOrchestrator {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun provisionDatabase(spec: DatabaseProvisionSpec): DatabaseProvisionResult {
        log.info(
            "Provision database requested. namespace={}, name={}",
            spec.namespace,
            spec.name,
        )
        // TODO: Replace with Helm/Kubernetes API integration.
        return DatabaseProvisionResult(secretName = "${spec.name}-conn")
    }

    override fun provisionApp(spec: AppProvisionSpec): AppProvisionResult {
        log.info(
            "Provision app requested. namespace={}, name={}, image={}",
            spec.namespace,
            spec.name,
            spec.image,
        )
        // TODO: Replace with Helm/Kubernetes API integration.
        val accessUrl = "http://${spec.name}.${spec.namespace}.local"
        return AppProvisionResult(accessUrl = accessUrl, readyReplicas = spec.replicas)
    }

    override fun deleteApp(spec: AppDeleteSpec) {
        log.info(
            "Delete app requested. namespace={}, name={}",
            spec.namespace,
            spec.name,
        )
        // TODO: Replace with Helm/Kubernetes API integration.
    }
}

