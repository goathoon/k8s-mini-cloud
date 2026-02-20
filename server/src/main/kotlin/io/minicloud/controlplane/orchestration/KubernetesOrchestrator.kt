package io.minicloud.controlplane.orchestration

data class DatabaseProvisionSpec(
    val name: String,
    val namespace: String,
)

data class DatabaseProvisionResult(
    val secretName: String,
)

data class AppProvisionSpec(
    val name: String,
    val namespace: String,
    val image: String,
    val port: Int,
    val replicas: Int,
    val databaseSecretName: String?,
)

data class AppProvisionResult(
    val accessUrl: String,
    val readyReplicas: Int,
)

data class AppDeleteSpec(
    val name: String,
    val namespace: String,
)

interface KubernetesOrchestrator {
    fun provisionDatabase(spec: DatabaseProvisionSpec): DatabaseProvisionResult
    fun provisionApp(spec: AppProvisionSpec): AppProvisionResult
    fun deleteApp(spec: AppDeleteSpec)
}

