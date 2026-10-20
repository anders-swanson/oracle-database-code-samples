package com.example.observability;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import io.micrometer.core.instrument.MeterRegistry;
import oracle.ucp.jdbc.PoolDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "lab.server-traces=false",
        "spring.datasource.oracleucp.max-pool-size=1",
        "management.otlp.metrics.export.enabled=false",
        "management.tracing.export.otlp.enabled=false",
        "management.logging.export.otlp.enabled=false"
})
class ObservabilityTest {
    @Container
    static final GenericContainer<?> oracle = new GenericContainer<>(
            "container-registry.oracle.com/database/free:latest")
            .withEnv("ORACLE_PWD", "Welcome123#")
            .withExposedPorts(1521)
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("oracle/init.sql")),
                    "/opt/oracle/scripts/startup/01-schema.sql")
            .waitingFor(Wait.forLogMessage(".*DONE: Executing user defined scripts.*", 1))
            .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:oracle:thin:@//" + oracle.getHost()
                + ":" + oracle.getMappedPort(1521) + "/FREEPDB1");
    }

    @LocalServerPort
    private int port;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private MeterRegistry meters;

    @Test
    void queriesDatabaseWithCorrelatedIdentityAndUcpMetrics() throws Exception {
        RestClient client = RestClient.create("http://localhost:" + port);
        var first = client.get().uri("/api/readings").header("X-Client-Id", "client-a")
                .retrieve().body(ReadingsController.ReadingsResponse.class);
        var second = client.get().uri("/api/readings").header("X-Client-Id", "client-b")
                .retrieve().body(ReadingsController.ReadingsResponse.class);
        assertThat(first.readings()).containsExactly(
                new ReadingsController.Reading(1, "temperature", 23.5),
                new ReadingsController.Reading(2, "humidity", 42));
        assertThat(first.traceId()).matches("[a-f0-9]{32}").doesNotMatch("0{32}");
        assertThat(second.traceId()).isNotEqualTo(first.traceId());
        assertThat(first.session().module()).isEqualTo("oracledb-e2e-observability");
        assertThat(first.session().action()).isEqualTo("readings.list");
        assertThat(first.session().clientId()).isEqualTo("client-a");
        assertThat(second.session().clientId()).isEqualTo("client-b");
        assertThat(second.session().sessionId()).isEqualTo(first.session().sessionId());
        PoolDataSource pool = dataSource.unwrap(PoolDataSource.class);
        assertThat(pool.getMaxPoolSize()).isEqualTo(1);
        assertThat(pool.getConnectionProperties())
                .containsEntry("oracle.jdbc.provider.traceEventListener", "observability-trace-event-listener-provider")
                .containsEntry("oracle.jdbc.provider.traceEventListener.unique_identifier", "oracledb-e2e-observability")
                .containsEntry("oracle.jdbc.serverTelemetryTracesEnabled", "false");
        assertThat(meters.get("ucp.connections.borrowed").functionCounter().count()).isGreaterThanOrEqualTo(2);
        assertThat(meters.get("db.client.connection.count").tag("db.client.connection.state", "idle")
                .gauge().value()).isEqualTo(1);

        // p95 requires finite HTTP buckets; UCP function timers expose counts and totals only.
        var http = meters.get("http.server.requests").tag("uri", "/api/readings").timer();
        assertThat(http.count()).isGreaterThanOrEqualTo(2);
        assertThat(http.takeSnapshot().histogramCounts())
                .anySatisfy(bucket -> {
                    assertThat(bucket.bucket(TimeUnit.MILLISECONDS)).isEqualTo(25);
                });

        // Returning a physical session to UCP must not leak its last client into the next decorated borrow.
        try (var connection = new ClientInfoDataSource(dataSource, false).getConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT SYS_CONTEXT('USERENV', 'CLIENT_IDENTIFIER') FROM dual")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("anonymous");
        }
        int status = client.get().uri("/api/readings").header("X-Client-Id", "invalid client")
                .exchange((request, response) -> response.getStatusCode().value());
        assertThat(status).isEqualTo(400);
    }
}
