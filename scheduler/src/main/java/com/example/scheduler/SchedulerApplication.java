package com.example.scheduler;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@SpringBootApplication
public class SchedulerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SchedulerApplication.class, args);
	}

}

@Service
class DogAdoptionScheduler {

	@McpTool(description = """
            schedule an appointment to pick up or adopt a dog from a Pooch Palace location" +
            """)
	Instant schedule(@McpToolParam int dogId) {
		var user= SecurityContextHolder
				.getContext()
				.getAuthentication()
				.getName() ;
		var i = Instant.now()
				.plus(3, ChronoUnit.DAYS);
		IO.println("scheduling "  +dogId  + " for " + i +
				" for "  + user
		);
		return  i ;
	}
}