package com.example.scheduler

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Service
import java.time.temporal.ChronoUnit

@SpringBootApplication
class SchedulerApplication

fun main(args: Array<String>) {
    runApplication<SchedulerApplication>(*args)
}

@Service
class DogAdoptionSchedulerService {

    @McpTool(description = "schedule an appointment to pick up or adopt a dog from a Pooch Palace location")
    fun schedule(@McpToolParam dogId: Int): java.time.Instant {
        return java.time.Instant.now()
            .plus(3, ChronoUnit.DAYS)
            .also { println("Scheduled appointment for dog $dogId at $it ") }
    }
}