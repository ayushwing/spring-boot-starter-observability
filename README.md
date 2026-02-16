# Spring Boot Starter Observability

[![Build Status](https://github.com/ayushwing/spring-boot-starter-observability/actions/workflows/build.yml/badge.svg)](https://github.com/ayushwing/spring-boot-starter-observability/actions)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green.svg)](https://spring.io/projects/spring-boot)

A reusable Spring Boot starter that bundles **structured logging**, **distributed tracing** (OpenTelemetry), and **health metrics** (Micrometer) into a single dependency. Drop it into any Spring Boot 3.x app and get production-grade observability out of the box.

## Why?

Every microservice needs observability, but wiring up structured logging, distributed tracing, and metrics from scratch is tedious and error-prone. Teams end up with inconsistent setups across services — different log formats, missing trace context, no correlation between logs and traces.

This starter solves that by providing a single, opinionated dependency that gives you:

- **Structured JSON logging** with trace context (traceId, spanId, requestId) injected into every log line
- **Distributed tracing** via OpenTelemetry with automatic span creation for HTTP and Kafka
- **Metrics** via Micrometer with custom annotation support (`@Timed`, `@Counted`)
- **Correlation IDs** that propagate across service boundaries
- **Graceful degradation** when exporters are unreachable

## Architecture

```
┌─────────────────────────────────────────────────┐
│              Your Spring Boot App                │
│                                                  │
│  ┌───────────────────────────────────────────┐   │
│  │   spring-boot-starter-observability       │   │
│  │                                           │   │
│  │  ┌─────────────┐  ┌──────────────────┐   │   │
│  │  │  Structured  │  │   Distributed    │   │   │
│  │  │  Logging     │  │   Tracing        │   │   │
│  │  │  (Logback +  │  │   (OpenTelemetry │   │   │
│  │  │   MDC)       │  │    + OTLP)       │   │   │
│  │  └─────────────┘  └──────────────────┘   │   │
│  │                                           │   │
│  │  ┌─────────────┐  ┌──────────────────┐   │   │
│  │  │  Metrics     │  │  Correlation ID  │   │   │
│  │  │  (Micrometer │  │  Propagation     │   │   │
│  │  │   + custom)  │  │                  │   │   │
│  │  └─────────────┘  └──────────────────┘   │   │
│  └───────────────────────────────────────────┘   │
│                                                  │
│       ┌──────────┐  ┌────────┐  ┌──────────┐    │
│       │  Jaeger   │  │ Prom.  │  │ Grafana  │    │
│       └──────────┘  └────────┘  └──────────┘    │
└─────────────────────────────────────────────────┘
```

## Modules

| Module | Description |
|--------|-------------|
| `starter-core` | Core abstractions, annotations, and utilities |
| `starter-autoconfigure` | Spring Boot auto-configuration for all observability components |
| `starter-sample` | Sample app demonstrating the starter in action |

## Quick Start

> **Note:** This library is under active development. Maven Central publishing coming with v1.0.

1. Build locally:
```bash
mvn clean install
```

2. Add the dependency to your Spring Boot app:
```xml
<dependency>
    <groupId>com.ayushwing</groupId>
    <artifactId>observability-starter-autoconfigure</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

3. Configure (optional — sensible defaults provided):
```yaml
observability:
  logging:
    format: json
    include-headers: true
  tracing:
    endpoint: http://localhost:4317
    service-name: ${spring.application.name}
  metrics:
    enabled: true
```

## Roadmap

- [x] Project structure and build setup
- [ ] Structured JSON logging with MDC
- [ ] OpenTelemetry tracing auto-configuration
- [ ] Kafka trace context propagation
- [ ] Micrometer metrics with custom annotations
- [ ] Correlation ID propagation
- [ ] Sample app with Docker Compose (Jaeger + Prometheus + Grafana)
- [ ] CI/CD with GitHub Actions
- [ ] v0.1.0 release

## Tech Stack

- Java 17+
- Spring Boot 3.2
- OpenTelemetry SDK
- Micrometer
- SLF4J + Logback
- JUnit 5 + Testcontainers

## License

[Apache License 2.0](LICENSE)
