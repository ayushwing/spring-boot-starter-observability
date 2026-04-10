package com.ayushwing.observability.autoconfigure.metrics;

import com.ayushwing.observability.core.annotation.Counted;
import com.ayushwing.observability.core.annotation.Timed;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsAspectTest {

    private SimpleMeterRegistry registry;
    private MetricsAspect aspect;

    @Mock
    private ProceedingJoinPoint pjp;
    @Mock
    private MethodSignature signature;

    @BeforeEach
    void setUp() throws Exception {
        registry = new SimpleMeterRegistry();
        aspect = new MetricsAspect(registry);

        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn((Class) TestService.class);
        when(signature.getName()).thenReturn("doWork");
    }

    @Nested
    class TimedAnnotationTests {

        @Test
        void recordsTimerOnSuccessfulInvocation() throws Throwable {
            when(pjp.proceed()).thenReturn("result");
            Timed timed = timedAnnotation("service.operation", "");

            Object result = aspect.timeMethod(pjp, timed);

            assertThat(result).isEqualTo("result");
            Timer timer = registry.find("service.operation")
                    .tags("exception", "none")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        void recordsTimerWithExceptionTagOnFailure() throws Throwable {
            when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));
            Timed timed = timedAnnotation("service.op", "");

            assertThatThrownBy(() -> aspect.timeMethod(pjp, timed))
                    .isInstanceOf(IllegalStateException.class);

            Timer timer = registry.find("service.op")
                    .tags("exception", "IllegalStateException")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        void usesDefaultNameWhenAnnotationValueIsEmpty() throws Throwable {
            when(pjp.proceed()).thenReturn(null);
            Timed timed = timedAnnotation("", "");

            aspect.timeMethod(pjp, timed);

            assertThat(registry.find("method.timed.TestService.doWork").timer()).isNotNull();
        }

        @Test
        void tagsTimerWithClassAndMethod() throws Throwable {
            when(pjp.proceed()).thenReturn(null);
            Timed timed = timedAnnotation("my.timer", "");

            aspect.timeMethod(pjp, timed);

            Timer timer = registry.find("my.timer")
                    .tags("class", "TestService", "method", "doWork")
                    .timer();
            assertThat(timer).isNotNull();
        }
    }

    @Nested
    class CountedAnnotationTests {

        @Test
        void incrementsCounterWithSuccessTagOnSuccess() throws Throwable {
            when(pjp.proceed()).thenReturn("ok");
            Counted counted = countedAnnotation("orders.processed", "");

            aspect.countMethod(pjp, counted);

            Counter counter = registry.find("orders.processed")
                    .tags("result", "success")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        void incrementsCounterWithFailureTagOnException() throws Throwable {
            when(pjp.proceed()).thenThrow(new RuntimeException("fail"));
            Counted counted = countedAnnotation("orders.processed", "");

            assertThatThrownBy(() -> aspect.countMethod(pjp, counted))
                    .isInstanceOf(RuntimeException.class);

            Counter counter = registry.find("orders.processed")
                    .tags("result", "failure")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        void usesDefaultNameWhenAnnotationValueIsEmpty() throws Throwable {
            when(pjp.proceed()).thenReturn(null);
            Counted counted = countedAnnotation("", "");

            aspect.countMethod(pjp, counted);

            assertThat(registry.find("method.counted.TestService.doWork").counter()).isNotNull();
        }

        @Test
        void countsMultipleInvocations() throws Throwable {
            when(pjp.proceed()).thenReturn(null);
            Counted counted = countedAnnotation("multi.call", "");

            aspect.countMethod(pjp, counted);
            aspect.countMethod(pjp, counted);
            aspect.countMethod(pjp, counted);

            Counter counter = registry.find("multi.call").tags("result", "success").counter();
            assertThat(counter.count()).isEqualTo(3.0);
        }
    }

    // --- helpers ---

    private Timed timedAnnotation(String value, String description) {
        return new Timed() {
            @Override public Class<Timed> annotationType() { return Timed.class; }
            @Override public String value() { return value; }
            @Override public String description() { return description; }
        };
    }

    private Counted countedAnnotation(String value, String description) {
        return new Counted() {
            @Override public Class<Counted> annotationType() { return Counted.class; }
            @Override public String value() { return value; }
            @Override public String description() { return description; }
        };
    }

    static class TestService {
        void doWork() {}
    }
}
