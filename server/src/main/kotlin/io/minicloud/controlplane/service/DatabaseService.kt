package io.minicloud.controlplane.service

import io.minicloud.controlplane.api.CreateDatabaseRequest
import io.minicloud.controlplane.domain.DatabaseInstance
import io.minicloud.controlplane.domain.DatabaseInstanceRepository
import io.minicloud.controlplane.domain.ProvisioningStatus
import io.minicloud.controlplane.orchestration.DatabaseProvisionSpec
import io.minicloud.controlplane.orchestration.KubernetesOrchestrator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DatabaseService(
    private val databaseRepository: DatabaseInstanceRepository,
    private val orchestrator: KubernetesOrchestrator,
) {
    @Transactional
    fun create(request: CreateDatabaseRequest): DatabaseInstance {
        val duplicate = databaseRepository.findByNamespaceAndName(request.namespace, request.name).isPresent
        if (duplicate) {
            throw ResourceAlreadyExistsException("Database ${request.namespace}/${request.name} already exists")
        }

        val database = databaseRepository.save(
            DatabaseInstance(
                name = request.name,
                namespace = request.namespace,
                status = ProvisioningStatus.REQUESTED,
            ),
        )

        database.status = ProvisioningStatus.PROVISIONING
        databaseRepository.save(database)

        return try {
            val result = orchestrator.provisionDatabase(
                DatabaseProvisionSpec(
                    name = database.name,
                    namespace = database.namespace,
                ),
            )
            database.secretName = result.secretName
            database.status = ProvisioningStatus.READY
            databaseRepository.save(database)
        } catch (ex: Exception) {
            database.status = ProvisioningStatus.FAILED
            database.message = ex.message
            databaseRepository.save(database)
        }
    }
}

