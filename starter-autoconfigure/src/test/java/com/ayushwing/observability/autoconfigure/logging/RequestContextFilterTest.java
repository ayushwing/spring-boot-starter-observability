package com.ayushwing.observability.autoconfigure.logging;

import com.ayushwing.observability.autoconfigure.ObservabilityProperties;
import com.ayushwing.observability.core.ObservabilityConstants;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RequestContextFilter}.
 * Verifies MDC population, header propagation, and cleanup behavior.
 */
class RequestContextFilterTest {

    private RequestContextFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new RequestContextFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    /**
     * Helper FilterChain that captures MDC values during execution.
     */
    private static class CapturingFilterChain implements FilterChain {
        String requestId;
        String traceId;
        String spanId;
        String httpMethod;
        String requestUri;
        Map<String, String> allMdc;
        boolean invoked = false;
        private final ServletException exceptionToThrow;

        CapturingFilterChain() {
            this(null);
        }

        CapturingFilterChain(ServletException exceptionToThrow) {
            this.exceptionToThrow = exceptionToThrow;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
                throws IOException, ServletException {
            invoked = true;
            requestId = MDC.get(ObservabilityConstants.REQUEST_ID_KEY);
            traceId = MDC.get(ObservabilityConstants.TRACE_ID_KEY);
            spanId = MDC.get(ObservabilityConstants.SPAN_ID_KEY);
            httpMethod = MDC.get("httpMethod");
            requestUri = MDC.get("requestUri");
            allMdc = MDC.getCopyOfContextMap();
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
        }
    }

    @Test
    @DisplayName("Should generate requestId, traceId, and spanId when no headers present")
    void shouldGenerateAllIdsWhenNoHeaders() throws ServletException, IOException {
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked).isTrue();
        assertThat(chain.requestId).isNotNull().isNotBlank();
        assertThat(chain.traceId).isNotNull().isNotBlank();
        assertThat(chain.spanId).isNotNull().isNotBlank();

        // traceId should fall back to requestId when no header
        assertThat(chain.traceId).isEqualTo(chain.requestId);
    }

