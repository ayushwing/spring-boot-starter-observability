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

        // Include MDC keys for trace context
        if (properties.getLogging().isIncludeMdc()) {
            encoder.addIncludeMdcKeyName(ObservabilityConstants.TRACE_ID_KEY);
            encoder.addIncludeMdcKeyName(ObservabilityConstants.SPAN_ID_KEY);
            encoder.addIncludeMdcKeyName(ObservabilityConstants.REQUEST_ID_KEY);
        }

        // Configure field names
        LogstashFieldNames fieldNames = new LogstashFieldNames();
        fieldNames.setVersion("[ignore]");
        fieldNames.setLevelValue("[ignore]");
        encoder.setFieldNames(fieldNames);

        // Set service name as a custom field if configured
        String serviceName = properties.getLogging().getServiceName();
        if (serviceName != null && !serviceName.isBlank()) {
            encoder.setCustomFields("{\"service\":\"" + serviceName + "\"}");
        }

        encoder.setTimeZone("UTC");
        encoder.start();

        jsonAppender.setEncoder(encoder);
        jsonAppender.start();

        rootLogger.addAppender(jsonAppender);

        log.info("Structured JSON logging configured by observability starter");
    }
}
