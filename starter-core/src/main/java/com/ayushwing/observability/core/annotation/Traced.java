package com.ayushwing.observability.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method (or all methods on a class) for automatic OpenTelemetry span creation.
 *
 * <p>When applied, the observability starter wraps each annotated method invocation
 * in a new child span. The span is named after the method by default, or using the
 * value provided in {@link #value()}.
 *
 * <p>Requires {@code spring-aop} and {@code aspectjweaver} on the classpath, and
 * {@code @EnableAspectJAutoProxy} (provided automatically by Spring Boot's AOP
 * auto-configuration when {@code spring-boot-starter-aop} is present).
 *
 * <pre>{@code
 * @Traced
 * public Order processOrder(String orderId) { ... }
 *
 * @Traced("payment.charge")
 * public void chargeCustomer(String customerId, BigDecimal amount) { ... }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Traced {

    /**
     * Name of the span. Defaults to {@code ClassName.methodName} when empty.
     */
    String value() default "";

    /**
     * Whether exceptions should be recorded on the span as events.
     * Defaults to {@code true}.
     */
    boolean recordException() default true;
}
