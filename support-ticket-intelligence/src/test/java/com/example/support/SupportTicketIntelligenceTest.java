package com.example.support;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;

import com.example.support.model.TicketResponse;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SupportTicketIntelligenceTest {
    private static final String LOG_PREFIX = "[support-ticket-intelligence]";

    // Start explicitly so TxEventQ grants are in place before Spring creates OKafka beans.
    private static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.2-full-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withInitScripts("schema.sql", "data.sql")
            .withCreateContainerCmdModifier(command -> command.withHostName("localhost"))
            .withUsername("testuser")
            .withPassword("testpwd");

    static {
        try {
            logSection("Test Environment");
            logStep("Database", "Starting Oracle AI Database Free container");
            oracle.start();
            logStep("Database", "Ready at " + oracle.getJdbcUrl());
            logStep("TxEventQ", "Configuring test user");
            oracle.copyFileToContainer(MountableFile.forClasspathResource("init.sql"), "/tmp/init.sql");
            oracle.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/init.sql");
            logStep("Seed data", "Schema and sample rows loaded");
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", oracle::getJdbcUrl);
        registry.add("spring.datasource.username", oracle::getUsername);
        registry.add("spring.datasource.password", oracle::getPassword);
        registry.add("support.okafka.bootstrap-servers", () -> oracle.getHost() + ":" + oracle.getOraclePort());
        registry.add("support.okafka.tns-admin", SupportTicketIntelligenceTest::classpathDirectory);
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TicketSearchService ticketSearchService;

    @Test
    void createsTicketEnrichesEventAndQueriesLiveSupportData() throws Exception {
        logSection("Seed Incident Preparation");
        logStep("Vector chunks", "Preparing searchable chunks for seeded incidents");
        enrichSeedTickets();

        logSection("Ticket Creation");
        logStep("REST", "Opening a new support ticket");
        TicketResponse ticket = postTicket();
        logStep("Created", "ticketId=" + ticket.ticketId() + ", status=" + ticket.status());

        assertThat(ticket.status()).isEqualTo("OPEN");
        assertThat(count("select count(*) from support_tickets where ticket_id = ?", ticket.ticketId())).isEqualTo(1);
        assertThat(count("select count(*) from ticket_product_edges where ticket_id = ?", ticket.ticketId())).isEqualTo(1);

        logSection("Event Enrichment");
        logStep("Consumer", "Waiting for TxEventQ enrichment for ticket " + ticket.ticketId());
        waitForEnrichment(ticket.ticketId());
        assertThat(count("select count(*) from ticket_chunks where ticket_id = ?", ticket.ticketId())).isGreaterThanOrEqualTo(2);
        logStep("Vector chunks", "Ticket " + ticket.ticketId() + " is searchable");

        logSection("Hybrid Search");
        logStep("Search", "Relational filters + JSON text + vector similarity");
        JsonObject similar = getJson("/tickets/" + ticket.ticketId() + "/similar?customerTier=ENTERPRISE&slaStatus=OPEN");
        JsonArray incidents = similar.getJsonArray("incidents");
        assertThat(incidents).hasSizeGreaterThanOrEqualTo(1);
        assertThat(incidents.getJsonObject(0).getJsonNumber("ticketId").longValue()).isEqualTo(1001L);
        assertThat(incidents.getJsonObject(0).getJsonNumber("score").doubleValue()).isGreaterThan(0.60d);
        logStep("Result", incidents.size() + " similar incident candidate(s)");

        logSection("Graph Impact");
        logStep("Graph", "Querying affected customers and products");
        JsonObject impact = getJson("/tickets/" + ticket.ticketId() + "/impact");
        assertThat(impact.getJsonArray("paths")).hasSize(2);
        assertThat(impact.getJsonArray("paths").toString()).contains(
                "Acme Manufacturing",
                "Brightline Retail",
                "Checkout Router 9000"
        );
        logStep("Result", impact.getJsonArray("paths").size() + " impact path(s)");

        logSection("Document View");
        logStep("Document", "Reading the ticket from the JSON-relational duality view");
        JsonObject document = getJson("/tickets/" + ticket.ticketId() + "/document");
        assertThat(document.getString("subject")).isEqualTo("Checkout terminals cannot reach inventory router");
        assertThat(document.getJsonObject("diagnostics").getString("errorCode")).isEqualTo("ORA12541");
        assertThat(document.getJsonObject("diagnostics").getString("sku")).isEqualTo("CXROUTER9K");
        assertThat(document.getJsonObject("customer").getString("tier")).isEqualTo("ENTERPRISE");
        assertThat(document.getJsonObject("product").getJsonObject("specs").getString("family")).isEqualTo("checkout-networking");
        assertThat(document.getString("slaStatus")).isEqualTo("OPEN");
        logStep("Complete", "Support ticket intelligence flow verified");
    }

    private TicketResponse postTicket() throws Exception {
        String requestBody = """
                {
                  "customerId": 1,
                  "orderId": 500,
                  "productId": 100,
                  "subject": "Checkout terminals cannot reach inventory router",
                  "body": "Acme checkout terminals report ORA12541 when order service traffic crosses CXROUTER9K. This looks like prior ticket TCK1001.",
                  "errorCode": "ORA12541",
                  "severity": "HIGH",
                  "slaStatus": "OPEN"
                }
                """;
        HttpRequest request = HttpRequest.newBuilder(uri("/tickets"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        logHttp("POST", "/tickets");
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
        JsonObject ticket = parseJson(response.body());
        return new TicketResponse(ticket.getJsonNumber("ticketId").longValue(), ticket.getString("status"));
    }

    private JsonObject getJson(String path) throws Exception {
        logHttp("GET", path);
        HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
        return parseJson(response.body());
    }

    private JsonObject parseJson(String body) {
        try (StringReader reader = new StringReader(body)) {
            return Json.createReader(reader).readObject();
        }
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private Integer count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private void enrichSeedTickets() {
        List<Long> ticketIds = jdbcTemplate.queryForList("""
                select ticket_id
                from support_tickets t
                where not exists (
                    select 1
                    from ticket_chunks tc
                    where tc.ticket_id = t.ticket_id
                )
                order by ticket_id
                """, Long.class);

        logStep("Seed tickets", ticketIds.size() + " ticket(s) need chunks");
        for (Long ticketId : ticketIds) {
            logStep("Enrich", "ticketId=" + ticketId);
            jdbcTemplate.execute((Connection connection) -> {
                ticketSearchService.enrichTicket(connection, ticketId);
                return null;
            });
        }
    }

    private void waitForEnrichment(long ticketId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (System.nanoTime() < deadline) {
            int chunkCount = count("select count(*) from ticket_chunks where ticket_id = ?", ticketId);
            if (chunkCount >= 2) {
                logStep("Chunks", "ticketId=" + ticketId + ", count=" + chunkCount);
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Timed out waiting for ticket enrichment");
    }

    private static String classpathDirectory() {
        try {
            return new ClassPathResource("").getFile().getAbsolutePath();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to resolve test classpath directory", exception);
        }
    }

    private static void logSection(String title) {
        System.out.println();
        System.out.println(LOG_PREFIX + " ------------------------------------------------------------");
        System.out.println(LOG_PREFIX + " " + title);
        System.out.println(LOG_PREFIX + " ------------------------------------------------------------");
    }

    private static void logStep(String label, String detail) {
        System.out.printf("%s   %-14s %s%n", LOG_PREFIX, label + ":", detail);
    }

    private static void logHttp(String method, String path) {
        logStep("HTTP", method + " " + path);
    }
}
