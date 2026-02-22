package com.ayushwing.observability.autoconfigure.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka {@link ProducerInterceptor} that propagates OpenTelemetry trace context
 * into Kafka message headers before a record is sent.
 *
 * <p>For every outgoing record this interceptor:
 * <ul>
 *   <li>Creates a PRODUCER span named {@code kafka publish <topic>}</li>
 *   <li>Injects {@code traceparent} and {@code traceId} / {@code spanId} headers</li>
 *   <li>Tags the span with {@code messaging.system}, {@code messaging.destination}, and {@code messaging.operation}</li>
 * </ul>
 *
 * <p>The span is ended immediately after headers are injected because the actual
 * send is asynchronous. The {@link #onAcknowledgement} callback is not used for
 * span lifecycle since it may be called on a background thread.
 */
public class TracingKafkaProducerInterceptor implements ProducerInterceptor<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger(TracingKafkaProducerInterceptor.class);

    private final Tracer tracer;

    public TracingKafkaProducerInterceptor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
        String topic = record.topic();

        Span span = tracer.spanBuilder("kafka publish " + topic)
                .setSpanKind(SpanKind.PRODUCER)
                .setParent(Context.current())
                .startSpan();

        try {
            // Tag with messaging metadata per OTel semantic conventions
            span.setAttribute("messaging.system", "kafka");
            span.setAttribute("messaging.destination", topic);
            span.setAttribute("messaging.operation", "publish");

            if (record.partition() != null) {
                span.setAttribute("messaging.kafka.partition", record.partition());
            }

            // Inject trace context into Kafka headers for downstream consumers
            String traceId = span.getSpanContext().getTraceId();
            String spanId = span.getSpanContext().getSpanId();
            String traceFlags = span.getSpanContext().getTraceFlags().asHex();

            record.headers().add("traceId", traceId.getBytes(StandardCharsets.UTF_8));
            record.headers().add("spanId", spanId.getBytes(StandardCharsets.UTF_8));

            // W3C traceparent header: version-traceId-spanId-traceFlags
            String traceparent = "00-" + traceId + "-" + spanId + "-" + traceFlags;
            record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));

            log.debug("Injected trace context into Kafka headers: topic={}, traceId={}", topic, traceId);
        } finally {
            span.end();
        }

        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // No-op: span is ended in onSend since ack is asynchronous
    }

    @Override
    public void close() {
        // No resources to clean up
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // Configuration handled via constructor injection
    }
}
