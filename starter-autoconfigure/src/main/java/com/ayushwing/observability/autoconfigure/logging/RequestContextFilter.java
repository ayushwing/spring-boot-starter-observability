package com.ayushwing.observability.autoconfigure.logging;

import com.ayushwing.observability.autoconfigure.ObservabilityProperties;
import com.ayushwing.observability.core.ObservabilityConstants;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Servlet filter that populates the SLF4J MDC with trace context for every
 * incoming HTTP request. This ensures that all log lines emitted during
 * request processing carry traceId, spanId, and requestId.
 *
 * <p>Configurable via {@link ObservabilityProperties.Logging}:
 * <ul>
 *   <li><b>include-headers</b> — adds request headers to MDC with "header." prefix</li>
 *   <li><b>header-filter</b> — comma-separated whitelist of header names to include</li>
 *   <li><b>include-request-info</b> — adds HTTP method and URI to MDC</li>
 *   <li><b>custom-fields</b> — static key-value pairs added to every request's MDC</li>
 * </ul>
 *
 * <p>All MDC values are cleaned up after the request completes to prevent
 * leaking context into thread-pooled threads.
 */
public class RequestContextFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String SPAN_ID_HEADER = "X-Span-Id";
    private static final String MDC_HTTP_METHOD = "httpMethod";
    private static final String MDC_REQUEST_URI = "requestUri";
    private static final String MDC_HEADER_PREFIX = "header.";

    private final boolean includeHeaders;
    private final Set<String> headerFilter;
    private final boolean includeRequestInfo;
    private final Map<String, String> customFields;

    public RequestContextFilter() {
        this(false, Set.of(), true, Map.of());
    }

    public RequestContextFilter(ObservabilityProperties.Logging loggingProps) {
        this.includeHeaders = loggingProps.isIncludeHeaders();
        this.headerFilter = parseHeaderFilter(loggingProps.getHeaderFilter());
        this.includeRequestInfo = loggingProps.isIncludeRequestInfo();
        this.customFields = loggingProps.getCustomFields() != null
                ? loggingProps.getCustomFields() : Map.of();
    }

    private RequestContextFilter(boolean includeHeaders, Set<String> headerFilter,
                                 boolean includeRequestInfo, Map<String, String> customFields) {
        this.includeHeaders = includeHeaders;
        this.headerFilter = headerFilter;
        this.includeRequestInfo = includeRequestInfo;
        this.customFields = customFields;
    }

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

            // Add HTTP method and URI to MDC
            if (includeRequestInfo) {
                MDC.put(MDC_HTTP_METHOD, request.getMethod());
                MDC.put(MDC_REQUEST_URI, request.getRequestURI());
            }

            // Add request headers to MDC
            if (includeHeaders) {
                addHeadersToMdc(request);
            }

            // Add custom static fields
            customFields.forEach(MDC::put);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(ObservabilityConstants.REQUEST_ID_KEY);
            MDC.remove(ObservabilityConstants.TRACE_ID_KEY);
            MDC.remove(ObservabilityConstants.SPAN_ID_KEY);
            MDC.remove(MDC_HTTP_METHOD);
            MDC.remove(MDC_REQUEST_URI);
            if (includeHeaders) {
                removeHeadersFromMdc(request);
            }
            customFields.keySet().forEach(MDC::remove);
        }
    }

    private void addHeadersToMdc(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return;
        }
        for (String name : Collections.list(headerNames)) {
            if (headerFilter.isEmpty() || headerFilter.contains(name.toLowerCase())) {
                MDC.put(MDC_HEADER_PREFIX + name.toLowerCase(), request.getHeader(name));
            }
        }
    }

    private void removeHeadersFromMdc(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return;
        }
        for (String name : Collections.list(headerNames)) {
            MDC.remove(MDC_HEADER_PREFIX + name.toLowerCase());
        }
    }

    private static Set<String> parseHeaderFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return Set.of();
        }
        return Stream.of(filter.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }
}
