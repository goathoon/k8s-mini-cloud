package io.minicloud.controlplane

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MiniCloudApplication

fun main(args: Array<String>) {
    runApplication<MiniCloudApplication>(*args)
}

