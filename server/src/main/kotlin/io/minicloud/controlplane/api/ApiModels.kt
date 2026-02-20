package io.minicloud.controlplane.api

import io.minicloud.controlplane.domain.AppInstance
import io.minicloud.controlplane.domain.DatabaseInstance
import io.minicloud.controlplane.domain.ProvisioningStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class CreateDatabaseRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val namespace: String,
)

data class CreateAppRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val namespace: String,
    @field:NotBlank
    val image: String,
    @field:Min(1)
    @field:Max(65535)
    val port: Int,
    @field:Min(1)
    val replicas: Int = 1,
    val databaseRef: String? = null,
)

data class DatabaseResponse(
    val name: String,
    val namespace: String,
    val status: ProvisioningStatus,
    val secretName: String?,
    val message: String?,
)

data class AppResponse(
    val name: String,
    val namespace: String,
    val image: String,
    val status: ProvisioningStatus,
    val replicas: Int,
    val readyReplicas: Int,
    val accessUrl: String?,
    val databaseRef: String?,
    val message: String?,
)

fun DatabaseInstance.toResponse() = DatabaseResponse(
    name = name,
    namespace = namespace,
    status = status,
    secretName = secretName,
    message = message,
)

fun AppInstance.toResponse() = AppResponse(
    name = name,
    namespace = namespace,
    image = image,
    status = status,
    replicas = replicas,
    readyReplicas = readyReplicas,
    accessUrl = accessUrl,
    databaseRef = databaseRef,
    message = message,
)

