package io.minicloud.controlplane.service

import io.minicloud.controlplane.api.CreateDatabaseRequest
import io.minicloud.controlplane.domain.DatabaseInstance
import io.minicloud.controlplane.domain.DatabaseInstanceRepository
import io.minicloud.controlplane.domain.ProvisioningStatus
import io.minicloud.controlplane.orchestration.DatabaseProvisionSpec
import io.minicloud.controlplane.orchestration.KubectlUnavailableException
import io.minicloud.controlplane.orchestration.KubernetesOrchestrator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class DatabaseService(
    private val databaseRepository: DatabaseInstanceRepository,
    private val orchestrator: KubernetesOrchestrator,
) {
    private val createLocks = ConcurrentHashMap<String, ReentrantLock>()

    @Transactional(noRollbackFor = [KubectlUnavailableException::class])
    fun create(request: CreateDatabaseRequest): DatabaseInstance {
        val lockKey = "${request.namespace}/${request.name}"
        val lock = createLocks.computeIfAbsent(lockKey) { ReentrantLock() }

        return try {
            lock.withLock {
                if (databaseRepository.existsByNamespaceAndName(request.namespace, request.name)) {
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

                try {
                    val result = orchestrator.provisionDatabase(
                        DatabaseProvisionSpec(
                            name = database.name,
                            namespace = database.namespace,
                        ),
                    )
                    database.secretName = result.secretName
                    database.status = ProvisioningStatus.READY
                    databaseRepository.save(database)
                } catch (ex: KubectlUnavailableException) {
                    database.status = ProvisioningStatus.FAILED
                    database.message = ex.message
                    databaseRepository.save(database)
                    throw ex
                } catch (ex: Exception) {
                    database.status = ProvisioningStatus.FAILED
                    database.message = ex.message
                    databaseRepository.save(database)
                }
            }
        } finally {
            if (!lock.isLocked && !lock.hasQueuedThreads()) {
                createLocks.remove(lockKey, lock)
            }
        }
    }
}
