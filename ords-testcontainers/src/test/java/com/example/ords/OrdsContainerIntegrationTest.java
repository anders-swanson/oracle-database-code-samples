package com.example.ords;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.bson.Document;
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
    private static final String ORDS_INIT_SCRIPT = "/tmp/ords_init.sql";
    private static final String DB_API_ADMIN_USERNAME = "ordsuser";
    private static final String DB_API_ADMIN_PASSWORD = "ordsuserpwd";
    private static final String MONGO_USERNAME = "mongouser";
    private static final String MONGO_PASSWORD = "mongouserpwd";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final SSLContext INSECURE_TLS_CONTEXT = insecureTlsContext();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static final Network NETWORK = Network.newNetwork();

    private static final OracleContainer oracleContainer = new OracleContainer(DATABASE_IMAGE)
            .withStartupTimeout(Duration.ofMinutes(5))
            .withPassword(ADMIN_PASSWORD)
            .withNetwork(NETWORK)
            .withNetworkAliases(DATABASE_ALIAS);

    private static final OrdsContainer ordsContainer = new OrdsContainer()
            .withNetwork(NETWORK)
            .withDatabaseConnectionString(DATABASE_CONNECTION)
            .withOraclePassword(ADMIN_PASSWORD)
            .withSchema(DB_API_ADMIN_USERNAME, DB_API_ADMIN_PASSWORD, SCHEMA_CONNECTION)
            .withSchema(MONGO_USERNAME, MONGO_PASSWORD, SCHEMA_CONNECTION);

    @BeforeAll
    static void startContainers() throws IOException, InterruptedException {
        oracleContainer.start();
        initializeDatabase();
        ordsContainer.start();
    }

    @AfterAll
    static void stopContainers() {
        ordsContainer.stop();
        oracleContainer.stop();
        NETWORK.close();
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

    @Test
    void supportsMongoClientCrudOperations() {
        try (MongoClient client = MongoClients.create(mongoClientSettings())) {
            MongoDatabase database = client.getDatabase(MONGO_USERNAME);
            String collectionName = "compat_" + UUID.randomUUID().toString().replace("-", "");
            MongoCollection<Document> collection = database.getCollection(collectionName);
            String documentId = UUID.randomUUID().toString();

            Document originalDocument = new Document("_id", documentId)
                    .append("name", "Alice")
                    .append("credits", 12)
                    .append("active", true);

            collection.insertOne(originalDocument);

            Document insertedDocument = collection.find(Filters.eq("_id", documentId)).first();
            assertNotNull(insertedDocument, "Expected inserted document to be present");
            assertEquals("Alice", insertedDocument.getString("name"));
            assertEquals(12, insertedDocument.getInteger("credits"));
            assertTrue(insertedDocument.getBoolean("active"), "Expected boolean field to round trip");

            long updatedCount = collection.updateOne(
                    Filters.eq("_id", documentId),
                    Updates.set("credits", 15)
            ).getModifiedCount();
            assertEquals(1, updatedCount, "Expected one document to be updated");

            Document updatedDocument = collection.find(Filters.eq("_id", documentId)).first();
            assertNotNull(updatedDocument, "Expected updated document to be present");
            assertEquals(15, updatedDocument.getInteger("credits"));

            long deletedCount = collection.deleteOne(Filters.eq("_id", documentId)).getDeletedCount();
            assertEquals(1, deletedCount, "Expected one document to be deleted");
            assertNull(collection.find(Filters.eq("_id", documentId)).first(), "Expected document to be deleted");
        }
    }

    private static void initializeDatabase() throws IOException, InterruptedException {
        oracleContainer.copyFileToContainer(
                MountableFile.forClasspathResource("ords_init.sql"),
                ORDS_INIT_SCRIPT
        );
        execOracleCommandOrThrow(
                "Database initialization failed",
                "sqlplus",
                "sys / as sysdba",
                "@" + ORDS_INIT_SCRIPT
        );
    }

    private static void execOracleCommandOrThrow(String failureMessage, String... command)
            throws IOException, InterruptedException {
        Container.ExecResult result = oracleContainer.execInContainer(command);
        if (result.getExitCode() == 0) {
            return;
        }

        throw new IllegalStateException(
                failureMessage + ".\nstdout:\n" + result.getStdout() + "\nstderr:\n" + result.getStderr()
        );
    }

    private static String ordsDatabaseApiUrl(String relativePath) {
        return ordsContainer.getBaseUrl() + "/ords/" + DB_API_ADMIN_USERNAME + "/_/db-api/stable/" + relativePath;
    }

    private static MongoClientSettings mongoClientSettings() {
        ConnectionString connectionString = new ConnectionString(
                "mongodb://%s:%s@%s:%d/%s?authMechanism=PLAIN&authSource=%%24external&tls=true&retryWrites=false&loadBalanced=true"
                        .formatted(
                                MONGO_USERNAME,
                                MONGO_PASSWORD,
                                ordsContainer.getHost(),
                                ordsContainer.getMongoDbApiPort(),
                                MONGO_USERNAME
                        )
        );

        // Containerized ORDS uses a self-signed, untrusted certificate by default.
        // For simplicity in this test, we trust all certificates.
        // Note that you may supply your own trusted certificate to ORDS, which is recommended in most settings.
        return MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .applyToSslSettings(builder -> builder
                        .enabled(true)
                        .invalidHostNameAllowed(true)
                        .context(INSECURE_TLS_CONTEXT))
                .build();
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

    private static SSLContext insecureTlsContext() {
        try {
            TrustManager[] trustAllManagers = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllManagers, new SecureRandom());
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to initialize insecure TLS context for MongoDB client", e);
        }
    }
}
