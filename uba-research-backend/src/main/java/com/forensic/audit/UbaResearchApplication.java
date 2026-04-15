// src/main/java/com/forensic/audit/UbaResearchBackendApplication.java
package com.forensic.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@SpringBootApplication
@EnableAsync
public class UbaResearchApplication {

	public static void main(String[] args) {
		SpringApplication.run(UbaResearchApplication.class, args);
	}

	/**
	 * Backs @Async with a virtual-thread-per-task executor.
	 * Named "taskExecutor" so Spring picks it up as the default async executor.
	 */
	@Bean(name = "taskExecutor")
	public Executor virtualThreadExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}
}