    @Test
    @DisplayName("Should use X-Trace-Id header when provided")
    void shouldUseTraceIdFromHeader() throws ServletException, IOException {
        String incomingTraceId = "incoming-trace-abc123";
        request.addHeader("X-Trace-Id", incomingTraceId);

        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.traceId).isEqualTo(incomingTraceId);
        // requestId should still be independently generated
        assertThat(chain.requestId).isNotEqualTo(incomingTraceId);
    }

    @Test
    @DisplayName("Should use X-Span-Id header when provided")
    void shouldUseSpanIdFromHeader() throws ServletException, IOException {
        String incomingSpanId = "span-xyz789";
        request.addHeader("X-Span-Id", incomingSpanId);

        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.spanId).isEqualTo(incomingSpanId);
    }

    @Test
    @DisplayName("Should generate 8-char spanId when no header present")
    void shouldGenerateShortSpanId() throws ServletException, IOException {
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.spanId).hasSize(8);
    }

    @Test
    @DisplayName("Should set X-Trace-Id response header for downstream correlation")
    void shouldSetTraceIdResponseHeader() throws ServletException, IOException {
        String incomingTraceId = "trace-for-response";
        request.addHeader("X-Trace-Id", incomingTraceId);

        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(incomingTraceId);
    }

    @Test
    @DisplayName("Should set generated traceId in response header when no incoming header")
    void shouldSetGeneratedTraceIdInResponseHeader() throws ServletException, IOException {
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(chain.traceId);
    }

    @Test
    @DisplayName("Should clean up MDC after request completes")
    void shouldCleanUpMdcAfterRequest() throws ServletException, IOException {
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        // MDC should be clean after filter completes
        assertThat(MDC.get(ObservabilityConstants.REQUEST_ID_KEY)).isNull();
        assertThat(MDC.get(ObservabilityConstants.TRACE_ID_KEY)).isNull();
        assertThat(MDC.get(ObservabilityConstants.SPAN_ID_KEY)).isNull();

        // But values were present during chain execution
        assertThat(chain.requestId).isNotNull();
        assertThat(chain.traceId).isNotNull();
        assertThat(chain.spanId).isNotNull();
    }

    @Test
    @DisplayName("Should clean up MDC even when filter chain throws exception")
    void shouldCleanUpMdcOnException() {
        CapturingFilterChain chain = new CapturingFilterChain(
                new ServletException("Simulated failure"));

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(ServletException.class)
                .hasMessage("Simulated failure");

        // MDC must still be cleaned up
        assertThat(MDC.get(ObservabilityConstants.REQUEST_ID_KEY)).isNull();
        assertThat(MDC.get(ObservabilityConstants.TRACE_ID_KEY)).isNull();
        assertThat(MDC.get(ObservabilityConstants.SPAN_ID_KEY)).isNull();

        // Values were populated before the exception
        assertThat(chain.requestId).isNotNull();
    }

    @Test
    @DisplayName("Should ignore blank X-Trace-Id header and generate new traceId")
    void shouldIgnoreBlankTraceIdHeader() throws ServletException, IOException {
        request.addHeader("X-Trace-Id", "   ");

        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        // Should fall back to requestId, not use blank header
        assertThat(chain.traceId).isEqualTo(chain.requestId);
    }

    @Test
    @DisplayName("Should ignore blank X-Span-Id header and generate new spanId")
    void shouldIgnoreBlankSpanIdHeader() throws ServletException, IOException {
        request.addHeader("X-Span-Id", "  ");

        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        // Should generate a new 8-char span id, not use blank
        assertThat(chain.spanId).hasSize(8);
    }

    @Test
    @DisplayName("Should generate unique requestId for each request")
    void shouldGenerateUniqueRequestIds() throws ServletException, IOException {
        CapturingFilterChain chain1 = new CapturingFilterChain();
        CapturingFilterChain chain2 = new CapturingFilterChain();

        filter.doFilter(request, response, chain1);
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain2);

        assertThat(chain1.requestId).isNotEqualTo(chain2.requestId);
    }

    @Test
    @DisplayName("Should use valid UUID format for generated requestId")
    void shouldGenerateValidUuidRequestId() throws ServletException, IOException {
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        // requestId should be a valid UUID string
        assertThat(chain.requestId).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    // --- Day 5: Configurable log fields tests ---

    @Test
    @DisplayName("Default filter should include request info in MDC")
    void defaultFilterShouldIncludeRequestInfo() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/orders");

        CapturingFilterChain chain = new CapturingFilterChain();
        filter.doFilter(request, response, chain);

        assertThat(chain.httpMethod).isEqualTo("POST");
        assertThat(chain.requestUri).isEqualTo("/api/orders");
    }

    @Test
    @DisplayName("Should clean up request info MDC keys after request")
    void shouldCleanUpRequestInfoMdc() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/health");

        CapturingFilterChain chain = new CapturingFilterChain();
        filter.doFilter(request, response, chain);

        assertThat(MDC.get("httpMethod")).isNull();
        assertThat(MDC.get("requestUri")).isNull();
    }

    @Nested
    @DisplayName("With properties-configured filter")
    class ConfiguredFilterTests {

        private ObservabilityProperties.Logging loggingProps;

        @BeforeEach
        void setUp() {
            loggingProps = new ObservabilityProperties.Logging();
        }

        @Test
        @DisplayName("Should include all request headers in MDC when include-headers is true")
        void shouldIncludeAllHeaders() throws ServletException, IOException {
            loggingProps.setIncludeHeaders(true);
            RequestContextFilter configuredFilter = new RequestContextFilter(loggingProps);

            request.addHeader("Content-Type", "application/json");
            request.addHeader("Accept", "text/html");

            CapturingFilterChain chain = new CapturingFilterChain();
            configuredFilter.doFilter(request, response, chain);

            assertThat(chain.allMdc).containsEntry("header.content-type", "application/json");
            assertThat(chain.allMdc).containsEntry("header.accept", "text/html");
        }

        @Test
        @DisplayName("Should filter headers by header-filter whitelist")
        void shouldFilterHeaders() throws ServletException, IOException {
            loggingProps.setIncludeHeaders(true);
            loggingProps.setHeaderFilter("Content-Type,Accept");
            RequestContextFilter configuredFilter = new RequestContextFilter(loggingProps);

            request.addHeader("Content-Type", "application/json");
            request.addHeader("Accept", "text/html");
            request.addHeader("Authorization", "Bearer secret");

            CapturingFilterChain chain = new CapturingFilterChain();
            configuredFilter.doFilter(request, response, chain);

            assertThat(chain.allMdc).containsEntry("header.content-type", "application/json");
            assertThat(chain.allMdc).containsEntry("header.accept", "text/html");
            assertThat(chain.allMdc).doesNotContainKey("header.authorization");
        }

        @Test
        @DisplayName("Should not include headers when include-headers is false")
        void shouldNotIncludeHeadersWhenDisabled() throws ServletException, IOException {
            loggingProps.setIncludeHeaders(false);
            RequestContextFilter configuredFilter = new RequestContextFilter(loggingProps);

            request.addHeader("Content-Type", "application/json");

            CapturingFilterChain chain = new CapturingFilterChain();
            configuredFilter.doFilter(request, response, chain);

            assertThat(chain.allMdc).doesNotContainKey("header.content-type");
        }

        @Test
        @DisplayName("Should not include request info when disabled")
        void shouldNotIncludeRequestInfoWhenDisabled() throws ServletException, IOException {
            loggingProps.setIncludeRequestInfo(false);
            RequestContextFilter configuredFilter = new RequestContextFilter(loggingProps);

            request.setMethod("DELETE");
            request.setRequestURI("/api/users/1");

            CapturingFilterChain chain = new CapturingFilterChain();
            configuredFilter.doFilter(request, response, chain);

            assertThat(chain.httpMethod).isNull();
            assertThat(chain.requestUri).isNull();
        }

        @Test
        @DisplayName("Should add custom fields to MDC")
        void shouldAddCustomFields() throws ServletException, IOException {
            Map<String, String> custom = new LinkedHashMap<>();
            custom.put("environment", "staging");
            custom.put("region", "us-east-1");
            loggingProps.setCustomFields(custom);
            RequestContextFilter configuredFilter = new RequestContextFilter(loggingProps);

            CapturingFilterChain chain = new CapturingFilterChain();
            configuredFilter.doFilter(request, response, chain);

            assertThat(chain.allMdc).containsEntry("environment", "staging");
            assertThat(chain.allMdc).containsEntry("region", "us-east-1");
        }

        @Test
        @DisplayName("Should clean up custom fields from MDC after request")
        void shouldCleanUpCustomFields() throws ServletException, IOException {
            Map<String, String> custom = new LinkedHashMap<>();
            custom.put("env", "test");
            loggingProps.setCustomFields(custom);
            RequestContextFilter configuredFilter = new RequestContextFilter(loggingProps);

            CapturingFilterChain chain = new CapturingFilterChain();
            configuredFilter.doFilter(request, response, chain);

            assertThat(MDC.get("env")).isNull();
        }

        @Test
        @DisplayName("Should clean up header MDC keys after request")
        void shouldCleanUpHeaderMdcKeys() throws ServletException, IOException {
            loggingProps.setIncludeHeaders(true);
            RequestContextFilter configuredFilter = new RequestContextFilter(loggingProps);

            request.addHeader("X-Custom", "value");

            CapturingFilterChain chain = new CapturingFilterChain();
            configuredFilter.doFilter(request, response, chain);

            assertThat(MDC.get("header.x-custom")).isNull();
        }
    }
}
