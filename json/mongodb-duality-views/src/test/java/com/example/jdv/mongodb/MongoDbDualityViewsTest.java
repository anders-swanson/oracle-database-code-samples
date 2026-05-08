package com.example.jdv.mongodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.example.ords.OrdsContainer;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

@Testcontainers(disabledWithoutDocker = true)
class MongoDbDualityViewsTest {
    private static final String ADMIN_PASSWORD = "Welcome12345";
    private static final String PROJECT_USERNAME = "projectuser";
    private static final String PROJECT_PASSWORD = "projectpwd";
    private static final String PROJECTS_COLLECTION = "PROJECTS_DV";
    private static final SSLContext INSECURE_TLS_CONTEXT = insecureTlsContext();

    // Network for Oracle AI Database and ORDS to communicate
    private static final Network NETWORK = Network.newNetwork();

    private static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.26.1-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withPassword(ADMIN_PASSWORD)
            .withNetwork(NETWORK)
            .withNetworkAliases("ordsdb");

    private static final OrdsContainer ordsContainer = new OrdsContainer()
            .withNetwork(NETWORK)
            .withDatabaseConnectionString("jdbc:oracle:thin:@ordsdb:1521/freepdb1")
            .withOraclePassword(ADMIN_PASSWORD)
            .withSchema(PROJECT_USERNAME, PROJECT_PASSWORD, "ordsdb:1521/freepdb1");

    @BeforeAll
    static void startContainers() throws IOException, InterruptedException {
        oracleContainer.start();

        // Mount and run the schema init SQL script
        oracleContainer.copyFileToContainer(
                MountableFile.forClasspathResource("init.sql"),
                "/tmp/init.sql"
        );
        oracleContainer.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/init.sql");

        // Start the ORDS container, which exposes the MongoDB-compatible HTTP API
        ordsContainer.start();
    }

    @Test
    void usesMongoClientWithJsonRelationalDualityView() {
        try (MongoClient client = MongoClients.create(mongoClientSettings())) {
            MongoDatabase database = client.getDatabase(PROJECT_USERNAME);
            MongoCollection<Document> projects = database.getCollection(PROJECTS_COLLECTION);

            // Insert a document
            Document project = new Document("_id", 1001)
                    .append("name", "MongoDB API Duality View")
                    .append("status", "active")
                    .append("owner", "Demo Team")
                    .append("tasks", List.of(
                            new Document("_id", 2001)
                                    .append("title", "Create relational schema")
                                    .append("status", "done")
                                    .append("priority", 1),
                            new Document("_id", 2002)
                                    .append("title", "Exercise duality view collection")
                                    .append("status", "in_progress")
                                    .append("priority", 2)
                    ));

            projects.insertOne(project);

            Document insertedProject = projects.find(Filters.eq("_id", 1001)).first();
            assertNotNull(insertedProject, "Expected inserted project document to be present");
            assertEquals("MongoDB API Duality View", insertedProject.getString("name"));
            assertEquals("active", insertedProject.getString("status"));
            assertEquals("Demo Team", insertedProject.getString("owner"));

            // Get documents
            List<Document> tasks = insertedProject.getList("tasks", Document.class);
            assertNotNull(tasks, "Expected nested tasks to round trip");
            assertEquals(2, tasks.size());
            assertEquals("Create relational schema", tasks.getFirst().getString("title"));

            // Update document
            long updatedCount = projects.updateOne(
                    Filters.eq("_id", 1001),
                    Updates.set("status", "complete")
            ).getModifiedCount();
            assertEquals(1, updatedCount, "Expected one project document to be updated");

            Document updatedProject = projects.find(Filters.eq("_id", 1001)).first();
            assertNotNull(updatedProject, "Expected updated project document to be present");
            assertEquals("complete", updatedProject.getString("status"));

            // Delete document
            long deletedCount = projects.deleteOne(Filters.eq("_id", 1001)).getDeletedCount();
            assertEquals(1, deletedCount, "Expected one project document to be deleted");
            assertNull(projects.find(Filters.eq("_id", 1001)).first(), "Expected project document to be deleted");
        }
    }

    private static MongoClientSettings mongoClientSettings() {
        ConnectionString connectionString = new ConnectionString(
                "mongodb://%s:%s@%s:%d/%s?authMechanism=PLAIN&authSource=%%24external&tls=true&retryWrites=false&loadBalanced=true"
                        .formatted(
                                PROJECT_USERNAME,
                                PROJECT_PASSWORD,
                                ordsContainer.getHost(),
                                ordsContainer.getMongoDbApiPort(),
                                PROJECT_USERNAME
                        )
        );

        return MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .applyToSslSettings(builder -> builder
                        .enabled(true)
                        .invalidHostNameAllowed(true)
                        .context(INSECURE_TLS_CONTEXT))
                .build();
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
