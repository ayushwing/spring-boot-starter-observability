package com.ayushwing.observability.autoconfigure.logging;

import com.ayushwing.observability.core.ObservabilityConstants;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that populates the SLF4J MDC with trace context for every
 * incoming HTTP request. This ensures that all log lines emitted during
 * request processing carry traceId, spanId, and requestId.
 *
 * <p>Behavior:
 * <ul>
 *   <li><b>requestId</b> — always generated as a new UUID per request</li>
 *   <li><b>traceId</b> — read from the {@code X-Trace-Id} header if present,
 *       otherwise falls back to the requestId</li>
 *   <li><b>spanId</b> — read from the {@code X-Span-Id} header if present,
 *       otherwise a short random ID is generated</li>
 * </ul>
 *
 * <p>All MDC values are cleaned up after the request completes to prevent
 * leaking context into thread-pooled threads.
 */
public class RequestContextFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String SPAN_ID_HEADER = "X-Span-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String requestId = UUID.randomUUID().toString();
            MDC.put(ObservabilityConstants.REQUEST_ID_KEY, requestId);

            // Use incoming trace header or fall back to requestId
            String traceId = request.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isBlank()) {
                traceId = requestId;
            }
            MDC.put(ObservabilityConstants.TRACE_ID_KEY, traceId);

            // Use incoming span header or generate a short random span id
            String spanId = request.getHeader(SPAN_ID_HEADER);
            if (spanId == null || spanId.isBlank()) {
                spanId = UUID.randomUUID().toString().substring(0, 8);
            }
            MDC.put(ObservabilityConstants.SPAN_ID_KEY, spanId);

            // Propagate trace ID back in response header for downstream correlation
            response.setHeader(TRACE_ID_HEADER, traceId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(ObservabilityConstants.REQUEST_ID_KEY);
            MDC.remove(ObservabilityConstants.TRACE_ID_KEY);
            MDC.remove(ObservabilityConstants.SPAN_ID_KEY);
        }
    }
}
