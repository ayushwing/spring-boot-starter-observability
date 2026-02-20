package com.ayushwing.observability.autoconfigure.logging;

import com.ayushwing.observability.autoconfigure.ObservabilityProperties;
import com.ayushwing.observability.core.ObservabilityConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.fieldnames.LogstashFieldNames;

import org.springframework.beans.factory.InitializingBean;

/**
 * Programmatically configures Logback for structured JSON output.
 *
 * <p>Replaces the default console appender with a JSON appender using
 * {@link LogstashEncoder}. MDC keys (traceId, spanId, requestId) are
 * included automatically so that every log line carries trace context.
 */
public class JsonLoggingConfigurer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(JsonLoggingConfigurer.class);

    private final ObservabilityProperties properties;

    public JsonLoggingConfigurer(ObservabilityProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
            log.warn("LoggerFactory is not a Logback LoggerContext — skipping JSON logging setup");
            return;
        }

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);

        // Detach existing appenders to avoid duplicate output
        rootLogger.detachAndStopAllAppenders();

        // Build the JSON console appender
        ConsoleAppender<ILoggingEvent> jsonAppender = new ConsoleAppender<>();
        jsonAppender.setName("JSON_CONSOLE");
        jsonAppender.setContext(context);

        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext(context);

        ObservabilityProperties.Logging loggingProps = properties.getLogging();

        // Include MDC keys for trace context
        if (loggingProps.isIncludeMdc()) {
            encoder.addIncludeMdcKeyName(ObservabilityConstants.TRACE_ID_KEY);
            encoder.addIncludeMdcKeyName(ObservabilityConstants.SPAN_ID_KEY);
            encoder.addIncludeMdcKeyName(ObservabilityConstants.REQUEST_ID_KEY);
        }

        // Include request info MDC keys if enabled
        if (loggingProps.isIncludeRequestInfo()) {
            encoder.addIncludeMdcKeyName("httpMethod");
            encoder.addIncludeMdcKeyName("requestUri");
        }

        // Configure field names
        LogstashFieldNames fieldNames = new LogstashFieldNames();
        fieldNames.setVersion("[ignore]");
        fieldNames.setLevelValue("[ignore]");
        encoder.setFieldNames(fieldNames);

        // Build custom fields JSON from service name and static custom fields
        StringBuilder customJson = new StringBuilder("{");
        String serviceName = loggingProps.getServiceName();
        if (serviceName != null && !serviceName.isBlank()) {
            customJson.append("\"service\":\"").append(serviceName).append("\"");
        }
        java.util.Map<String, String> custom = loggingProps.getCustomFields();
        if (custom != null && !custom.isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : custom.entrySet()) {
                if (customJson.length() > 1) {
                    customJson.append(",");
                }
                customJson.append("\"").append(entry.getKey()).append("\":\"")
                        .append(entry.getValue()).append("\"");
            }
        }
        customJson.append("}");
        if (customJson.length() > 2) {
            encoder.setCustomFields(customJson.toString());
        }

        encoder.setTimeZone("UTC");
        encoder.start();

        jsonAppender.setEncoder(encoder);
        jsonAppender.start();

        rootLogger.addAppender(jsonAppender);

        log.info("Structured JSON logging configured by observability starter");
    }
}
