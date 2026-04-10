package com.ayushwing.observability.autoconfigure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

/**
 * Composite health indicator for the observability stack.
 *
 * <p>Reports details on the metrics registry and tracing provider so that ops teams
 * can verify observability is fully wired without inspecting logs or dashboards.
 *
 * <p>Accessible at {@code GET /actuator/health/observability} when Spring Boot
 * Actuator and this starter are both on the classpath.
 */
public class ObservabilityHealthIndicator extends AbstractHealthIndicator {

    private final MeterRegistry meterRegistry;
    private final SdkTracerProvider tracerProvider;

    public ObservabilityHealthIndicator(MeterRegistry meterRegistry,
                                        SdkTracerProvider tracerProvider) {
        super("Observability stack health check failed");
        this.meterRegistry = meterRegistry;
        this.tracerProvider = tracerProvider;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        String metricsStatus = meterRegistry != null ? "UP" : "DISABLED";
        String tracingStatus = tracerProvider != null ? "UP" : "DISABLED";

        builder.withDetail("metrics", metricsStatus)
               .withDetail("tracing", tracingStatus)
               .withDetail("logging", "UP");

        if (meterRegistry != null || tracerProvider != null) {
            builder.up();
        } else {
            builder.down();
        }
    }
}
