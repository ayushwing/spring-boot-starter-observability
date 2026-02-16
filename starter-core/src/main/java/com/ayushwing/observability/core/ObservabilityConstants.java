package com.ayushwing.observability.core;

/**
 * Shared constants used across the observability starter modules.
 */
public final class ObservabilityConstants {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String SPAN_ID_KEY = "spanId";
    public static final String REQUEST_ID_KEY = "requestId";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CONFIG_PREFIX = "observability";

    private ObservabilityConstants() {
        // utility class
    }
}
