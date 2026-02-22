package com.ayushwing.observability.autoconfigure.tracing;

import com.ayushwing.observability.autoconfigure.ObservabilityProperties;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * Auto-configuration for OpenTelemetry distributed tracing.
 *
 * <p>Activates when:
 * <ul>
 *   <li>OpenTelemetry SDK is on the classpath</li>
 *   <li>OTLP exporter is on the classpath</li>
 *   <li>{@code observability.tracing.enabled} is true (default)</li>
 * </ul>
 *
 * <p>Configures:
 * <ul>
 *   <li>{@link SdkTracerProvider} with OTLP gRPC exporter</li>
 *   <li>Configurable sampling ratio</li>
 *   <li>Service name from properties or spring.application.name</li>
 *   <li>Graceful shutdown with configurable timeout</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass({OpenTelemetrySdk.class, OtlpGrpcSpanExporter.class})
@ConditionalOnProperty(prefix = "observability.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityTracingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityTracingAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public OtlpGrpcSpanExporter otlpGrpcSpanExporter(ObservabilityProperties properties) {
        ObservabilityProperties.Tracing tracingProps = properties.getTracing();
        log.info("Configuring OTLP gRPC span exporter with endpoint: {}", tracingProps.getEndpoint());

        return OtlpGrpcSpanExporter.builder()
                .setEndpoint(tracingProps.getEndpoint())
                .setTimeout(Duration.ofMillis(tracingProps.getExporterTimeoutMs()))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public SdkTracerProvider sdkTracerProvider(
            OtlpGrpcSpanExporter spanExporter,
            ObservabilityProperties properties,
            @Value("${spring.application.name:unknown-service}") String applicationName) {

        ObservabilityProperties.Tracing tracingProps = properties.getTracing();

        String serviceName = tracingProps.getServiceName();
        if (serviceName == null || serviceName.isBlank()) {
            serviceName = applicationName;
        }

        Resource resource = Resource.getDefault().merge(
                Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), serviceName
                ))
        );

        Sampler sampler;
        double ratio = tracingProps.getSamplingRatio();
        if (ratio >= 1.0) {
            sampler = Sampler.alwaysOn();
        } else if (ratio <= 0.0) {
            sampler = Sampler.alwaysOff();
        } else {
            sampler = Sampler.traceIdRatioBased(ratio);
        }

        log.info("Configuring tracer provider: service={}, sampling={}", serviceName, ratio);

        return SdkTracerProvider.builder()
                .setResource(resource)
                .setSampler(sampler)
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenTelemetry openTelemetry(SdkTracerProvider tracerProvider) {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public Tracer observabilityTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("com.ayushwing.observability", "0.1.0");
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public TracingInterceptor tracingInterceptor(Tracer tracer) {
        log.info("Registering TracingInterceptor for HTTP span enrichment");
        return new TracingInterceptor(tracer);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public TracingWebMvcConfigurer tracingWebMvcConfigurer(TracingInterceptor tracingInterceptor) {
        return new TracingWebMvcConfigurer(tracingInterceptor);
    }
}
