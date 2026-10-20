package com.example.observability;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@DependsOn("telemetry")
public class ReadingsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReadingsController.class);
    private final ClientInfoDataSource dataSource;
    private final ObservationRegistry observations;
    private final Tracer tracer;

    public ReadingsController(DataSource pool, ObservationRegistry observations, Tracer tracer,
            @Value("${lab.server-traces:true}") boolean serverTraces) {
        // Keep the underlying UCP bean visible to Oracle's Micrometer binder.
        this.dataSource = new ClientInfoDataSource(pool, serverTraces);
        this.observations = observations;
        this.tracer = tracer;
    }

    @GetMapping("/api/readings")
    public ReadingsResponse readings(@RequestHeader(value = "X-Client-Id", defaultValue = "lab-client") String clientId)
            throws SQLException {
        if (!clientId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Client-Id must contain 1–64 ASCII letters, digits, dots, underscores, or hyphens");
        }
        Observation observation = Observation.start("lab.readings", observations);
        try (Observation.Scope ignored = observation.openScope(); var connection = dataSource.getConnection()) {
            // Bound database work to this request before returning the physical session to UCP.
            connection.beginRequest();
            try {
                connection.setClientInfo("OCSID.CLIENTID", clientId);
                List<Reading> readings = new ArrayList<>();
                SessionInfo session = null;
                try (var statement = connection.prepareStatement("""
                        SELECT id, sensor, value,
                               SYS_CONTEXT('USERENV', 'MODULE') AS module,
                               SYS_CONTEXT('USERENV', 'ACTION') AS action,
                               SYS_CONTEXT('USERENV', 'CLIENT_IDENTIFIER') AS client_id,
                               SYS_CONTEXT('USERENV', 'SID') AS session_id
                        FROM lab_readings ORDER BY id
                        """); var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        readings.add(new Reading(rows.getInt("id"), rows.getString("sensor"), rows.getDouble("value")));
                        session = new SessionInfo(rows.getString("module"), rows.getString("action"),
                                rows.getString("client_id"), rows.getString("session_id"));
                    }
                }
                String traceId = tracer.currentSpan().context().traceId();
                LOGGER.info("Read {} readings for client {}", readings.size(), clientId);
                return new ReadingsResponse(traceId, session, readings);
            } finally {
                connection.endRequest();
            }
        } catch (SQLException | RuntimeException failure) {
            observation.error(failure);
            throw failure;
        } finally {
            observation.stop();
        }
    }

    public record Reading(int id, String sensor, double value) { }
    public record SessionInfo(String module, String action, String clientId, String sessionId) { }
    public record ReadingsResponse(String traceId, SessionInfo session, List<Reading> readings) { }
}
