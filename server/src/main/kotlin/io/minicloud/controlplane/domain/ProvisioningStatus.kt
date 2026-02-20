package io.minicloud.controlplane.domain

enum class ProvisioningStatus {
    REQUESTED,
    PROVISIONING,
    READY,
    FAILED,
    DELETING,
    DELETED,
}

