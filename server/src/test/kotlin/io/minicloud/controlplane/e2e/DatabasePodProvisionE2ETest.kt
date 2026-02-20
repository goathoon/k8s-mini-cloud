package io.minicloud.controlplane.e2e

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "RUN_DB_POD_E2E", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DatabasePodProvisionE2ETest {
    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `database endpoint should create postgres pod in kubernetes`() {
        val namespace = "mini-cloud-e2e-${System.currentTimeMillis().toString().takeLast(6)}"
        val databaseName = "pg-${UUID.randomUUID().toString().replace("-", "").take(8)}"

        assertKubectlConnected()

        try {
            val response = restTemplate.postForEntity(
                "/v1/databases",
                CreateDatabaseRequest(
                    name = databaseName,
                    namespace = namespace,
                ),
                CreateDatabaseResponse::class.java,
            )

            assertEquals(HttpStatus.CREATED, response.statusCode)
            assertNotNull(response.body)
            assertEquals(databaseName, response.body!!.name)
            assertEquals(namespace, response.body!!.namespace)
            assertEquals("READY", response.body!!.status)
            assertEquals("$databaseName-conn", response.body!!.secretName)

            val podName = "$databaseName-pg"
            val podPhase = runKubectl(
                "-n",
                namespace,
                "get",
                "pod",
                podName,
                "-o",
                "jsonpath={.status.phase}",
            )
            assertEquals(0, podPhase.exitCode, "Failed to query pod: ${podPhase.stderr}")
            assertEquals("Running", podPhase.stdout.trim())

            val serviceExists = runKubectl(
                "-n",
                namespace,
                "get",
                "service",
                "$databaseName-svc",
            )
            assertEquals(0, serviceExists.exitCode, "Service was not created: ${serviceExists.stderr}")
        } finally {
            // Clean up all resources created for this test run.
            runKubectl("delete", "namespace", namespace, "--ignore-not-found=true", "--wait=true")
        }
    }

    private fun assertKubectlConnected() {
        val result = runKubectl("cluster-info")
        assertEquals(
            0,
            result.exitCode,
            "kubectl/minikube is not ready. Start cluster first. stderr=${result.stderr}",
        )
    }

    private fun runKubectl(vararg args: String): CmdResult {
        val binary = System.getenv("KUBECTL_BIN") ?: "kubectl"
        val command = mutableListOf(binary)
        command.addAll(args)

        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        return CmdResult(exitCode = exitCode, stdout = stdout, stderr = stderr)
    }

    private data class CmdResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )
}

private data class CreateDatabaseRequest(
    val name: String,
    val namespace: String,
)

private data class CreateDatabaseResponse(
    val name: String?,
    val namespace: String?,
    val status: String?,
    val secretName: String?,
)
