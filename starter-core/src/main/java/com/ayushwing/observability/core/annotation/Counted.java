package com.ayushwing.observability.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Increments a Micrometer {@code Counter} each time the annotated method is invoked.
 *
 * <p>The counter is tagged with the class name, method name, and result tag
 * ({@code "success"} or {@code "failure"}), giving you an accurate invocation count
 * broken down by outcome.
 *
 * <pre>{@code
 * @Counted("orders.processed")
 * public void processOrder(Order order) { ... }
 * }</pre>
 *
 * @see Timed
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Counted {

    /**
     * Name of the Micrometer counter metric. Defaults to {@code method.counted} when empty.
     */
    String value() default "";

    /**
     * Human-readable description for the metric registry.
     */
    String description() default "";
}
