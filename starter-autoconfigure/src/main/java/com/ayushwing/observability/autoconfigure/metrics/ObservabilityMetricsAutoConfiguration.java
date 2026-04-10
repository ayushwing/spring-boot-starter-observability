package com.ayushwing.observability.autoconfigure.metrics;

import com.ayushwing.observability.autoconfigure.ObservabilityProperties;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Micrometer metrics integration.
 *
 * <p>Activates when {@code io.micrometer:micrometer-core} and a {@link MeterRegistry}
 * implementation are on the classpath. Provides:
 * <ul>
 *   <li>Standard JVM metrics (memory, GC, threads, class loader, CPU)</li>
 *   <li>{@link MetricsAspect} for {@code @Timed} and {@code @Counted} annotation processing</li>
 *   <li>{@link ObservabilityHealthIndicator} for observability stack health reporting</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "observability.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityMetricsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityMetricsAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(JvmMemoryMetrics.class)
    public JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics();
    }

    @Bean
    @ConditionalOnMissingBean(JvmGcMetrics.class)
    public JvmGcMetrics jvmGcMetrics() {
        return new JvmGcMetrics();
    }

    @Bean
    @ConditionalOnMissingBean(JvmThreadMetrics.class)
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }

    @Bean
    @ConditionalOnMissingBean(ClassLoaderMetrics.class)
    public ClassLoaderMetrics classLoaderMetrics() {
        return new ClassLoaderMetrics();
    }

    @Bean
    @ConditionalOnMissingBean(ProcessorMetrics.class)
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }

    @Bean
    @ConditionalOnMissingBean(MetricsAspect.class)
    @ConditionalOnClass(ProceedingJoinPoint.class)
    public MetricsAspect metricsAspect(MeterRegistry meterRegistry) {
        log.info("Registering MetricsAspect for @Timed and @Counted annotation processing");
        return new MetricsAspect(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(ObservabilityHealthIndicator.class)
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnBean(MeterRegistry.class)
    public ObservabilityHealthIndicator observabilityHealthIndicator(
            MeterRegistry meterRegistry,
            @Autowired(required = false) SdkTracerProvider tracerProvider) {
        return new ObservabilityHealthIndicator(meterRegistry, tracerProvider);
    }
}
