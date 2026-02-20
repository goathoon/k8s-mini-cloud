package io.minicloud.controlplane.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface AppInstanceRepository : JpaRepository<AppInstance, Long> {
    fun findByNamespaceAndName(namespace: String, name: String): Optional<AppInstance>
}

