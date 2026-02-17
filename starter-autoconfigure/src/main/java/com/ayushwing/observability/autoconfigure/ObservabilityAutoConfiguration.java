package com.ayushwing.observability.autoconfigure;

import com.ayushwing.observability.autoconfigure.logging.ObservabilityLoggingAutoConfiguration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Main auto-configuration entry point for the observability starter.
 * Imports individual feature auto-configurations:
 * - Structured logging (JSON + MDC)
 * - Distributed tracing (OpenTelemetry) — coming soon
 * - Metrics (Micrometer) — coming soon
 */
@AutoConfiguration
@Import(ObservabilityLoggingAutoConfiguration.class)
public class ObservabilityAutoConfiguration {
}
