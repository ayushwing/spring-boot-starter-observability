package com.ayushwing.observability.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Records the execution time of the annotated method as a Micrometer {@code Timer}.
 *
 * <p>The timer is tagged with the class name, method name, and exception class
 * (or "none" on success), enabling fine-grained latency breakdowns per method.
 *
 * <pre>{@code
 * @Timed("inventory.stock.check")
 * public boolean checkStock(String productId) { ... }
 * }</pre>
 *
 * @see Counted
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Timed {

    /**
     * Name of the Micrometer timer metric. Defaults to {@code method.timed} when empty.
     */
    String value() default "";

    /**
     * Human-readable description for the metric registry.
     */
    String description() default "";
}
