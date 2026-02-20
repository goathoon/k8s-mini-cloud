package io.minicloud.controlplane.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebCorsConfig(
    @Value("\${minicloud.web.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:5500,http://127.0.0.1:5500}")
    private val allowedOriginsRaw: String,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        val origins = allowedOriginsRaw.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toTypedArray()

        registry.addMapping("/v1/**")
            .allowedOrigins(*origins)
            .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
            .allowedHeaders("*")
    }
}

