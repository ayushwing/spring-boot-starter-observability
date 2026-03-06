package com.ayushwing.observability.autoconfigure.tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link TracingInterceptor} using
 * OTel's {@link InMemorySpanExporter} for in-process span verification.
 */
class TracingInterceptorTest {

    private InMemorySpanExporter spanExporter;
    private TracingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        Tracer tracer = openTelemetry.getTracer("test", "0.1.0");
        interceptor = new TracingInterceptor(tracer);
    }

    @AfterEach
    void tearDown() {
        spanExporter.reset();
    }

    @Test
    void shouldCreateServerSpanForGetRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setScheme("http");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo("GET /api/products");
        assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);
    }

    @Test
    void shouldTagSpanWithHttpMethodAndUrl() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setServerName("example.com");
        request.setServerPort(443);
        request.setScheme("https");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("http.method"))).isEqualTo("POST");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("http.route"))).isEqualTo("/api/orders");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("http.scheme"))).isEqualTo("https");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("net.host.name"))).isEqualTo("example.com");
        assertThat(span.getAttributes().get(AttributeKey.longKey("net.host.port"))).isEqualTo(443);
    }

    @Test
    void shouldSetStatusCodeAttribute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/items");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AttributeKey.longKey("http.status_code"))).isEqualTo(200);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    }

    @Test
    void shouldSetErrorStatusOn5xx() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/fail");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AttributeKey.longKey("http.status_code"))).isEqualTo(500);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    @Test
    void shouldSetUnsetStatusOn4xx() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/missing");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(404);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AttributeKey.longKey("http.status_code"))).isEqualTo(404);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
    }

    @Test
    void shouldRecordExceptionAndSetErrorStatus() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/process");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        RuntimeException exception = new RuntimeException("Processing failed");

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), exception);

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).isEqualTo("Processing failed");
        assertThat(span.getEvents()).isNotEmpty();
    }

    @Test
    void shouldTagUserAgentWhenPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
        request.addHeader("User-Agent", "Mozilla/5.0 TestAgent");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("http.user_agent")))
                .isEqualTo("Mozilla/5.0 TestAgent");
    }

    @Test
    void shouldSkipUserAgentWhenAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("http.user_agent"))).isNull();
    }

    @Test
    void shouldTagContentLengthWhenPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/upload");
        request.addHeader("Content-Length", "1024");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AttributeKey.longKey("http.request_content_length")))
                .isEqualTo(1024);
    }

    @Test
    void shouldHandleAfterCompletionWithoutPreHandle() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orphan");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // afterCompletion without preHandle should not throw
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(spanExporter.getFinishedSpanItems()).isEmpty();
    }

    @Test
    void shouldCreateDistinctSpansForDifferentRequests() throws Exception {
        MockHttpServletRequest request1 = new MockHttpServletRequest("GET", "/api/first");
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        response1.setStatus(200);
        interceptor.preHandle(request1, response1, new Object());
        interceptor.afterCompletion(request1, response1, new Object(), null);

        MockHttpServletRequest request2 = new MockHttpServletRequest("POST", "/api/second");
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        response2.setStatus(201);
        interceptor.preHandle(request2, response2, new Object());
        interceptor.afterCompletion(request2, response2, new Object(), null);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(2);
        assertThat(spans.get(0).getName()).isEqualTo("GET /api/first");
        assertThat(spans.get(1).getName()).isEqualTo("POST /api/second");
        assertThat(spans.get(0).getSpanContext().getTraceId())
                .isNotEqualTo(spans.get(1).getSpanContext().getTraceId());
    }
}
