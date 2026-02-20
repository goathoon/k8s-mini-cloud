package io.minicloud.controlplane.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface DatabaseInstanceRepository : JpaRepository<DatabaseInstance, Long> {
    fun findByNamespaceAndName(namespace: String, name: String): Optional<DatabaseInstance>
}

