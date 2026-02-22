package com.ayushwing.observability.autoconfigure.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka {@link ConsumerInterceptor} that extracts OpenTelemetry trace context
 * from incoming Kafka message headers and creates CONSUMER spans.
 *
 * <p>For every consumed record batch this interceptor:
 * <ul>
 *   <li>Extracts {@code traceparent} header (W3C format) or falls back to
 *       {@code traceId}/{@code spanId} headers</li>
 *   <li>Creates a CONSUMER span linked to the producer's span context</li>
 *   <li>Tags the span with {@code messaging.system}, {@code messaging.destination},
 *       {@code messaging.operation}, and {@code messaging.kafka.partition}</li>
 * </ul>
 *
 * <p>A single span is created per record to maintain proper trace linkage.
 * Spans are ended immediately after creation since the actual processing
 * happens downstream in the application's message listener.
 */
public class TracingKafkaConsumerInterceptor implements ConsumerInterceptor<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger(TracingKafkaConsumerInterceptor.class);

    private final Tracer tracer;

    public TracingKafkaConsumerInterceptor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public ConsumerRecords<Object, Object> onConsume(ConsumerRecords<Object, Object> records) {
        for (ConsumerRecord<Object, Object> record : records) {
            processRecord(record);
        }
        return records;
    }

    private void processRecord(ConsumerRecord<Object, Object> record) {
        String topic = record.topic();
        Context parentContext = extractParentContext(record);

        Span span = tracer.spanBuilder("kafka consume " + topic)
                .setSpanKind(SpanKind.CONSUMER)
                .setParent(parentContext)
                .startSpan();

        try {
            span.setAttribute("messaging.system", "kafka");
            span.setAttribute("messaging.destination", topic);
            span.setAttribute("messaging.operation", "consume");
            span.setAttribute("messaging.kafka.partition", record.partition());
            span.setAttribute("messaging.kafka.offset", record.offset());

            if (record.key() != null) {
                span.setAttribute("messaging.kafka.message_key", record.key().toString());
            }

            log.debug("Extracted trace context from Kafka record: topic={}, partition={}, offset={}",
                    topic, record.partition(), record.offset());
        } finally {
            span.end();
        }
    }

    /**
     * Extracts the parent span context from Kafka headers.
     * Tries W3C {@code traceparent} first, then falls back to individual
     * {@code traceId} and {@code spanId} headers.
     */
    private Context extractParentContext(ConsumerRecord<Object, Object> record) {
        // Try W3C traceparent header first
        String traceparent = getHeaderValue(record, "traceparent");
        if (traceparent != null) {
            SpanContext remoteContext = parseTraceparent(traceparent);
            if (remoteContext != null && remoteContext.isValid()) {
                return Context.current().with(Span.wrap(remoteContext));
            }
        }

        // Fall back to individual traceId/spanId headers
        String traceId = getHeaderValue(record, "traceId");
        String spanId = getHeaderValue(record, "spanId");

        if (traceId != null && spanId != null) {
            SpanContext remoteContext = SpanContext.createFromRemoteParent(
                    traceId, spanId, TraceFlags.getDefault(), TraceState.getDefault());
            if (remoteContext.isValid()) {
                return Context.current().with(Span.wrap(remoteContext));
            }
        }

        return Context.current();
    }

    /**
     * Parses a W3C traceparent header: {@code version-traceId-spanId-traceFlags}.
     */
    private SpanContext parseTraceparent(String traceparent) {
        try {
            String[] parts = traceparent.split("-");
            if (parts.length < 4) {
                return null;
            }
            String traceId = parts[1];
            String spanId = parts[2];
            byte flags = Byte.parseByte(parts[3], 16);

            return SpanContext.createFromRemoteParent(
                    traceId, spanId, TraceFlags.fromByte(flags), TraceState.getDefault());
        } catch (Exception e) {
            log.warn("Failed to parse traceparent header: {}", traceparent, e);
            return null;
        }
    }

    private String getHeaderValue(ConsumerRecord<Object, Object> record, String key) {
        Header header = record.headers().lastHeader(key);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return null;
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
        // No-op
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
