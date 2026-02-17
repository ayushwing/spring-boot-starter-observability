package com.ayushwing.observability.autoconfigure.logging;

import com.ayushwing.observability.autoconfigure.ObservabilityProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import ch.qos.logback.classic.LoggerContext;
import net.logstash.logback.encoder.LogstashEncoder;

/**
 * Auto-configuration for structured JSON logging.
 *
 * <p>Activates when:
 * <ul>
 *   <li>Logback is on the classpath</li>
 *   <li>LogstashEncoder is on the classpath</li>
 *   <li>{@code observability.logging.format} is set to "json" (default)</li>
 * </ul>
 *
 * <p>Programmatically reconfigures the Logback root logger to use a
 * {@link net.logstash.logback.encoder.LogstashEncoder} console appender,
 * producing structured JSON log output with MDC context (traceId, spanId, requestId).
 */
@AutoConfiguration
@ConditionalOnClass({LoggerContext.class, LogstashEncoder.class})
@ConditionalOnProperty(prefix = "observability.logging", name = "format", havingValue = "json", matchIfMissing = true)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityLoggingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityLoggingAutoConfiguration.class);

    @Bean
    public JsonLoggingConfigurer jsonLoggingConfigurer(ObservabilityProperties properties) {
        return new JsonLoggingConfigurer(properties);
    }
}
