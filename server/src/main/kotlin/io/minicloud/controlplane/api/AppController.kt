package io.minicloud.controlplane.api

import io.minicloud.controlplane.service.AppService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/apps")
class AppController(
    private val appService: AppService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateAppRequest): AppResponse {
        return appService.create(request).toResponse()
    }

    @GetMapping("/{name}")
    fun get(
        @PathVariable name: String,
        @RequestParam namespace: String,
    ): AppResponse {
        return appService.get(namespace = namespace, name = name).toResponse()
    }

    @DeleteMapping("/{name}")
    fun delete(
        @PathVariable name: String,
        @RequestParam namespace: String,
    ): AppResponse {
        return appService.delete(namespace = namespace, name = name).toResponse()
    }
}

