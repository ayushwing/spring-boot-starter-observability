package com.ayushwing.observability.autoconfigure.tracing;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link TracingInterceptor} with Spring MVC so that
 * every HTTP request is automatically instrumented with an OpenTelemetry span.
 */
public class TracingWebMvcConfigurer implements WebMvcConfigurer {

    private final TracingInterceptor tracingInterceptor;

    public TracingWebMvcConfigurer(TracingInterceptor tracingInterceptor) {
        this.tracingInterceptor = tracingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tracingInterceptor)
                .addPathPatterns("/**");
    }
}
