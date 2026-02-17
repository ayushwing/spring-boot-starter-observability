package com.ayushwing.observability.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the observability starter.
 * Bind with prefix "observability" in application.yml.
 */
@ConfigurationProperties(prefix = "observability")
public class ObservabilityProperties {

    private final Logging logging = new Logging();

    public Logging getLogging() {
        return logging;
    }

    public static class Logging {

        /**
         * Log output format: "json" for structured JSON, "text" for plain text.
         */
        private String format = "json";

        /**
         * Whether to include HTTP request headers in log output.
         */
        private boolean includeHeaders = false;

        /**
         * Whether to include the MDC context (traceId, spanId, etc.) in log output.
         */
        private boolean includeMdc = true;

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

        public boolean isIncludeMdc() {
            return includeMdc;
        }

        public void setIncludeMdc(boolean includeMdc) {
            this.includeMdc = includeMdc;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }
    }
}
