package io.minicloud.controlplane.service

class ResourceAlreadyExistsException(message: String) : RuntimeException(message)

class ResourceNotFoundException(message: String) : RuntimeException(message)

