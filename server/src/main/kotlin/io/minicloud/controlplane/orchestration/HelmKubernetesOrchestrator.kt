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

        val manifest = """
            apiVersion: v1
            kind: Pod
            metadata:
              name: $podName
              namespace: ${spec.namespace}
              labels:
                app.kubernetes.io/name: postgres
                app.kubernetes.io/instance: ${spec.name}
            spec:
              containers:
                - name: postgres
                  image: $postgresImage
                  ports:
                    - containerPort: $postgresPort
                  env:
                    - name: POSTGRES_DB
                      valueFrom:
                        secretKeyRef:
                          name: $secretName
                          key: POSTGRES_DB
                    - name: POSTGRES_USER
                      valueFrom:
                        secretKeyRef:
                          name: $secretName
                          key: POSTGRES_USER
                    - name: POSTGRES_PASSWORD
                      valueFrom:
                        secretKeyRef:
                          name: $secretName
                          key: POSTGRES_PASSWORD
                  readinessProbe:
                    tcpSocket:
                      port: $postgresPort
                    initialDelaySeconds: 5
                    periodSeconds: 5
            ---
            apiVersion: v1
            kind: Service
            metadata:
              name: $serviceName
              namespace: ${spec.namespace}
            spec:
              selector:
                app.kubernetes.io/instance: ${spec.name}
              ports:
                - port: $postgresPort
                  targetPort: $postgresPort
                  protocol: TCP
        """.trimIndent()

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
        log.info(
            "Provision app requested. namespace={}, name={}, image={}",
            spec.namespace,
            spec.name,
            spec.image,
        )
        val accessUrl = "http://${spec.name}.${spec.namespace}.local"
        return AppProvisionResult(accessUrl = accessUrl, readyReplicas = spec.replicas)
    }

    override fun deleteApp(spec: AppDeleteSpec) {
        log.info(
            "Delete app requested. namespace={}, name={}",
            spec.namespace,
            spec.name,
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
        val output = try {
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
