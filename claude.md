# Spring Boot Starter Observability — Project Context

## Overview
A reusable Spring Boot starter bundling structured logging (JSON + MDC), distributed tracing (OpenTelemetry), and health metrics (Micrometer) into one dependency. Part of a 90-day GitHub contribution plan (Days 1–25).

## Project Structure
```
spring-boot-starter-observability/
├── pom.xml                          # Parent POM (Spring Boot 3.2.4, OTel 1.36.0, Micrometer 1.12.4)
├── starter-core/                    # Shared constants (ObservabilityConstants)
├── starter-autoconfigure/           # Auto-configuration module
│   ├── pom.xml                      # Optional deps: logback, logstash-encoder 7.4, servlet-api, spring-web, spring-webmvc, spring-kafka, otel-sdk, otel-exporter-otlp
│   └── src/main/java/com/ayushwing/observability/autoconfigure/
│       ├── ObservabilityAutoConfiguration.java   # Main entry, @Import logging + tracing configs
│       ├── ObservabilityProperties.java          # @ConfigurationProperties("observability") with Logging + Tracing inner classes
│       ├── logging/
│       │   ├── JsonLoggingConfigurer.java         # InitializingBean, reconfigures Logback with LogstashEncoder
│       │   ├── ObservabilityLoggingAutoConfiguration.java  # Beans: jsonLoggingConfigurer, requestContextFilter (FilterRegistrationBean)
│       │   └── RequestContextFilter.java          # OncePerRequestFilter, populates MDC (requestId, traceId, spanId, headers, custom fields)
│       └── tracing/
│           ├── ObservabilityTracingAutoConfiguration.java  # Beans: otlpGrpcSpanExporter, sdkTracerProvider, openTelemetry, tracer, tracingInterceptor, tracingWebMvcConfigurer
│           ├── ObservabilityKafkaTracingAutoConfiguration.java  # Beans: tracingKafkaProducerInterceptor, tracingKafkaConsumerInterceptor (conditional on spring-kafka)
│           ├── TracingInterceptor.java            # HandlerInterceptor, creates SERVER spans with HTTP metadata
│           ├── TracingWebMvcConfigurer.java        # Registers TracingInterceptor on /**
│           ├── TracingKafkaProducerInterceptor.java  # Injects traceId/spanId/traceparent into outgoing Kafka records
│           └── TracingKafkaConsumerInterceptor.java  # Extracts trace context from incoming Kafka records
├── starter-sample/                  # Sample app with application.yml demo
└── .gitignore, LICENSE, README.md
```

## Key Patterns
- `@AutoConfiguration` + `@ConditionalOnClass` + `@ConditionalOnProperty` + `@ConditionalOnMissingBean`
- `@ConditionalOnWebApplication(SERVLET)` for servlet-only beans
- `@EnableConfigurationProperties(ObservabilityProperties.class)`
- Optional Maven dependencies for classpath-conditional activation
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` for discovery
- `META-INF/additional-spring-configuration-metadata.json` for IDE support
- Tests use custom `CapturingFilterChain` (not MockFilterChain with lambdas — Spring 6 incompatible)
- Used `InitializingBean` instead of `@PostConstruct` (no jakarta.annotation dependency)

## Git Workflow
- Branch: `master` (NOT main)
- Feature branches: `feat/structured-logging`, `feat/tracing`, etc.
- Conventional commits: `feat:`, `test:`, `fix:`, `refactor:`
- PRs via `gh pr create` against `master`
- Repo: `ayushwing/spring-boot-starter-observability`
- NEVER include "🤖 Generated with Claude Code" anywhere
- NEVER include "Co-Authored-By: Claude" or any Claude/AI attribution in commits, PRs, or anywhere
- Commits must look fully human-authored — no AI co-author trailers

## Configuration Properties
- `observability.logging.*`: format (json/text), includeHeaders, headerFilter, includeMdc, includeRequestInfo, serviceName, customFields (Map)
- `observability.tracing.*`: enabled, endpoint, serviceName, samplingRatio, exporterTimeoutMs

## Completed Days
| Day | Branch | What was done | Commit |
|-----|--------|--------------|--------|
| 1 | master | Init project skeleton | `feat: init project structure with multi-module maven setup` |
| 2 | feat/structured-logging | JSON logging auto-config | `feat: add structured JSON logging auto-configuration` |
| 3 | feat/structured-logging | MDC trace context filter | `feat: inject trace context into MDC for structured logs` |
| 4 | feat/structured-logging | Unit tests for filter (21 tests) | `test: add unit tests for RequestContextFilter` |
| 5 | feat/structured-logging | Configurable log fields + metadata | `feat: make log fields configurable via properties` |
| 6 | feat/tracing | OTel tracing auto-config | `feat: add OpenTelemetry tracing auto-configuration` |
| 7 | feat/tracing | TracingInterceptor + span enrichment | `feat: enrich spans with HTTP request metadata` |
| 8 | feat/tracing | Kafka trace context propagation (producer + consumer interceptors) | 4 commits: build dep, producer, consumer, auto-config |

## Day Plan (upcoming)
| Day | Branch | Task | Commit |
|-----|--------|------|--------|
| 9 | feat/tracing | Integration tests with Testcontainers + Jaeger | `test: add tracing integration tests with Jaeger` |
| 10 | feat/metrics | Micrometer auto-config + request timing | `feat: add Micrometer metrics auto-configuration` |

## Important Notes
- Java 17, Spring Boot 3.2.4
- OpenTelemetry SDK 1.36.0
- All deps in autoconfigure pom are `<optional>true</optional>`
- Tests: 21 passing (RequestContextFilterTest)
- Make multiple small commits per day to look natural (not AI-generated)
