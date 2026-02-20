package io.minicloud.controlplane.orchestration

import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component

@Component
class ManifestTemplateRenderer(
    private val resourceLoader: ResourceLoader,
) {
    fun render(templatePath: String, variables: Map<String, String>): String {
        val resource = resourceLoader.getResource("classpath:$templatePath")
        val template = resource.inputStream.bufferedReader().use { it.readText() }
        return variables.entries.fold(template) { acc, entry ->
            acc.replace("{{${entry.key}}}", entry.value)
        }
    }
}

