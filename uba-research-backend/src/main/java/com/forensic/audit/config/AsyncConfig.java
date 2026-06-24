package com.forensic.audit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables @Async on EmailService so email sending never blocks
 * the Kafka consumer thread.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
