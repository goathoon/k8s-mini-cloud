package io.minicloud.controlplane.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "app_instances")
class AppInstance(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var namespace: String,

    @Column(nullable = false)
    var image: String,

    @Column(nullable = false)
    var port: Int,

    @Column(nullable = false)
    var replicas: Int,

    @Column(nullable = true)
    var databaseRef: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProvisioningStatus = ProvisioningStatus.REQUESTED,

    @Column(nullable = false)
    var readyReplicas: Int = 0,

    @Column(nullable = true)
    var accessUrl: String? = null,

    @Column(nullable = true)
    var message: String? = null,

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    @PrePersist
    fun prePersist() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now()
    }
}

