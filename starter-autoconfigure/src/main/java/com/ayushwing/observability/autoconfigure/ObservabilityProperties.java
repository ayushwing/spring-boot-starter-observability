package com.ayushwing.observability.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the observability starter.
 * Bind with prefix "observability" in application.yml.
 */
@ConfigurationProperties(prefix = "observability")
public class ObservabilityProperties {

    private final Logging logging = new Logging();
    private final Tracing tracing = new Tracing();

    public Logging getLogging() {
        return logging;
    }

    public Tracing getTracing() {
        return tracing;
    }

    public static class Logging {

        /**
         * Log output format: "json" for structured JSON, "text" for plain text.
         */
        private String format = "json";

        /**
         * Whether to include HTTP request headers in structured log output.
         * Headers are added to MDC with the prefix "header.".
         */
        private boolean includeHeaders = false;

        /**
         * Comma-separated list of header names to include when include-headers is true.
         * If empty, all headers are included.
         * Example: "Content-Type,Accept,Authorization"
         */
        private String headerFilter = "";

        /**
         * Whether to include the MDC context (traceId, spanId, etc.) in log output.
         */
        private boolean includeMdc = true;

        /**
         * Whether to include HTTP method and URI in MDC.
         */
        private boolean includeRequestInfo = true;

        /**
         * Additional custom MDC key-value pairs to include in every log line.
         * Useful for environment or deployment-specific context.
         * Example: environment=staging, region=us-east-1
         */
        private java.util.Map<String, String> customFields = new java.util.LinkedHashMap<>();

        /**
         * Application name to include in structured log output.
         * Defaults to spring.application.name if not set.
         */
        private String serviceName;

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public boolean isIncludeHeaders() {
            return includeHeaders;
        }

        public void setIncludeHeaders(boolean includeHeaders) {
            this.includeHeaders = includeHeaders;
        }

        public String getHeaderFilter() {
            return headerFilter;
        }

        public void setHeaderFilter(String headerFilter) {
            this.headerFilter = headerFilter;
        }

        public boolean isIncludeMdc() {
            return includeMdc;
        }

        public void setIncludeMdc(boolean includeMdc) {
            this.includeMdc = includeMdc;
        }

        public boolean isIncludeRequestInfo() {
            return includeRequestInfo;
        }

        public void setIncludeRequestInfo(boolean includeRequestInfo) {
            this.includeRequestInfo = includeRequestInfo;
        }

        public java.util.Map<String, String> getCustomFields() {
            return customFields;
        }

        public void setCustomFields(java.util.Map<String, String> customFields) {
            this.customFields = customFields;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }
    }

    public static class Tracing {

        /**
         * Whether distributed tracing is enabled.
         */
        private boolean enabled = true;

        /**
         * OTLP exporter endpoint for sending trace data.
         * Defaults to gRPC endpoint on localhost.
         */
        private String endpoint = "http://localhost:4317";

        /**
         * Logical name of the service reported in traces.
         * Defaults to spring.application.name if not set.
         */
        private String serviceName;

        /**
         * Sampling ratio between 0.0 and 1.0.
         * 1.0 means all traces are sampled, 0.1 means 10%.
         */
        private double samplingRatio = 1.0;

        /**
         * Maximum time in milliseconds to wait for the exporter to flush on shutdown.
         */
        private long exporterTimeoutMs = 30000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public double getSamplingRatio() {
            return samplingRatio;
        }

        public void setSamplingRatio(double samplingRatio) {
            this.samplingRatio = samplingRatio;
        }

        public long getExporterTimeoutMs() {
            return exporterTimeoutMs;
        }

        public void setExporterTimeoutMs(long exporterTimeoutMs) {
            this.exporterTimeoutMs = exporterTimeoutMs;
        }
    }
}
