package com.example.metrics;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.oracle.spring.testcontainers.OracleContainer;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.metrics.v1.Metric;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class MetricsInstrumentationTest {
    @Container
    static final OracleContainer oracle = new OracleContainer()
            .withUsername("testuser")
            .withPassword("Welcome123#");

    @Test
    void exportsMetricsTracesAndLogsThroughOtlp() throws Exception {
        oracle.copyFileToContainer(MountableFile.forClasspathResource("okafka.sql"), "/tmp/okafka.sql");
        var grants = oracle.execInContainer("sqlplus", "-s", "sys / as sysdba", "@/tmp/okafka.sql");
        assertThat(grants.getExitCode()).isZero();
        assertThat(grants.getStdout()).doesNotContain("ORA-");

        var metrics = new ConcurrentLinkedQueue<Metric>();
        var spans = new ConcurrentLinkedQueue<Span>();
        var logs = new ConcurrentLinkedQueue<LogRecord>();
        var receiver = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        receiver.createContext("/v1/metrics", exchange -> {
            try (exchange) {
                ExportMetricsServiceRequest.parseFrom(exchange.getRequestBody()).getResourceMetricsList()
                        .forEach(resource -> resource.getScopeMetricsList()
                                .forEach(scope -> metrics.addAll(scope.getMetricsList())));
                // An empty protobuf response acknowledges the OTLP export.
                exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
                exchange.sendResponseHeaders(200, -1);
            }
        });
        receiver.createContext("/v1/traces", exchange -> {
            try (exchange) {
                ExportTraceServiceRequest.parseFrom(exchange.getRequestBody()).getResourceSpansList()
                        .forEach(resource -> resource.getScopeSpansList()
                                .forEach(scope -> spans.addAll(scope.getSpansList())));
                exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
                exchange.sendResponseHeaders(200, -1);
            }
        });
        receiver.createContext("/v1/logs", exchange -> {
            try (exchange) {
                ExportLogsServiceRequest.parseFrom(exchange.getRequestBody()).getResourceLogsList()
                        .forEach(resource -> resource.getScopeLogsList()
                                .forEach(scope -> logs.addAll(scope.getLogRecordsList())));
                exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
                exchange.sendResponseHeaders(200, -1);
            }
        });
        receiver.start();
        String endpoint = "http://localhost:" + receiver.getAddress().getPort() + "/v1/metrics";
        // ApplicationRunner keeps the application thread polling until the context closes.
        var started = new CompletableFuture<ConfigurableApplicationContext>();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var application = new SpringApplicationBuilder(MetricsApplication.class)
                    .listeners((ApplicationListener<ApplicationStartedEvent>) event ->
                            started.complete(event.getApplicationContext()))
                    .build();
            var polling = executor.submit(() -> {
                try {
                    application.run("--server.port=0",
                            "--okafka.bootstrap-servers=" + oracle.getHost() + ":" + oracle.getMappedPort(1521),
                            "--okafka.tns-admin=" + Path.of("src/test/resources").toAbsolutePath(),
                            "--management.otlp.metrics.export.url=" + endpoint,
                            "--management.otlp.metrics.export.step=1s",
                            "--management.opentelemetry.tracing.export.otlp.endpoint=" + endpoint.replace("/metrics", "/traces"),
                            "--management.opentelemetry.logging.export.otlp.endpoint=" + endpoint.replace("/metrics", "/logs"),
                            "--management.tracing.sampling.probability=1",
                            "--management.opentelemetry.tracing.export.schedule-delay=1s",
                            "--management.opentelemetry.logging.export.schedule-delay=1s");
                } catch (RuntimeException exception) {
                    started.completeExceptionally(exception);
                    throw exception;
                }
            });
            try (var context = started.get(60, TimeUnit.SECONDS)) {
                await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                    assertThat(metrics).anyMatch(metric -> metric.getName().startsWith("kafka.producer."));
                    assertThat(metrics).anyMatch(metric -> metric.getName().startsWith("kafka.consumer."));
                    // A consumer span must continue the producer trace and use its span as parent.
                    assertThat(spans).filteredOn(span -> span.getName().equals("okafka.process"))
                            .anySatisfy(consumerSpan -> {
                                assertThat(consumerSpan.getKind()).isEqualTo(Span.SpanKind.SPAN_KIND_CONSUMER);
                                assertThat(spans).anySatisfy(producerSpan -> {
                                    assertThat(producerSpan.getName()).isEqualTo("okafka.produce");
                                    assertThat(producerSpan.getKind()).isEqualTo(Span.SpanKind.SPAN_KIND_PRODUCER);
                                    assertThat(producerSpan.getTraceId()).isEqualTo(consumerSpan.getTraceId());
                                    assertThat(producerSpan.getSpanId()).isEqualTo(consumerSpan.getParentSpanId());
                                    assertThat(logs).anySatisfy(record -> {
                                        assertThat(record.getBody().getStringValue()).startsWith("Published ");
                                        assertThat(record.getTraceId()).isEqualTo(producerSpan.getTraceId());
                                        assertThat(record.getSpanId()).isEqualTo(producerSpan.getSpanId());
                                    });
                                });
                                assertThat(logs).anySatisfy(record -> {
                                    assertThat(record.getBody().getStringValue()).startsWith("Processed ");
                                    assertThat(record.getTraceId()).isEqualTo(consumerSpan.getTraceId());
                                    assertThat(record.getSpanId()).isEqualTo(consumerSpan.getSpanId());
                                });
                            });
                });
            }
            polling.get(10, TimeUnit.SECONDS);
        } finally {
            receiver.stop(0);
        }
    }

}
