package io.minicloud.controlplane.api

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class ConsoleController {
    @GetMapping("/console", "/console/")
    fun console(): String = "redirect:/console/index.html"
}

