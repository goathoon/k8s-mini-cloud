package io.minicloud.controlplane.service

import io.minicloud.controlplane.api.CreateAppRequest
import io.minicloud.controlplane.domain.AppInstance
import io.minicloud.controlplane.domain.AppInstanceRepository
import io.minicloud.controlplane.domain.DatabaseInstanceRepository
import io.minicloud.controlplane.domain.ProvisioningStatus
import io.minicloud.controlplane.orchestration.AppDeleteSpec
import io.minicloud.controlplane.orchestration.AppProvisionSpec
import io.minicloud.controlplane.orchestration.KubectlUnavailableException
import io.minicloud.controlplane.orchestration.KubernetesOrchestrator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AppService(
    private val appRepository: AppInstanceRepository,
    private val databaseRepository: DatabaseInstanceRepository,
    private val orchestrator: KubernetesOrchestrator,
) {
    @Transactional(noRollbackFor = [KubectlUnavailableException::class])
    fun create(request: CreateAppRequest): AppInstance {
        val duplicate = appRepository.existsByNamespaceAndName(request.namespace, request.name)
        if (duplicate) {
            throw ResourceAlreadyExistsException("App ${request.namespace}/${request.name} already exists")
        }

        val databaseSecretName = request.databaseRef?.let { dbName ->
            val db = databaseRepository.findTopByNamespaceAndNameOrderByCreatedAtDesc(request.namespace, dbName)
                .orElseThrow {
                    ResourceNotFoundException("Database ${request.namespace}/$dbName not found")
                }
            db.secretName
        }

        val app = appRepository.save(
            AppInstance(
                name = request.name,
                namespace = request.namespace,
                image = request.image,
                port = request.port,
                replicas = request.replicas,
                databaseRef = request.databaseRef,
                status = ProvisioningStatus.REQUESTED,
            ),
        )

        app.status = ProvisioningStatus.PROVISIONING
        appRepository.save(app)

        return try {
            val result = orchestrator.provisionApp(
                AppProvisionSpec(
                    name = app.name,
                    namespace = app.namespace,
                    image = app.image,
                    port = app.port,
                    replicas = app.replicas,
                    databaseSecretName = databaseSecretName,
                ),
            )
            app.accessUrl = result.accessUrl
            app.readyReplicas = result.readyReplicas
            app.status = ProvisioningStatus.READY
            appRepository.save(app)
        } catch (ex: KubectlUnavailableException) {
            app.status = ProvisioningStatus.FAILED
            app.message = ex.message
            appRepository.save(app)
            throw ex
        } catch (ex: Exception) {
            app.status = ProvisioningStatus.FAILED
            app.message = ex.message
            appRepository.save(app)
        }
    }

    @Transactional(readOnly = true)
    fun get(namespace: String, name: String): AppInstance {
        return appRepository.findTopByNamespaceAndNameOrderByCreatedAtDesc(namespace, name)
            .orElseThrow { ResourceNotFoundException("App $namespace/$name not found") }
    }

    @Transactional(noRollbackFor = [KubectlUnavailableException::class])
    fun delete(namespace: String, name: String): AppInstance {
        val app = appRepository.findTopByNamespaceAndNameOrderByCreatedAtDesc(namespace, name)
            .orElseThrow { ResourceNotFoundException("App $namespace/$name not found") }

        app.status = ProvisioningStatus.DELETING
        appRepository.save(app)

        return try {
            orchestrator.deleteApp(
                AppDeleteSpec(
                    name = app.name,
                    namespace = app.namespace,
                ),
            )
            app.status = ProvisioningStatus.DELETED
            appRepository.save(app)
        } catch (ex: KubectlUnavailableException) {
            app.status = ProvisioningStatus.FAILED
            app.message = ex.message
            appRepository.save(app)
            throw ex
        } catch (ex: Exception) {
            app.status = ProvisioningStatus.FAILED
            app.message = ex.message
            appRepository.save(app)
        }
    }
}
