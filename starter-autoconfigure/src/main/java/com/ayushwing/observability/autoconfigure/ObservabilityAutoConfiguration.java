package com.ayushwing.observability.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * Main auto-configuration entry point for the observability starter.
 * Components will be added incrementally:
 * - Structured logging (JSON + MDC)
 * - Distributed tracing (OpenTelemetry)
 * - Metrics (Micrometer)
 */
@AutoConfiguration
public class ObservabilityAutoConfiguration {
    // TODO: wire up logging, tracing, and metrics auto-config beans
}
