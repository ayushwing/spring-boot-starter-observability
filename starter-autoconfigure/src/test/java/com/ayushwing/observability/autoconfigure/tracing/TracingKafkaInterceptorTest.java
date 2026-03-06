package com.ayushwing.observability.autoconfigure.tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TracingKafkaProducerInterceptor} and
 * {@link TracingKafkaConsumerInterceptor} verifying trace context
 * propagation through Kafka headers.
 */
class TracingKafkaInterceptorTest {

    private InMemorySpanExporter spanExporter;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        tracer = openTelemetry.getTracer("test", "0.1.0");
    }

    @AfterEach
    void tearDown() {
        spanExporter.reset();
    }

    @Nested
    class ProducerInterceptorTests {

        private TracingKafkaProducerInterceptor interceptor;

        @BeforeEach
        void setUp() {
            interceptor = new TracingKafkaProducerInterceptor(tracer);
        }

        @Test
        void shouldCreateProducerSpan() {
            ProducerRecord<Object, Object> record = new ProducerRecord<>("orders", "key", "value");
            interceptor.onSend(record);

            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            assertThat(spans).hasSize(1);
            assertThat(spans.get(0).getName()).isEqualTo("kafka publish orders");
            assertThat(spans.get(0).getKind()).isEqualTo(SpanKind.PRODUCER);
        }

        @Test
        void shouldInjectTraceIdHeader() {
            ProducerRecord<Object, Object> record = new ProducerRecord<>("events", "data");
            interceptor.onSend(record);

            Header traceIdHeader = record.headers().lastHeader("traceId");
            assertThat(traceIdHeader).isNotNull();
            String traceId = new String(traceIdHeader.value(), StandardCharsets.UTF_8);
            assertThat(traceId).hasSize(32); // OTel trace IDs are 32 hex chars
        }

        @Test
        void shouldInjectSpanIdHeader() {
            ProducerRecord<Object, Object> record = new ProducerRecord<>("events", "data");
            interceptor.onSend(record);

            Header spanIdHeader = record.headers().lastHeader("spanId");
            assertThat(spanIdHeader).isNotNull();
            String spanId = new String(spanIdHeader.value(), StandardCharsets.UTF_8);
            assertThat(spanId).hasSize(16); // OTel span IDs are 16 hex chars
        }

        @Test
        void shouldInjectTraceparentHeader() {
            ProducerRecord<Object, Object> record = new ProducerRecord<>("events", "data");
            interceptor.onSend(record);

            Header traceparentHeader = record.headers().lastHeader("traceparent");
            assertThat(traceparentHeader).isNotNull();
            String traceparent = new String(traceparentHeader.value(), StandardCharsets.UTF_8);
            // W3C format: version-traceId-spanId-traceFlags
            assertThat(traceparent).matches("00-[a-f0-9]{32}-[a-f0-9]{16}-[a-f0-9]{2}");
        }

        @Test
        void shouldTagSpanWithMessagingAttributes() {
            ProducerRecord<Object, Object> record = new ProducerRecord<>("notifications", 2, "key", "msg");
            interceptor.onSend(record);

            SpanData span = spanExporter.getFinishedSpanItems().get(0);
            assertThat(span.getAttributes().get(AttributeKey.stringKey("messaging.system"))).isEqualTo("kafka");
            assertThat(span.getAttributes().get(AttributeKey.stringKey("messaging.destination"))).isEqualTo("notifications");
            assertThat(span.getAttributes().get(AttributeKey.stringKey("messaging.operation"))).isEqualTo("publish");
        }

        @Test
        void shouldReturnSameRecord() {
            ProducerRecord<Object, Object> record = new ProducerRecord<>("topic", "value");
            ProducerRecord<Object, Object> result = interceptor.onSend(record);
            assertThat(result).isSameAs(record);
        }
    }

    @Nested
    class ConsumerInterceptorTests {

        private TracingKafkaConsumerInterceptor interceptor;

        @BeforeEach
        void setUp() {
            interceptor = new TracingKafkaConsumerInterceptor(tracer);
        }

        @Test
        void shouldCreateConsumerSpanForEachRecord() {
            ConsumerRecord<Object, Object> record = new ConsumerRecord<>("orders", 0, 42L, "key", "value");
            ConsumerRecords<Object, Object> records = buildRecords("orders", record);

            interceptor.onConsume(records);

            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            assertThat(spans).hasSize(1);
            assertThat(spans.get(0).getName()).isEqualTo("kafka consume orders");
            assertThat(spans.get(0).getKind()).isEqualTo(SpanKind.CONSUMER);
        }

        @Test
        void shouldTagConsumerSpanWithMessagingAttributes() {
            ConsumerRecord<Object, Object> record = new ConsumerRecord<>("events", 3, 100L, "k", "v");
            ConsumerRecords<Object, Object> records = buildRecords("events", record);

            interceptor.onConsume(records);

            SpanData span = spanExporter.getFinishedSpanItems().get(0);
            assertThat(span.getAttributes().get(AttributeKey.stringKey("messaging.system"))).isEqualTo("kafka");
            assertThat(span.getAttributes().get(AttributeKey.stringKey("messaging.destination"))).isEqualTo("events");
            assertThat(span.getAttributes().get(AttributeKey.stringKey("messaging.operation"))).isEqualTo("consume");
            assertThat(span.getAttributes().get(AttributeKey.longKey("messaging.kafka.partition"))).isEqualTo(3);
            assertThat(span.getAttributes().get(AttributeKey.longKey("messaging.kafka.offset"))).isEqualTo(100);
        }

        @Test
        void shouldExtractTraceContextFromTraceparentHeader() {
            // Simulate a producer sending a traceparent header
            ProducerRecord<Object, Object> produced = new ProducerRecord<>("topic", "value");
            TracingKafkaProducerInterceptor producer = new TracingKafkaProducerInterceptor(tracer);
            producer.onSend(produced);

            SpanData producerSpan = spanExporter.getFinishedSpanItems().get(0);
            String expectedTraceId = producerSpan.getTraceId();

            // Build consumer record with the producer's headers
            ConsumerRecord<Object, Object> consumerRecord = new ConsumerRecord<>("topic", 0, 0L, null, "value");
            for (Header header : produced.headers()) {
                consumerRecord.headers().add(header);
            }
            ConsumerRecords<Object, Object> records = buildRecords("topic", consumerRecord);

            interceptor.onConsume(records);

            List<SpanData> allSpans = spanExporter.getFinishedSpanItems();
            SpanData consumerSpan = allSpans.get(1); // second span is the consumer
            // Consumer span should share the same traceId as the producer
            assertThat(consumerSpan.getTraceId()).isEqualTo(expectedTraceId);
        }

        @Test
        void shouldHandleRecordWithoutTraceHeaders() {
            ConsumerRecord<Object, Object> record = new ConsumerRecord<>("raw-topic", 0, 0L, null, "data");
            ConsumerRecords<Object, Object> records = buildRecords("raw-topic", record);

            interceptor.onConsume(records);

            // Should still create a span, just without parent linkage
            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            assertThat(spans).hasSize(1);
            assertThat(spans.get(0).getName()).isEqualTo("kafka consume raw-topic");
        }

        @Test
        void shouldReturnSameRecords() {
            ConsumerRecord<Object, Object> record = new ConsumerRecord<>("t", 0, 0L, null, "v");
            ConsumerRecords<Object, Object> records = buildRecords("t", record);

            ConsumerRecords<Object, Object> result = interceptor.onConsume(records);
            assertThat(result).isSameAs(records);
        }

        @SafeVarargs
        private ConsumerRecords<Object, Object> buildRecords(String topic, ConsumerRecord<Object, Object>... records) {
            TopicPartition tp = new TopicPartition(topic, 0);
            Map<TopicPartition, List<ConsumerRecord<Object, Object>>> map = new HashMap<>();
            map.put(tp, List.of(records));
            return new ConsumerRecords<>(map);
        }
    }
}
