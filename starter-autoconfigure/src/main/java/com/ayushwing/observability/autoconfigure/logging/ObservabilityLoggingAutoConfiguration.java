package com.ayushwing.observability.autoconfigure.logging;

import com.ayushwing.observability.autoconfigure.ObservabilityProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

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
 * <p>Provides:
 * <ul>
 *   <li>JSON console appender via {@link JsonLoggingConfigurer}</li>
 *   <li>MDC context injection via {@link RequestContextFilter} (servlet apps only)</li>
 * </ul>
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

    /**
     * Registers the {@link RequestContextFilter} with highest precedence so that
     * traceId, spanId, and requestId are available in MDC for all downstream filters
     * and the request handler.
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<RequestContextFilter> requestContextFilter(ObservabilityProperties properties) {
        FilterRegistrationBean<RequestContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestContextFilter(properties.getLogging()));
        registration.addUrlPatterns("/*");
        registration.setName("observabilityRequestContextFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
