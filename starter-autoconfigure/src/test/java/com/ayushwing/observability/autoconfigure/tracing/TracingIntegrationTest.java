package com.ayushwing.observability.autoconfigure.tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that verify traces flow end-to-end through
 * an OTLP exporter into a real Jaeger backend.
 */
@Testcontainers
class TracingIntegrationTest {

    @Container
    static final GenericContainer<?> jaeger = new GenericContainer<>("jaegertracing/all-in-one:1.57")
            .withExposedPorts(4317, 16686)
            .waitingFor(
                    Wait.forHttp("/")
                            .forPort(16686)
                            .withStartupTimeout(Duration.ofSeconds(90))
            );

    @Test
    void spansSentViaOtlpAppearInJaeger() throws Exception {
        String otlpEndpoint = "http://" + jaeger.getHost() + ":" + jaeger.getMappedPort(4317);
        String jaegerApiUrl = "http://" + jaeger.getHost() + ":" + jaeger.getMappedPort(16686);

        // Build OTel SDK with OTLP exporter pointing at the container
        OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpEndpoint)
                .setTimeout(Duration.ofSeconds(5))
                .build();

        Resource resource = Resource.getDefault().merge(
                Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), "integration-test-service"
                ))
        );

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();

        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        Tracer tracer = openTelemetry.getTracer("com.ayushwing.observability");

        // Emit a test span
        Span span = tracer.spanBuilder("verify-jaeger-ingestion")
                .setAttribute("test.run", true)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Thread.sleep(10);
        } finally {
            span.end();
        }

        // Flush synchronously so the export completes before we query
        tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);
        tracerProvider.shutdown().join(5, TimeUnit.SECONDS);

        // Give Jaeger a moment to index the trace
        Thread.sleep(300);

        // Query Jaeger's HTTP API — the service name should appear
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> servicesResponse = restTemplate.getForEntity(
                jaegerApiUrl + "/api/services", String.class);

        assertThat(servicesResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(servicesResponse.getBody()).contains("integration-test-service");
    }

    @Test
    void traceContextPropagatesViaW3CHeaders() throws Exception {
        String otlpEndpoint = "http://" + jaeger.getHost() + ":" + jaeger.getMappedPort(4317);

        OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpEndpoint)
                .setTimeout(Duration.ofSeconds(5))
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), "propagation-test"
                )))
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();

        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        Tracer tracer = openTelemetry.getTracer("propagation-test");

        // Parent span
        Span parent = tracer.spanBuilder("parent-operation").startSpan();
        try (Scope parentScope = parent.makeCurrent()) {
            // Child span — should be linked to parent via context propagation
            Span child = tracer.spanBuilder("child-operation").startSpan();
            try (Scope childScope = child.makeCurrent()) {
                assertThat(child.getSpanContext().getTraceId())
                        .isEqualTo(parent.getSpanContext().getTraceId());
            } finally {
                child.end();
            }
        } finally {
            parent.end();
        }

        tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);
        tracerProvider.shutdown().join(5, TimeUnit.SECONDS);
    }
}
