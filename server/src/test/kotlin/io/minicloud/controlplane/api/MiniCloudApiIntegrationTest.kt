package io.minicloud.controlplane.api

import com.fasterxml.jackson.databind.ObjectMapper
import io.minicloud.controlplane.domain.AppInstanceRepository
import io.minicloud.controlplane.domain.DatabaseInstanceRepository
import io.minicloud.controlplane.orchestration.AppProvisionResult
import io.minicloud.controlplane.orchestration.AppProvisionSpec
import io.minicloud.controlplane.orchestration.DatabaseProvisionResult
import io.minicloud.controlplane.orchestration.DatabaseProvisionSpec
import io.minicloud.controlplane.orchestration.KubectlUnavailableException
import io.minicloud.controlplane.orchestration.KubernetesOrchestrator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MiniCloudApiIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var appRepository: AppInstanceRepository

    @Autowired
    lateinit var databaseRepository: DatabaseInstanceRepository

    @MockBean
    lateinit var orchestrator: KubernetesOrchestrator

    @BeforeEach
    fun setUp() {
        appRepository.deleteAll()
        databaseRepository.deleteAll()
    }

    @Test
    fun `create database should return created and ready status`() {
        given(
            orchestrator.provisionDatabase(
                DatabaseProvisionSpec(name = "pg-main", namespace = "demo"),
            ),
        ).willReturn(
            DatabaseProvisionResult(secretName = "pg-main-conn"),
        )

        val request = mapOf(
            "name" to "pg-main",
            "namespace" to "demo",
        )

        mockMvc.perform(
            post("/v1/databases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("pg-main"))
            .andExpect(jsonPath("$.namespace").value("demo"))
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.secretName").value("pg-main-conn"))
    }

    @Test
    fun `create duplicate database should return conflict`() {
        given(
            orchestrator.provisionDatabase(
                DatabaseProvisionSpec(name = "pg-main", namespace = "demo"),
            ),
        ).willReturn(
            DatabaseProvisionResult(secretName = "pg-main-conn"),
        )

        val request = mapOf(
            "name" to "pg-main",
            "namespace" to "demo",
        )

        mockMvc.perform(
            post("/v1/databases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isCreated)

        mockMvc.perform(
            post("/v1/databases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ALREADY_EXISTS"))
    }

    @Test
    fun `create database should return service unavailable when kubectl is not ready`() {
        given(
            orchestrator.provisionDatabase(
                DatabaseProvisionSpec(name = "pg-main", namespace = "demo"),
            ),
        ).willThrow(
            KubectlUnavailableException("kubectl이 실행/연결되어 있지 않습니다. 먼저 minikube와 kubectl을 실행해 주세요."),
        )

        val request = mapOf(
            "name" to "pg-main",
            "namespace" to "demo",
        )

        mockMvc.perform(
            post("/v1/databases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("KUBECTL_UNAVAILABLE"))
    }

    @Test
    fun `create app with database ref should return ready and accessible via get`() {
        given(
            orchestrator.provisionDatabase(
                DatabaseProvisionSpec(name = "pg-main", namespace = "demo"),
            ),
        ).willReturn(
            DatabaseProvisionResult(secretName = "pg-main-conn"),
        )
        given(
            orchestrator.provisionApp(
                AppProvisionSpec(
                    name = "hello",
                    namespace = "demo",
                    image = "nginx:latest",
                    port = 80,
                    replicas = 1,
                    databaseSecretName = "pg-main-conn",
                ),
            ),
        ).willReturn(
            AppProvisionResult(
                accessUrl = "http://hello.demo.local",
                readyReplicas = 1,
            ),
        )

        val dbRequest = mapOf(
            "name" to "pg-main",
            "namespace" to "demo",
        )
        mockMvc.perform(
            post("/v1/databases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dbRequest)),
        )
            .andExpect(status().isCreated)

        val appRequest = mapOf(
            "name" to "hello",
            "namespace" to "demo",
            "image" to "nginx:latest",
            "port" to 80,
            "replicas" to 1,
            "databaseRef" to "pg-main",
        )

        mockMvc.perform(
            post("/v1/apps")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(appRequest)),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.readyReplicas").value(1))
            .andExpect(jsonPath("$.accessUrl").value("http://hello.demo.local"))
            .andExpect(jsonPath("$.databaseRef").value("pg-main"))

        mockMvc.perform(get("/v1/apps/hello").param("namespace", "demo"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("hello"))
            .andExpect(jsonPath("$.status").value("READY"))
    }

    @Test
    fun `create app with missing database should return not found`() {
        val appRequest = mapOf(
            "name" to "hello",
            "namespace" to "demo",
            "image" to "nginx:latest",
            "port" to 80,
            "replicas" to 1,
            "databaseRef" to "pg-main",
        )

        mockMvc.perform(
            post("/v1/apps")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(appRequest)),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `create app should return service unavailable when kubectl is not ready`() {
        given(
            orchestrator.provisionApp(
                AppProvisionSpec(
                    name = "hello",
                    namespace = "demo",
                    image = "nginx:latest",
                    port = 80,
                    replicas = 1,
                    databaseSecretName = null,
                ),
            ),
        ).willThrow(
            KubectlUnavailableException("kubectl이 실행/연결되어 있지 않습니다. 먼저 minikube와 kubectl을 실행해 주세요."),
        )

        val appRequest = mapOf(
            "name" to "hello",
            "namespace" to "demo",
            "image" to "nginx:latest",
            "port" to 80,
            "replicas" to 1,
        )

        mockMvc.perform(
            post("/v1/apps")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(appRequest)),
        )
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("KUBECTL_UNAVAILABLE"))
    }

    @Test
    fun `create app with invalid port should return validation error`() {
        val appRequest = mapOf(
            "name" to "hello",
            "namespace" to "demo",
            "image" to "nginx:latest",
            "port" to 0,
            "replicas" to 1,
        )

        mockMvc.perform(
            post("/v1/apps")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(appRequest)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `delete app should transition to deleted`() {
        given(
            orchestrator.provisionApp(
                AppProvisionSpec(
                    name = "hello",
                    namespace = "demo",
                    image = "nginx:latest",
                    port = 80,
                    replicas = 1,
                    databaseSecretName = null,
                ),
            ),
        ).willReturn(
            AppProvisionResult(
                accessUrl = "http://hello.demo.local",
                readyReplicas = 1,
            ),
        )

        val appRequest = mapOf(
            "name" to "hello",
            "namespace" to "demo",
            "image" to "nginx:latest",
            "port" to 80,
            "replicas" to 1,
        )

        mockMvc.perform(
            post("/v1/apps")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(appRequest)),
        )
            .andExpect(status().isCreated)

        mockMvc.perform(delete("/v1/apps/hello").param("namespace", "demo"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("DELETED"))
    }
}
