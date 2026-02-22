package com.ayushwing.observability.autoconfigure.tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC interceptor that creates and enriches OpenTelemetry spans
 * for every incoming HTTP request.
 *
 * <p>Each request gets a SERVER span tagged with:
 * <ul>
 *   <li>{@code http.method} — GET, POST, PUT, DELETE, etc.</li>
 *   <li>{@code http.url} — full request URL</li>
 *   <li>{@code http.route} — servlet path (URI)</li>
 *   <li>{@code http.status_code} — response status code</li>
 *   <li>{@code http.scheme} — http or https</li>
 *   <li>{@code http.user_agent} — User-Agent header value</li>
 *   <li>{@code net.host.name} — server name</li>
 *   <li>{@code net.host.port} — server port</li>
 * </ul>
 *
 * <p>Spans are stored as request attributes so they can be ended in
 * {@link #afterCompletion} regardless of success or failure.
 */
public class TracingInterceptor implements HandlerInterceptor {

    private static final String SPAN_ATTRIBUTE = TracingInterceptor.class.getName() + ".span";
    private static final String SCOPE_ATTRIBUTE = TracingInterceptor.class.getName() + ".scope";

    private final Tracer tracer;

    public TracingInterceptor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {

        String spanName = request.getMethod() + " " + request.getRequestURI();

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.SERVER)
                .setParent(Context.current())
                .startSpan();

        // Tag with HTTP request metadata
        span.setAttribute(AttributeKey.stringKey("http.method"), request.getMethod());
        span.setAttribute(AttributeKey.stringKey("http.url"), request.getRequestURL().toString());
        span.setAttribute(AttributeKey.stringKey("http.route"), request.getRequestURI());
        span.setAttribute(AttributeKey.stringKey("http.scheme"), request.getScheme());
        span.setAttribute(AttributeKey.stringKey("net.host.name"), request.getServerName());
        span.setAttribute(AttributeKey.longKey("net.host.port"), request.getServerPort());

        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null) {
            span.setAttribute(AttributeKey.stringKey("http.user_agent"), userAgent);
        }

        String contentLength = request.getHeader("Content-Length");
        if (contentLength != null) {
            try {
                span.setAttribute(AttributeKey.longKey("http.request_content_length"),
                        Long.parseLong(contentLength));
            } catch (NumberFormatException ignored) {
                // skip if not a valid number
            }
        }

        // Make span current and store for cleanup in afterCompletion
        Scope scope = span.makeCurrent();
        request.setAttribute(SPAN_ATTRIBUTE, span);
        request.setAttribute(SCOPE_ATTRIBUTE, scope);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {

        Span span = (Span) request.getAttribute(SPAN_ATTRIBUTE);
        Scope scope = (Scope) request.getAttribute(SCOPE_ATTRIBUTE);

        if (span == null) {
            return;
        }

        try {
            // Tag with response metadata
            int statusCode = response.getStatus();
            span.setAttribute(AttributeKey.longKey("http.status_code"), statusCode);

            if (ex != null) {
                span.setStatus(StatusCode.ERROR, ex.getMessage());
                span.recordException(ex);
            } else if (statusCode >= 500) {
                span.setStatus(StatusCode.ERROR, "HTTP " + statusCode);
            } else if (statusCode >= 400) {
                // 4xx is not an error on the server span per OTel conventions,
                // but we still mark it for visibility
                span.setStatus(StatusCode.UNSET);
            } else {
                span.setStatus(StatusCode.OK);
            }
        } finally {
            if (scope != null) {
                scope.close();
            }
            span.end();
        }
    }
}
