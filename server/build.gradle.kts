import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.minicloud"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val webDir = rootProject.layout.projectDirectory.dir("web")
val webDistDir = webDir.dir("dist")
val generatedWebDir = layout.buildDirectory.dir("generated/web-static")
val skipWebBuild = providers.gradleProperty("skipWebBuild")
    .orElse(providers.environmentVariable("MINICLOUD_SKIP_WEB_BUILD"))
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)

val installWebDependencies by tasks.registering(Exec::class) {
    group = "web"
    description = "Install web console dependencies"
    workingDir(webDir.asFile)
    commandLine("npm", "install")
    inputs.files(
        fileTree(webDir.asFile) {
            include("package.json")
            include("package-lock.json")
        },
    )
    outputs.dir(webDir.dir("node_modules"))
    onlyIf { !skipWebBuild.get() }
}

val buildWebConsole by tasks.registering(Exec::class) {
    group = "web"
    description = "Build web console (React + Vite)"
    dependsOn(installWebDependencies)
    workingDir(webDir.asFile)
    commandLine("npm", "run", "build")
    inputs.files(
        fileTree(webDir.asFile) {
            include("src/**")
            include("index.html")
            include("package.json")
            include("package-lock.json")
            include("vite.config.js")
            include(".env*")
        },
    )
    outputs.dir(webDistDir.asFile)
    onlyIf { !skipWebBuild.get() }
}

val syncWebConsole by tasks.registering(Sync::class) {
    group = "web"
    description = "Sync built web console into server static resources"
    dependsOn(buildWebConsole)
    from(webDistDir)
    into(generatedWebDir.map { it.dir("static/console") })
    doFirst {
        delete(generatedWebDir)
    }
    onlyIf { !skipWebBuild.get() }
}

tasks.named<ProcessResources>("processResources") {
    doFirst {
        delete(layout.buildDirectory.dir("resources/main/console"))
    }
    if (!skipWebBuild.get()) {
        dependsOn(syncWebConsole)
    }
    from(generatedWebDir.map { it.dir("static") }) {
        into("static")
    }
}
