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

@EnabledIfEnvironmentVariable(named = "RUN_APP_DB_E2E", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppWithDatabaseE2ETest {
    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `app endpoint should create deployment with database env binding`() {
        val namespace = "mini-cloud-app-e2e-${System.currentTimeMillis().toString().takeLast(6)}"
        val dbName = "pg-${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val appName = "app-${UUID.randomUUID().toString().replace("-", "").take(8)}"

        assertKubectlConnected()

        try {
            val dbResponse = restTemplate.postForEntity(
                "/v1/databases",
                CreateDatabaseForAppRequest(
                    name = dbName,
                    namespace = namespace,
                ),
                CreateDatabaseForAppResponse::class.java,
            )
            assertEquals(HttpStatus.CREATED, dbResponse.statusCode)
            assertEquals("READY", dbResponse.body?.status)

            val appResponse = restTemplate.postForEntity(
                "/v1/apps",
                CreateAppRequest(
                    name = appName,
                    namespace = namespace,
                    image = "nginx:latest",
                    port = 80,
                    replicas = 1,
                    databaseRef = dbName,
                ),
                CreateAppResponse::class.java,
            )

            assertEquals(HttpStatus.CREATED, appResponse.statusCode)
            assertNotNull(appResponse.body)
            assertEquals("READY", appResponse.body?.status)
            assertEquals(dbName, appResponse.body?.databaseRef)

            val deploymentReady = runKubectl(
                "-n",
                namespace,
                "get",
                "deployment",
                appName,
                "-o",
                "jsonpath={.status.readyReplicas}",
            )
            assertEquals(0, deploymentReady.exitCode, "Failed to query deployment: ${deploymentReady.stderr}")
            assertEquals("1", deploymentReady.stdout.trim())

            val dbHostEnv = runKubectl(
                "-n",
                namespace,
                "get",
                "deployment",
                appName,
                "-o",
                "jsonpath={.spec.template.spec.containers[0].env[?(@.name=='DB_HOST')].value}",
            )
            assertEquals(0, dbHostEnv.exitCode, "Failed to query DB_HOST env: ${dbHostEnv.stderr}")
            assertEquals("$dbName-svc", dbHostEnv.stdout.trim())
        } finally {
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

private data class CreateAppRequest(
    val name: String,
    val namespace: String,
    val image: String,
    val port: Int,
    val replicas: Int,
    val databaseRef: String?,
)

private data class CreateDatabaseForAppRequest(
    val name: String,
    val namespace: String,
)

private data class CreateDatabaseForAppResponse(
    val name: String?,
    val namespace: String?,
    val status: String?,
    val secretName: String?,
)

private data class CreateAppResponse(
    val name: String?,
    val namespace: String?,
    val status: String?,
    val replicas: Int?,
    val readyReplicas: Int?,
    val databaseRef: String?,
    val accessUrl: String?,
)
