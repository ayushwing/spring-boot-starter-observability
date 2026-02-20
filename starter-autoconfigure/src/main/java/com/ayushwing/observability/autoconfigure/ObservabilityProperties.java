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
}
