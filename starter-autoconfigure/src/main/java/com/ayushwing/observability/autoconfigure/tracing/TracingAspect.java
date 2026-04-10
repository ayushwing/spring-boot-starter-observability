package com.ayushwing.observability.autoconfigure.tracing;

import com.ayushwing.observability.core.annotation.Traced;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * AOP aspect that creates an OpenTelemetry span for every method annotated with {@link Traced}.
 *
 * <p>The span is automatically named from the annotation value, or falls back to
 * {@code ClassName.methodName}. Exceptions are recorded as span events when
 * {@link Traced#recordException()} is true, and the span status is set to ERROR.
 *
 * <p>Registered as a Spring bean by {@link ObservabilityTracingAutoConfiguration}
 * when AspectJ is on the classpath.
 */
@Aspect
public class TracingAspect {

    private final Tracer tracer;

    public TracingAspect(Tracer tracer) {
        this.tracer = tracer;
    }

    @Around("@annotation(traced)")
    public Object traceMethod(ProceedingJoinPoint pjp, Traced traced) throws Throwable {
        String spanName = resolveSpanName(pjp, traced);

        Span span = tracer.spanBuilder(spanName)
                .setAttribute("code.namespace", pjp.getSignature().getDeclaringTypeName())
                .setAttribute("code.function", pjp.getSignature().getName())
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            return pjp.proceed();
        } catch (Throwable t) {
            if (traced.recordException()) {
                span.recordException(t);
                span.setStatus(StatusCode.ERROR, t.getMessage());
            }
            throw t;
        } finally {
            span.end();
        }
    }

    @Around("@within(traced) && !@annotation(com.ayushwing.observability.core.annotation.Traced)")
    public Object traceClass(ProceedingJoinPoint pjp, Traced traced) throws Throwable {
        return traceMethod(pjp, traced);
    }

    private String resolveSpanName(ProceedingJoinPoint pjp, Traced traced) {
        if (!traced.value().isBlank()) {
            return traced.value();
        }
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        return sig.getDeclaringType().getSimpleName() + "." + sig.getName();
    }
}
