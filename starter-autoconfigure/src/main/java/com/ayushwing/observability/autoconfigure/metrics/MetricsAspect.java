package com.ayushwing.observability.autoconfigure.metrics;

import com.ayushwing.observability.core.annotation.Counted;
import com.ayushwing.observability.core.annotation.Timed;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * AOP aspect that applies Micrometer metrics to methods annotated with
 * {@link Timed} or {@link Counted}.
 *
 * <p>{@link Timed} records the full execution time including exception cases,
 * tagged with {@code exception} for easy error-latency breakdown.
 *
 * <p>{@link Counted} increments a counter on each invocation, tagged with
 * {@code result=success} or {@code result=failure}.
 *
 * <p>Registered by {@link ObservabilityMetricsAutoConfiguration} when AspectJ
 * and Micrometer are on the classpath.
 */
@Aspect
public class MetricsAspect {

    private final MeterRegistry meterRegistry;

    public MetricsAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(timed)")
    public Object timeMethod(ProceedingJoinPoint pjp, Timed timed) throws Throwable {
        String name = resolveName(pjp, timed.value(), "method.timed");
        String className = ((MethodSignature) pjp.getSignature()).getDeclaringType().getSimpleName();
        String methodName = pjp.getSignature().getName();
        String exceptionClass = "none";

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            exceptionClass = t.getClass().getSimpleName();
            throw t;
        } finally {
            sample.stop(Timer.builder(name)
                    .description(timed.description())
                    .tags("class", className, "method", methodName, "exception", exceptionClass)
                    .register(meterRegistry));
        }
    }

    @Around("@annotation(counted)")
    public Object countMethod(ProceedingJoinPoint pjp, Counted counted) throws Throwable {
        String name = resolveName(pjp, counted.value(), "method.counted");
        String className = ((MethodSignature) pjp.getSignature()).getDeclaringType().getSimpleName();
        String methodName = pjp.getSignature().getName();

        try {
            Object result = pjp.proceed();
            increment(name, counted.description(), className, methodName, "success");
            return result;
        } catch (Throwable t) {
            increment(name, counted.description(), className, methodName, "failure");
            throw t;
        }
    }

    private void increment(String name, String description,
                           String className, String methodName, String result) {
        Counter.builder(name)
                .description(description)
                .tags("class", className, "method", methodName, "result", result)
                .register(meterRegistry)
                .increment();
    }

    private String resolveName(ProceedingJoinPoint pjp, String annotationValue, String fallback) {
        if (!annotationValue.isBlank()) {
            return annotationValue;
        }
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        return fallback + "." + sig.getDeclaringType().getSimpleName() + "." + sig.getName();
    }
}
