package com.example.ords;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

@Testcontainers(disabledWithoutDocker = true)
class OrdsContainerIntegrationTest {
    private static final String DATABASE_IMAGE = "gvenzl/oracle-free:23.26.1-slim-faststart";
    private static final String DATABASE_ALIAS = "ordsdb";
    private static final String ADMIN_PASSWORD = "Welcome12345";
    private static final String DATABASE_CONNECTION = "jdbc:oracle:thin:@ordsdb:1521/freepdb1";
    private static final String SCHEMA_CONNECTION = "ordsdb:1521/freepdb1";
    private static final String DB_API_ADMIN_USERNAME = "ordsuser";
    private static final String DB_API_ADMIN_PASSWORD = "ordsuserpwd";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static Network network;
    private static OracleContainer oracleContainer;
    private static OrdsContainer ordsContainer;

    @BeforeAll
    static void startContainers() throws Exception {
        network = Network.newNetwork();

        oracleContainer = new OracleContainer(DATABASE_IMAGE)
                .withStartupTimeout(Duration.ofMinutes(5))
                .withPassword(ADMIN_PASSWORD)
                .withNetwork(network)
                .withNetworkAliases(DATABASE_ALIAS);

        oracleContainer.start();

        // Initialize the user schema for ORDS
        oracleContainer.copyFileToContainer(
                MountableFile.forClasspathResource("ords_init.sql"),
                "/tmp/ords_init.sql"
        );

        Container.ExecResult result = oracleContainer.execInContainer(
                "sqlplus",
                "sys / as sysdba",
                "@/tmp/ords_init.sql"
        );

        if (result.getExitCode() != 0) {
            throw new IllegalStateException(
                    "Database initialization failed.\nstdout:\n" + result.getStdout() + "\nstderr:\n" + result.getStderr()
            );
        }

        ordsContainer = new OrdsContainer()
                .withNetwork(network)
                .withDatabaseConnectionString(DATABASE_CONNECTION)
                .withOraclePassword(ADMIN_PASSWORD)
                .withSchema(DB_API_ADMIN_USERNAME, DB_API_ADMIN_PASSWORD, SCHEMA_CONNECTION);

        ordsContainer.start();
    }

    @Test
    void startsOrdsAgainstOracleDatabase() throws Exception {
        HttpResponse<String> response = assertDoesNotThrow(() -> HTTP_CLIENT.send(
                HttpRequest.newBuilder(URI.create(ordsContainer.getBaseUrl()))
                        .GET()
                        .timeout(Duration.ofSeconds(30))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        ));

        assertTrue(response.statusCode() < 400, "Expected ORDS HTTP endpoint to respond successfully");
        assertTrue(ordsContainer.getMongoDbApiPort() > 0, "Expected mapped MongoDB API port");
    }

    @Test
    void getsDatabaseVersionFromOrdsApi() throws Exception {
        HttpResponse<String> response = assertDoesNotThrow(() -> HTTP_CLIENT.send(
                HttpRequest.newBuilder(URI.create(ordsDatabaseApiUrl("database/version")))
                        .header("Authorization", basicAuth(DB_API_ADMIN_USERNAME, DB_API_ADMIN_PASSWORD))
                        .GET()
                        .timeout(Duration.ofSeconds(30))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        ));

        assertEquals(200, response.statusCode(), "Expected ORDS Database API to return HTTP 200");
        assertJsonResponse(response.headers());
        DatabaseVersionResponse databaseVersion = assertDoesNotThrow(
                () -> OBJECT_MAPPER.readValue(response.body(), DatabaseVersionResponse.class),
                "Expected ORDS Database API to return valid JSON"
        );

        assertNotNull(databaseVersion.instanceName(), "Expected instance metadata in ORDS response");
        assertNotNull(databaseVersion.instanceVersion(), "Expected instance version metadata in ORDS response");
        assertFalse(databaseVersion.instanceVersion().isEmpty(), "Expected at least one instance version entry");

        String versionBanner = databaseVersion.instanceVersion().getFirst().banner();
        assertNotNull(versionBanner, "Expected version banner in ORDS response");
    }

    private String ordsDatabaseApiUrl(String relativePath) {
        return ordsContainer.getBaseUrl() + "/ords/" + DB_API_ADMIN_USERNAME + "/_/db-api/stable/" + relativePath;
    }

    private static String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertJsonResponse(HttpHeaders headers) {
        String contentType = headers.firstValue("Content-Type").orElse("");
        assertTrue(contentType.contains("application/json"), "Expected JSON response but was " + contentType);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DatabaseVersionResponse(
            @JsonProperty("instance_name") String instanceName,
            @JsonProperty("instance_version") List<InstanceVersion> instanceVersion
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InstanceVersion(String banner) {
    }
}
