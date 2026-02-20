package io.minicloud.controlplane.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface AppInstanceRepository : JpaRepository<AppInstance, Long> {
    fun existsByNamespaceAndName(namespace: String, name: String): Boolean
    fun findTopByNamespaceAndNameOrderByCreatedAtDesc(namespace: String, name: String): Optional<AppInstance>
}
