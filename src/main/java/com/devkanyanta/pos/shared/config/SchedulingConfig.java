package com.devkanyanta.pos.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables background jobs (ZRA submission retries). Can be switched off with
 * {@code pos.scheduling.enabled=false}, e.g. in tests that drive processing explicitly.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "pos.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
