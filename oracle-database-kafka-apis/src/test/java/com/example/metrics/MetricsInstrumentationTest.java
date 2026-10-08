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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
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
import oracle.jdbc.pool.OracleDataSource;
import org.apache.kafka.common.MetricName;
import org.junit.jupiter.api.Test;
import org.oracle.okafka.clients.producer.KafkaProducer;
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
                            "--management.opentelemetry.tracing.export.schedule-delay=1s",
                            "--management.opentelemetry.logging.export.schedule-delay=1s");
                } catch (RuntimeException exception) {
                    started.completeExceptionally(exception);
                    throw exception;
                }
            });
            try (var context = started.get(60, TimeUnit.SECONDS)) {
                // Observe committed business state through an independent database connection.
                var dataSource = new OracleDataSource();
                dataSource.setURL(oracle.getJdbcUrl());
                dataSource.setUser("testuser");
                dataSource.setPassword("Welcome123#");
                await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                    try (var connection = dataSource.getConnection();
                         var statement = connection.createStatement();
                         var result = statement.executeQuery("""
                                 select count(*), count(consumed_at),
                                        count(case when reading = 'sensor-reading-aborted' then 1 end)
                                 from OKAFKA_METRICS_READINGS
                                 """)) {
                        assertThat(result.next()).isTrue();
                        assertThat(result.getInt(1)).isPositive();
                        assertThat(result.getInt(2)).isPositive();
                        assertThat(result.getInt(3)).isZero();
                    }
                });

                List<Metric> metrics = new ArrayList<>();
                long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
                while (System.nanoTime() < deadline &&
                        !(hasConsumerFetchCount(metrics)
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
                        assertThat(point.getAsDouble()).isPositive();
                        assertThat(point.getAttributesList()).anySatisfy(attribute -> {
                            assertThat(attribute.getKey()).isEqualTo("client.id");
                            assertThat(attribute.getValue().getStringValue()).isEqualTo("metrics-consumer");
                        });
                    });
                });
                assertThat(metrics).anySatisfy(metric -> {
                    assertThat(metric.getName()).isEqualTo("kafka.producer.txn.commit.time.ns.total");
                    assertThat(metric.getSum().getDataPointsList()).anySatisfy(point ->
                            assertThat(point.getAsDouble()).isPositive());
                });

                assertThat(metrics).anySatisfy(metric -> {
                    assertThat(metric.getName()).isEqualTo("kafka.producer.txn.abort.time.ns.total");
                    assertThat(metric.getSum().getDataPointsList()).anySatisfy(point ->
                            assertThat(point.getAsDouble()).isPositive());
                });

                // These are the producer's own metrics, exposed through KafkaClientMetrics.
                var producer = (KafkaProducer<?, ?>) context.getBean("metricsProducer");
                assertThat(producer.metrics().keySet()).extracting(MetricName::name)
                        .contains("txn-begin-time-ns-total", "txn-commit-time-ns-total", "txn-abort-time-ns-total");
                assertThat(metrics).anySatisfy(metric -> {
                    assertThat(metric.getName()).isEqualTo("kafka.producer.txn.begin.time.ns.total");
                    assertThat(metric.getSum().getDataPointsList()).anySatisfy(point -> {
                        assertThat(point.getAsDouble()).isPositive();
                        assertThat(point.getAttributesList()).anySatisfy(attribute -> {
                            assertThat(attribute.getKey()).isEqualTo("client.id");
                            assertThat(attribute.getValue().getStringValue()).isEqualTo("metrics-producer");
                        });
                    });
                });

                List<Span> spans = new ArrayList<>();
                deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
                while (System.nanoTime() < deadline &&
                        !(spans.stream().anyMatch(span -> span.getName().equals("okafka.sample"))
                                && spans.stream().anyMatch(span -> span.getName().equals("okafka.reading"))
                                && spans.stream().anyMatch(span -> hasOutcome(span, "okafka.producer.transaction", "aborted"))
                                && spans.stream().anyMatch(span -> hasOutcome(span, "okafka.consumer.transaction", "rolled_back"))
                                && spans.stream().anyMatch(span -> hasOutcome(span, "okafka.producer.transaction", "committed"))
                                && spans.stream().anyMatch(span -> hasOutcome(span, "okafka.consumer.transaction", "committed")))) {
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
                assertThat(spans).anyMatch(span -> hasOutcome(span, "okafka.producer.transaction", "aborted"));
                assertThat(spans).anyMatch(span -> hasOutcome(span, "okafka.consumer.transaction", "rolled_back"));
                for (var transactionName : List.of("okafka.producer.transaction", "okafka.consumer.transaction")) {
                    var transaction = spans.stream().filter(span -> hasOutcome(span, transactionName, "committed"))
                            .findFirst().orElseThrow();
                    assertThat(transaction.getTraceId().size()).isEqualTo(16);
                    assertThat(transaction.getSpanId().size()).isEqualTo(8);
                    String observation = transactionName.equals("okafka.producer.transaction")
                            ? "okafka.reading" : "okafka.sample";
                    assertThat(spans).anySatisfy(span -> {
                        assertThat(span.getName()).isEqualTo(observation);
                        assertThat(span.getTraceId()).isEqualTo(transaction.getTraceId());
                        assertThat(span.getSpanId()).isEqualTo(transaction.getParentSpanId());
                    });
                }

                List<LogRecord> logs = new ArrayList<>();
                deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
                while (System.nanoTime() < deadline && logs.stream().noneMatch(record ->
                        record.getBody().getStringValue().startsWith("Committed "))) {
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
                    assertThat(record.getBody().getStringValue()).startsWith("Committed ");
                    assertThat(spans).anySatisfy(span -> {
                        assertThat(hasOutcome(span, "okafka.consumer.transaction", "committed")).isTrue();
                        assertThat(record.getTraceId()).isEqualTo(span.getTraceId());
                        assertThat(record.getSpanId()).isEqualTo(span.getSpanId());
                    });
                });

                String port = context.getEnvironment().getProperty("local.server.port");
                var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/actuator/metrics")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).contains("jvm.memory.used", "kafka.consumer.fetch.manager.records.consumed.total",
                        "kafka.producer.txn.begin.time.ns.total", "kafka.producer.txn.commit.time.ns.total");
            }
            polling.get(10, TimeUnit.SECONDS);
        } finally {
            receiver.stop(0);
        }
    }

    private static boolean hasConsumerFetchCount(List<Metric> metrics) {
        return metrics.stream().anyMatch(metric -> metric.getName().equals("kafka.consumer.fetch.manager.records.consumed.total")
                && metric.getSum().getDataPointsList().stream().anyMatch(point ->
                point.getAsDouble() > 0));
    }

    private static boolean hasOutcome(Span span, String name, String outcome) {
        return span.getName().equals(name) && span.getAttributesList().stream().anyMatch(attribute ->
                attribute.getKey().equals("outcome") && attribute.getValue().getStringValue().equals(outcome));
    }
}
