package io.minicloud.controlplane.orchestration

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.util.UUID

@Component
class HelmKubernetesOrchestrator(
    @Value("\${minicloud.kubernetes.kubectl-bin:kubectl}")
    private val kubectlBin: String,
    @Value("\${minicloud.kubernetes.postgres.image:postgres:16-alpine}")
    private val postgresImage: String,
    @Value("\${minicloud.kubernetes.postgres.port:5432}")
    private val postgresPort: Int,
    @Value("\${minicloud.kubernetes.wait-timeout-seconds:120}")
    private val waitTimeoutSeconds: Long,
    private val manifestTemplateRenderer: ManifestTemplateRenderer,
) : KubernetesOrchestrator {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun provisionDatabase(spec: DatabaseProvisionSpec): DatabaseProvisionResult {
        validateK8sName(spec.namespace, "namespace")
        validateK8sName(spec.name, "database name")
        ensureKubectlReady()

        val secretName = "${spec.name}-conn"
        val podName = "${spec.name}-pg"
        val serviceName = "${spec.name}-svc"
        val dbPassword = UUID.randomUUID().toString().replace("-", "").take(20)
        val dbUser = "appuser"
        val dbName = spec.name.replace("-", "_")

        log.info(
            "Provision database requested. namespace={}, name={}, pod={}",
            spec.namespace,
            spec.name,
            podName,
        )

        runCommand(
            listOf(kubectlBin, "create", "namespace", spec.namespace, "--dry-run=client", "-o", "yaml"),
            pipelineTo = listOf(kubectlBin, "apply", "-f", "-"),
        )
        runCommand(
            listOf(
                kubectlBin,
                "-n",
                spec.namespace,
                "create",
                "secret",
                "generic",
                secretName,
                "--from-literal=POSTGRES_DB=$dbName",
                "--from-literal=POSTGRES_USER=$dbUser",
                "--from-literal=POSTGRES_PASSWORD=$dbPassword",
                "--dry-run=client",
                "-o",
                "yaml",
            ),
            pipelineTo = listOf(kubectlBin, "apply", "-f", "-"),
        )

        val manifest = manifestTemplateRenderer.render(
            templatePath = "k8s/templates/database.yaml",
            variables = mapOf(
                "POD_NAME" to podName,
                "NAMESPACE" to spec.namespace,
                "INSTANCE_NAME" to spec.name,
                "POSTGRES_IMAGE" to postgresImage,
                "POSTGRES_PORT" to postgresPort.toString(),
                "SECRET_NAME" to secretName,
                "SERVICE_NAME" to serviceName,
            ),
        )

        applyYaml(manifest)

        runCommand(
            listOf(
                kubectlBin,
                "-n",
                spec.namespace,
                "wait",
                "--for=condition=Ready",
                "pod/$podName",
                "--timeout=${waitTimeoutSeconds}s",
            ),
        )

        return DatabaseProvisionResult(secretName = secretName)
    }

    override fun provisionApp(spec: AppProvisionSpec): AppProvisionResult {
        validateK8sName(spec.namespace, "namespace")
        validateK8sName(spec.name, "app name")
        ensureKubectlReady()

        val deploymentName = spec.name
        val serviceName = "${spec.name}-svc"
        val ingressName = "${spec.name}-ing"
        val accessHost = "${spec.name}.${spec.namespace}.local"

        log.info(
            "Provision app requested. namespace={}, name={}, image={}, replicas={}",
            spec.namespace,
            spec.name,
            spec.image,
            spec.replicas,
        )

        runCommand(
            listOf(kubectlBin, "create", "namespace", spec.namespace, "--dry-run=client", "-o", "yaml"),
            pipelineTo = listOf(kubectlBin, "apply", "-f", "-"),
        )

        val dbEnvBlock = if (spec.databaseSecretName != null) {
            val dbHost = "${spec.databaseSecretName.removeSuffix("-conn")}-svc"
            val envBlock = manifestTemplateRenderer.render(
                templatePath = "k8s/templates/app-db-env.yaml",
                variables = mapOf(
                    "DB_HOST" to dbHost,
                    "DB_SECRET_NAME" to spec.databaseSecretName,
                ),
            )
            "\n${envBlock.prependIndent("          ")}"
        } else {
            ""
        }

        val manifest = manifestTemplateRenderer.render(
            templatePath = "k8s/templates/app.yaml",
            variables = mapOf(
                "DEPLOYMENT_NAME" to deploymentName,
                "NAMESPACE" to spec.namespace,
                "APP_NAME" to spec.name,
                "REPLICAS" to spec.replicas.toString(),
                "APP_IMAGE" to spec.image,
                "APP_PORT" to spec.port.toString(),
                "DB_ENV_BLOCK" to dbEnvBlock,
                "SERVICE_NAME" to serviceName,
                "INGRESS_NAME" to ingressName,
                "ACCESS_HOST" to accessHost,
            ),
        )

        applyYaml(manifest)

        runCommand(
            listOf(
                kubectlBin,
                "-n",
                spec.namespace,
                "rollout",
                "status",
                "deployment/$deploymentName",
                "--timeout=${waitTimeoutSeconds}s",
            ),
        )

        val readyReplicasOutput = runCommandWithOutput(
            listOf(
                kubectlBin,
                "-n",
                spec.namespace,
                "get",
                "deployment",
                deploymentName,
                "-o",
                "jsonpath={.status.readyReplicas}",
            ),
        ).stdout.trim()

        val readyReplicas = readyReplicasOutput.toIntOrNull() ?: 0
        val accessUrl = "http://$accessHost"
        return AppProvisionResult(accessUrl = accessUrl, readyReplicas = readyReplicas)
    }

    override fun deleteApp(spec: AppDeleteSpec) {
        validateK8sName(spec.namespace, "namespace")
        validateK8sName(spec.name, "app name")
        ensureKubectlReady()

        val deploymentName = spec.name
        val serviceName = "${spec.name}-svc"
        val ingressName = "${spec.name}-ing"

        log.info(
            "Delete app requested. namespace={}, name={}",
            spec.namespace,
            spec.name,
        )

        runCommand(
            listOf(
                kubectlBin,
                "-n",
                spec.namespace,
                "delete",
                "ingress",
                ingressName,
                "--ignore-not-found=true",
            ),
        )
        runCommand(
            listOf(
                kubectlBin,
                "-n",
                spec.namespace,
                "delete",
                "service",
                serviceName,
                "--ignore-not-found=true",
            ),
        )
        runCommand(
            listOf(
                kubectlBin,
                "-n",
                spec.namespace,
                "delete",
                "deployment",
                deploymentName,
                "--ignore-not-found=true",
            ),
        )
    }

    private fun applyYaml(yaml: String) {
        val tmp = Files.createTempFile("mini-cloud-", ".yaml")
        try {
            Files.writeString(tmp, yaml)
            runCommand(listOf(kubectlBin, "apply", "-f", tmp.toString()))
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    private fun validateK8sName(value: String, fieldName: String) {
        val regex = Regex("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")
        require(value.matches(regex)) {
            "$fieldName must follow Kubernetes DNS-1123 label format: $value"
        }
    }

    private fun runCommand(command: List<String>, pipelineTo: List<String>? = null) {
        val output = executeWithPipeline(command, pipelineTo)
        if (output.exitCode != 0) {
            val message = output.stderr.ifBlank { output.stdout }
            if (looksLikeKubectlUnavailable(message)) {
                throw KubectlUnavailableException(
                    "kubectl이 실행/연결되어 있지 않습니다. 먼저 minikube와 kubectl을 실행해 주세요.",
                )
            }
            throw IllegalStateException("Command failed (${command.joinToString(" ")}): $message")
        }
    }

    private fun runCommandWithOutput(command: List<String>, pipelineTo: List<String>? = null): CommandOutput {
        val output = executeWithPipeline(command, pipelineTo)
        if (output.exitCode != 0) {
            val message = output.stderr.ifBlank { output.stdout }
            if (looksLikeKubectlUnavailable(message)) {
                throw KubectlUnavailableException(
                    "kubectl이 실행/연결되어 있지 않습니다. 먼저 minikube와 kubectl을 실행해 주세요.",
                )
            }
            throw IllegalStateException("Command failed (${command.joinToString(" ")}): $message")
        }
        return output
    }

    private fun executeWithPipeline(command: List<String>, pipelineTo: List<String>?): CommandOutput {
        return try {
            if (pipelineTo == null) {
                execute(command)
            } else {
                val first = execute(command)
                execute(pipelineTo, stdin = first.stdout)
            }
        } catch (ex: Exception) {
            throw KubectlUnavailableException(
                "kubectl이 실행/연결되어 있지 않습니다. 먼저 minikube와 kubectl을 실행해 주세요.",
            )
        }
    }

    private fun ensureKubectlReady() {
        runCommand(listOf(kubectlBin, "version", "--client"))
        runCommand(listOf(kubectlBin, "cluster-info"))
    }

    private fun looksLikeKubectlUnavailable(message: String): Boolean {
        val lowered = message.lowercase()
        return lowered.contains("unable to connect to the server") ||
            lowered.contains("connection refused") ||
            lowered.contains("connection to the server") ||
            lowered.contains("no configuration has been provided") ||
            lowered.contains("context was not found") ||
            lowered.contains("no such file or directory")
    }

    private fun execute(command: List<String>, stdin: String? = null): CommandOutput {
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        if (stdin != null) {
            process.outputStream.bufferedWriter().use { it.write(stdin) }
        } else {
            process.outputStream.close()
        }

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        return CommandOutput(exitCode = exitCode, stdout = stdout, stderr = stderr)
    }
}

private data class CommandOutput(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)
