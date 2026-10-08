package com.example.metrics;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MetricsInstrumentationTest {
    @Container
    static final OracleContainer oracle = new OracleContainer()
            .withUsername("testuser")
            .withPassword("Welcome123#");

    @Test
    void exportsMetricsTracesAndCorrelatedLogsThroughOtlp() throws Exception {
        oracle.copyFileToContainer(MountableFile.forClasspathResource("okafka.sql"), "/tmp/okafka.sql");
        var grants = oracle.execInContainer("sqlplus", "-s", "sys / as sysdba", "@/tmp/okafka.sql");
        assertThat(grants.getExitCode()).isZero();
        assertThat(grants.getStdout()).doesNotContain("ORA-");

        var requests = new LinkedBlockingQueue<ExportMetricsServiceRequest>();
        var traceRequests = new LinkedBlockingQueue<ExportTraceServiceRequest>();
        var logRequests = new LinkedBlockingQueue<ExportLogsServiceRequest>();
        var receiver = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        receiver.createContext("/v1/metrics", exchange -> {
            try (exchange) {
                requests.add(ExportMetricsServiceRequest.parseFrom(exchange.getRequestBody()));
                // An empty protobuf message is a successful OTLP ExportMetricsServiceResponse.
                exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
                exchange.sendResponseHeaders(200, -1);
            }
        });
        receiver.createContext("/v1/traces", exchange -> {
            try (exchange) {
                traceRequests.add(ExportTraceServiceRequest.parseFrom(exchange.getRequestBody()));
                exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
                exchange.sendResponseHeaders(200, -1);
            }
        });
        receiver.createContext("/v1/logs", exchange -> {
            try (exchange) {
                logRequests.add(ExportLogsServiceRequest.parseFrom(exchange.getRequestBody()));
                exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
                exchange.sendResponseHeaders(200, -1);
            }
        });
        receiver.start();
        String endpoint = "http://localhost:" + receiver.getAddress().getPort() + "/v1/metrics";
        try (var context = new SpringApplicationBuilder(MetricsApplication.class)
                .run("--server.port=0",
                        "--okafka.bootstrap-servers=" + oracle.getHost() + ":" + oracle.getMappedPort(1521),
                        "--okafka.tns-admin=" + Path.of("src/test/resources").toAbsolutePath(),
                        "--management.otlp.metrics.export.url=" + endpoint,
                        "--management.otlp.metrics.export.step=1s",
                        "--management.opentelemetry.tracing.export.otlp.endpoint=" + endpoint.replace("/metrics", "/traces"),
                        "--management.opentelemetry.logging.export.otlp.endpoint=" + endpoint.replace("/metrics", "/logs"),
                        "--management.opentelemetry.tracing.export.schedule-delay=1s",
                        "--management.opentelemetry.logging.export.schedule-delay=1s")) {
            List<Metric> metrics = new ArrayList<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < deadline &&
                    !(metrics.stream().anyMatch(metric ->
                            metric.getName().equals("kafka.consumer.fetch.manager.records.consumed.total")
                                    && metric.getSum().getDataPointsList().stream().anyMatch(point ->
                                    point.getAsDouble() > MetricsSample.MESSAGE_COUNT))
                            && metrics.stream().anyMatch(metric -> metric.getName().equals("jvm.memory.used")))) {
                var request = requests.poll(1, TimeUnit.SECONDS);
                if (request == null) {
                    continue;
                }
                request.getResourceMetricsList().forEach(resourceMetrics -> {
                    assertThat(resourceMetrics.getResource().getAttributesList()).anySatisfy(attribute -> {
                        assertThat(attribute.getKey()).isEqualTo("service.name");
                        assertThat(attribute.getValue().getStringValue()).isEqualTo("okafka-metrics-sample");
                    });
                    resourceMetrics.getScopeMetricsList().forEach(scope -> metrics.addAll(scope.getMetricsList()));
                });
            }
            assertThat(metrics).extracting(Metric::getName).contains("jvm.memory.used");
            assertThat(metrics).anySatisfy(metric -> {
                assertThat(metric.getName()).isEqualTo("kafka.consumer.fetch.manager.records.consumed.total");
                assertThat(metric.getSum().getDataPointsList()).anySatisfy(point -> {
                    assertThat(point.getAsDouble()).isGreaterThan(MetricsSample.MESSAGE_COUNT);
                    assertThat(point.getAttributesList()).anySatisfy(attribute -> {
                        assertThat(attribute.getKey()).isEqualTo("client.id");
                        assertThat(attribute.getValue().getStringValue()).isEqualTo("metrics-consumer");
                    });
                });
            });
            assertThat(metrics).anySatisfy(metric -> {
                assertThat(metric.getName()).isEqualTo("kafka.producer.record.send.total");
                assertThat(metric.getSum().getDataPointsList()).anySatisfy(point ->
                        assertThat(point.getAsDouble()).isGreaterThan(MetricsSample.MESSAGE_COUNT));
            });
            assertThat(metrics).anySatisfy(metric -> {
                assertThat(metric.getName()).isEqualTo("kafka.producer.flush.time.ns.total");
                assertThat(metric.getSum().getDataPointsList()).anySatisfy(point ->
                        assertThat(point.getAsDouble()).isPositive());
            });

            List<Span> spans = new ArrayList<>();
            deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < deadline &&
                    !(spans.stream().anyMatch(span -> span.getName().equals("okafka.sample"))
                            && spans.stream().anyMatch(span -> span.getName().equals("okafka.reading")))) {
                var request = traceRequests.poll(1, TimeUnit.SECONDS);
                if (request != null) {
                    request.getResourceSpansList().forEach(resourceSpans -> {
                        assertThat(resourceSpans.getResource().getAttributesList()).anySatisfy(attribute -> {
                            assertThat(attribute.getKey()).isEqualTo("service.name");
                            assertThat(attribute.getValue().getStringValue()).isEqualTo("okafka-metrics-sample");
                        });
                        resourceSpans.getScopeSpansList().forEach(scope -> spans.addAll(scope.getSpansList()));
                    });
                }
            }
            var sampleSpan = spans.stream().filter(span -> span.getName().equals("okafka.sample")).findFirst().orElseThrow();
            assertThat(sampleSpan.getTraceId().size()).isEqualTo(16);
            assertThat(sampleSpan.getSpanId().size()).isEqualTo(8);
            assertThat(spans).anySatisfy(span -> {
                assertThat(span.getName()).isEqualTo("okafka.reading");
                assertThat(span.getTraceId().size()).isEqualTo(16);
            });

            List<LogRecord> logs = new ArrayList<>();
            deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < deadline && logs.stream().noneMatch(record ->
                    record.getBody().getStringValue().startsWith("Consumed 10 sample records"))) {
                var request = logRequests.poll(1, TimeUnit.SECONDS);
                if (request != null) {
                    request.getResourceLogsList().forEach(resourceLogs -> {
                        assertThat(resourceLogs.getResource().getAttributesList()).anySatisfy(attribute -> {
                            assertThat(attribute.getKey()).isEqualTo("service.name");
                            assertThat(attribute.getValue().getStringValue()).isEqualTo("okafka-metrics-sample");
                        });
                        resourceLogs.getScopeLogsList().forEach(scope -> logs.addAll(scope.getLogRecordsList()));
                    });
                }
            }
            assertThat(logs).anySatisfy(record -> {
                assertThat(record.getBody().getStringValue()).startsWith("Consumed 10 sample records");
                assertThat(record.getTraceId()).isEqualTo(sampleSpan.getTraceId());
                assertThat(record.getSpanId()).isEqualTo(sampleSpan.getSpanId());
            });

            String port = context.getEnvironment().getProperty("local.server.port");
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/actuator/metrics")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("jvm.memory.used", "kafka.consumer.fetch.manager.records.consumed.total",
                    "kafka.producer.record.send.total");
        } finally {
            receiver.stop(0);
        }
    }
}
