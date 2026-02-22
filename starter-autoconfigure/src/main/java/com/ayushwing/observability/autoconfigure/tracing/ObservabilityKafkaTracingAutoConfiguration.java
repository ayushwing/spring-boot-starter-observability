package com.ayushwing.observability.autoconfigure.tracing;

import io.opentelemetry.api.trace.Tracer;

import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Kafka trace context propagation.
 *
 * <p>Activates when:
 * <ul>
 *   <li>Spring Kafka is on the classpath ({@link ProducerInterceptor})</li>
 *   <li>A {@link Tracer} bean is available (provided by {@link ObservabilityTracingAutoConfiguration})</li>
 *   <li>{@code observability.tracing.enabled} is true (default)</li>
 * </ul>
 *
 * <p>Provides:
 * <ul>
 *   <li>{@link TracingKafkaProducerInterceptor} — injects trace context into outgoing records</li>
 *   <li>{@link TracingKafkaConsumerInterceptor} — extracts trace context from incoming records</li>
 * </ul>
 *
 * <p>These interceptors are registered as Spring beans. To wire them into Kafka,
 * configure them in your producer/consumer factory or reference them in
 * {@code spring.kafka.producer.properties} / {@code spring.kafka.consumer.properties}.
 */
@AutoConfiguration(after = ObservabilityTracingAutoConfiguration.class)
@ConditionalOnClass({ProducerInterceptor.class, ConsumerInterceptor.class})
@ConditionalOnBean(Tracer.class)
@ConditionalOnProperty(prefix = "observability.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityKafkaTracingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityKafkaTracingAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public TracingKafkaProducerInterceptor tracingKafkaProducerInterceptor(Tracer tracer) {
        log.info("Registering Kafka producer interceptor for trace context propagation");
        return new TracingKafkaProducerInterceptor(tracer);
    }

    @Bean
    @ConditionalOnMissingBean
    public TracingKafkaConsumerInterceptor tracingKafkaConsumerInterceptor(Tracer tracer) {
        log.info("Registering Kafka consumer interceptor for trace context extraction");
        return new TracingKafkaConsumerInterceptor(tracer);
    }
}
