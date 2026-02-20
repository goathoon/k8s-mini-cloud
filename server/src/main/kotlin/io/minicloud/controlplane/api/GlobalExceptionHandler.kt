package io.minicloud.controlplane.api

import io.minicloud.controlplane.orchestration.KubectlUnavailableException
import io.minicloud.controlplane.service.ResourceAlreadyExistsException
import io.minicloud.controlplane.service.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
)

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(KubectlUnavailableException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun handleKubectlUnavailable(ex: KubectlUnavailableException): ErrorResponse {
        return ErrorResponse(
            code = "KUBECTL_UNAVAILABLE",
            message = ex.message ?: "kubectl is unavailable",
        )
    }

    @ExceptionHandler(ResourceAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleAlreadyExists(ex: ResourceAlreadyExistsException): ErrorResponse {
        return ErrorResponse(code = "ALREADY_EXISTS", message = ex.message ?: "Resource already exists")
    }

    @ExceptionHandler(ResourceNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(ex: ResourceNotFoundException): ErrorResponse {
        return ErrorResponse(code = "NOT_FOUND", message = ex.message ?: "Resource not found")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(ex: MethodArgumentNotValidException): ErrorResponse {
        val details = ex.bindingResult.allErrors
            .filterIsInstance<FieldError>()
            .associate { it.field to (it.defaultMessage ?: "invalid value") }
        return ErrorResponse(code = "VALIDATION_ERROR", message = "Invalid request", details = details)
    }
}
