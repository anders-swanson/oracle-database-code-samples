package com.example.reference;

import com.oracle.spring.testcontainers.OracleContainer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.Span;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@ActiveProfiles("test")
@DirtiesContext
@TestExecutionListeners(listeners = ReferenceApplicationTest.CollectorCleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReferenceApplicationTest {
    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer()
            .withUsername("testuser").withPassword("testpwd").withStartupTimeout(Duration.ofMinutes(5));

    private static final Queue<byte[]> logs = new ConcurrentLinkedQueue<>();
    private static final Queue<byte[]> traces = new ConcurrentLinkedQueue<>();
    private static final Queue<byte[]> metrics = new ConcurrentLinkedQueue<>();
    private static final HttpServer collector = startCollector();

    @DynamicPropertySource
    static void telemetryEndpoints(DynamicPropertyRegistry properties) {
        String base = "http://127.0.0.1:" + collector.getAddress().getPort();
        properties.add("management.opentelemetry.logging.export.otlp.endpoint", () -> base + "/v1/logs");
        properties.add("management.opentelemetry.tracing.export.otlp.endpoint", () -> base + "/v1/traces");
        properties.add("management.otlp.metrics.export.url", () -> base + "/v1/metrics");
    }

    @LocalServerPort
    int port;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    record Scenario(long seed, long bridge, long match, long other, long nearby, long second) { }

    @Test
    void developerCanCreateDiscoverAndAdjustBooksOverHttp() throws Exception {
        verifyStartupAndSecurity();
        Scenario scenario = createDiscoveryScenario();
        verifyApiErrors(scenario);
        verifyDiscovery(scenario);
        verifyStockChangesAndConcurrency(scenario);
        verifyTelemetry(scenario);
    }

    private void verifyStartupAndSecurity() throws Exception {
        assertThat(request("GET", "/actuator/health/readiness", null, false).body()).contains("UP");
        assertThat(request("GET", "/api/books", null, false).statusCode()).isEqualTo(401);
        assertThat(request("GET", "/actuator/metrics", null, false).statusCode()).isEqualTo(401);
        JsonNode catalog = get("/api/books?size=100");
        assertThat(catalog.get("totalElements").asInt()).isEqualTo(8);
        assertThat(catalog.get("items").toString()).contains("Demo Sora Fern", "Demo Mossgarden Press", "Demo Jules Lumen");
        assertThat(catalog.get("items").get(0).has("stock")).isFalse();
        JsonNode embedded = get("/api/books/semantic-search?q=fictional+catalog&limit=50");
        assertThat(java.util.stream.StreamSupport.stream(embedded.spliterator(), false)
                .map(book -> book.get("bookId").asLong()).toList())
                .containsExactlyInAnyOrderElementsOf(java.util.stream.StreamSupport.stream(catalog.get("items").spliterator(), false)
                        .map(book -> book.get("id").asLong()).toList());
        assertThat(get("/api/books?size=1").get("items").size()).isEqualTo(1);
    }

    private Scenario createDiscoveryScenario() throws Exception {
        long author = create("/api/authors", Map.of("name", "Test Casey Rivera"));
        long coauthor = create("/api/authors", Map.of("name", "Test Jordan Lee"));
        long gardener = create("/api/authors", Map.of("name", "Test Harper Green"));
        long publisher = create("/api/publishers", Map.of("name", "Test Developer Press", "countryCode", "GB"));
        long gardenPublisher = create("/api/publishers", Map.of("name", "Test Garden Press", "countryCode", "US"));
        List<Long> technicalAuthors = List.of(author, coauthor);
        String synopsis = "Build resilient Java order processing services with database transactions and connection pools.";

        long seed = book("0000000000101", "Java Orders", technicalAuthors, publisher, "An introduction to Java commerce applications.");
        long bridge = book("0000000000102", "Service Design", List.of(coauthor), publisher, "Designing maintainable service architectures.");
        long match = book("0000000000103", "Resilient Java Orders", technicalAuthors, publisher, synopsis);
        long other = book("0000000000104", "Urban Gardens", List.of(gardener), gardenPublisher, "Grow flowers and vegetables in a small city garden.");
        long unrelated = book("0000000000105", "Unrelated Orders", technicalAuthors, publisher, synopsis);
        long remote = book("0000000000106", "Remote Orders", technicalAuthors, publisher, synopsis);
        long soldOut = book("0000000000107", "Sold Out Orders", technicalAuthors, publisher, synopsis);
        long unindexed = book("0000000000108", "Unindexed Orders", technicalAuthors, publisher, null);

        relate(seed, bridge);
        relate(bridge, match);
        relate(match, seed);
        for (long candidate : List.of(other, remote, soldOut, unindexed)) {
            relate(seed, candidate);
        }
        relate(seed, bridge); // PUT is idempotent.

        long nearby = create("/api/stores", Map.of("name", "Test London Books", "longitude", -0.1276, "latitude", 51.5072));
        long second = create("/api/stores", Map.of("name", "Test West End Books", "longitude", -0.1320, "latitude", 51.5090));
        long far = create("/api/stores", Map.of("name", "Test Remote Books", "longitude", -74.0060, "latitude", 40.7128));
        for (long candidate : List.of(match, other, unrelated, unindexed)) {
            create(stockPath(nearby, candidate), Map.of("quantity", 4));
        }
        create(stockPath(second, match), Map.of("quantity", 8));
        create(stockPath(far, remote), Map.of("quantity", 3));
        create(stockPath(nearby, soldOut), Map.of("quantity", 0));

        JsonNode indexed = get("/api/books/semantic-search?q=" + encode(synopsis));
        assertThat(indexed.get(0).get("bookId").asLong()).isEqualTo(match);
        assertThat(indexed.get(0).get("similarity").asDouble()).isGreaterThan(0.99);
        // Updating a synopsis exercises the existing-row branch of vector indexing too.
        index(other, "Practical advice for growing vegetables and flowers in urban gardens.");
        return new Scenario(seed, bridge, match, other, nearby, second);
    }

    private void verifyApiErrors(Scenario scenario) throws Exception {
        for (String path : List.of("/api/books?size=101", "/api/books?page=-1", "/api/books/semantic-search?q=java&limit=51",
                "/api/stores?longitude=NaN&latitude=0", stockPath(scenario.nearby(), scenario.match()) + "/stock-adjustments?limit=101")) {
            assertThat(request("GET", path, null, true).statusCode()).as(path).isEqualTo(400);
        }
        assertThat(request("POST", "/api/books", "{bad-json", true).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/api/books", Map.of("isbn", "bad", "title", ""), true).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/api/stores", Map.of("name", "Invalid", "longitude", 181, "latitude", 0), true).statusCode()).isEqualTo(400);
        assertThat(request("PUT", "/api/books/" + scenario.match() + "/synopsis", Map.of("synopsis", " "), true).statusCode()).isEqualTo(400);
        assertThat(request("POST", stockPath(scenario.nearby(), scenario.match()), Map.of("quantity", -1), true).statusCode()).isEqualTo(400);

        for (String path : List.of("/api/books/999999", "/api/books/999999/document", "/api/stores/999999",
                "/api/books/999999/related-books", stockPath(scenario.nearby(), 999999),
                stockPath(scenario.nearby(), 999999) + "/stock-adjustments")) {
            assertThat(request("GET", path, null, true).statusCode()).as(path).isEqualTo(404);
        }
        for (String path : List.of(stockPath(999999, scenario.match()), stockPath(scenario.nearby(), 999999))) {
            assertThat(request("POST", path, Map.of("quantity", 1), true).statusCode()).as(path).isEqualTo(404);
        }
        for (String path : List.of("/api/books/999999/related-books/" + scenario.match(),
                "/api/books/" + scenario.seed() + "/related-books/999999")) {
            assertThat(request("PUT", path, null, true).statusCode()).as(path).isEqualTo(404);
        }
        assertThat(request("PUT", "/api/books/999999/synopsis", Map.of("synopsis", "Valid synopsis"), true).statusCode()).isEqualTo(404);
        int before = get("/api/books?size=100").get("totalElements").asInt();
        for (Map<String, Object> references : List.<Map<String, Object>>of(
                Map.of("authorIds", List.of(999999)), Map.of("publisherId", 999999))) {
            var body = new java.util.HashMap<>(references);
            body.put("isbn", "0000000000199");
            body.put("title", "Invalid Reference");
            assertThat(request("POST", "/api/books", body, true).statusCode()).isEqualTo(404);
        }
        assertThat(get("/api/books?size=100").get("totalElements").asInt()).isEqualTo(before);

        var duplicate = request("POST", "/api/books", Map.of("isbn", "0000000000103", "title", "Duplicate"), true);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).doesNotContain("ORA-", "INSERT", "BOOKS_ISBN_UQ");
        assertThat(request("POST", stockPath(scenario.nearby(), scenario.match()), Map.of("quantity", 4), true).statusCode()).isEqualTo(409);
    }

    private void verifyDiscovery(Scenario scenario) throws Exception {
        JsonNode related = get("/api/books/" + scenario.seed() + "/related-books");
        assertThat(java.util.stream.StreamSupport.stream(related.spliterator(), false).map(JsonNode::asLong).toList())
                .contains(scenario.bridge(), scenario.match()).doesNotContain(scenario.seed());
        JsonNode stores = get("/api/stores?longitude=-0.1276&latitude=51.5072&radiusKm=5");
        assertThat(stores.size()).isEqualTo(2);
        assertThat(stores.get(0).get("storeId").asLong()).isEqualTo(scenario.nearby());
        JsonNode matches = discover(scenario);
        assertThat(matches.size()).isEqualTo(2); // No relationship, remote, sold out and unindexed are all excluded.
        assertThat(matches.get(0).get("bookId").asLong()).isEqualTo(scenario.match());
        assertThat(matches.get(1).get("bookId").asLong()).isEqualTo(scenario.other());
        assertThat(matches.get(0).get("storeId").asLong()).isEqualTo(scenario.nearby());
        assertThat(matches.get(0).get("quantity").asInt()).isEqualTo(4);
        JsonNode document = get("/api/books/" + scenario.match() + "/document");
        assertThat(document.get("authors").toString()).contains("Test Casey Rivera", "Test Jordan Lee");
        assertThat(document.get("publisher").get("name").asText()).isEqualTo("Test Developer Press");
        assertThat(document.get("availability").size()).isEqualTo(2);
    }

    private void verifyStockChangesAndConcurrency(Scenario scenario) throws Exception {
        String path = stockPath(scenario.nearby(), scenario.match());
        JsonNode adjusted = json("POST", path + "/stock-adjustments", Map.of("delta", -4, "expectedVersion", 0), 200);
        assertThat(adjusted.get("quantity").asInt()).isZero();
        assertThat(adjusted.get("version").asInt()).isEqualTo(1);
        for (Map<String, Integer> change : List.of(Map.of("delta", 1, "expectedVersion", 0),
                Map.of("delta", -1, "expectedVersion", 1), Map.of("delta", 0, "expectedVersion", 1))) {
            assertThat(request("POST", path + "/stock-adjustments", change, true).statusCode()).isEqualTo(409);
        }
        assertThat(get(path).get("quantity").asInt()).isZero();
        JsonNode history = get(path + "/stock-adjustments");
        assertThat(history.size()).isEqualTo(1);
        assertThat(history.get(0).get("delta").asInt()).isEqualTo(-4);
        assertThat(history.get(0).get("resultingQuantity").asInt()).isZero();
        assertThat(OffsetDateTime.parse(history.get(0).get("recordedAt").asText())).isNotNull();
        JsonNode after = discover(scenario);
        assertThat(after.get(0).get("storeId").asLong()).isEqualTo(scenario.second());
        assertThat(after.get(0).get("quantity").asInt()).isEqualTo(8);
        JsonNode availability = get("/api/books/" + scenario.match() + "/document").get("availability");
        assertThat(java.util.stream.StreamSupport.stream(availability.spliterator(), false)
                .filter(row -> row.get("store").get("_id").asLong() == scenario.nearby())
                .findFirst().orElseThrow().get("quantity").asInt()).isZero();

        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var attempts = java.util.stream.IntStream.range(0, 2).mapToObj(ignored -> executor.submit(() -> {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return request("POST", path + "/stock-adjustments", Map.of("delta", 1, "expectedVersion", 1), true).statusCode();
            })).toList();
            assertThat(List.of(attempts.get(0).get(), attempts.get(1).get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(get(path).get("quantity").asInt()).isEqualTo(1);
        assertThat(get(path).get("version").asInt()).isEqualTo(2);
        history = get(path + "/stock-adjustments");
        assertThat(history.size()).isEqualTo(2); // Rejected writes produce no extra audit entries.
        assertThat(history.get(0).get("delta").asInt()).isEqualTo(1);
        assertThat(history.get(0).get("resultingQuantity").asInt()).isEqualTo(1);
        assertThat(get(path + "/stock-adjustments?limit=1").size()).isEqualTo(1);
    }

    private void verifyTelemetry(Scenario scenario) throws Exception {
        assertThat(get("/actuator/metrics").get("names").toString())
                .contains("http.server.requests", "ucp.connections.borrowed", "db.client.connection.count");
        assertThat(get("/actuator/metrics/ucp.connections.borrowed").get("measurements").get(0)
                .get("value").asDouble()).isGreaterThan(0);
        assertThat(get("/actuator/metrics/db.client.connection.count?tag=db.client.connection.state:idle")
                .get("measurements").get(0).get("value").asDouble()).isGreaterThan(0);
        String traceId = "12345678901234567890123456789012";
        var traced = HttpRequest.newBuilder(URI.create(base() + "/api/books/" + scenario.match()))
                .header("Authorization", credentials()).header("traceparent", "00-" + traceId + "-1234567890123456-01").GET().build();
        assertThat(http.send(traced, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<Span> spans = exportedSpans().stream()
                    .filter(span -> HexFormat.of().formatHex(span.getTraceId().toByteArray()).equals(traceId)).toList();
            assertThat(spans).anyMatch(span -> span.getKind() == Span.SpanKind.SPAN_KIND_SERVER);
            Span server = spans.stream().filter(span -> span.getKind() == Span.SpanKind.SPAN_KIND_SERVER)
                    .findFirst().orElseThrow();
            assertThat(spans).anySatisfy(span -> {
                assertThat(span.getKind()).isEqualTo(Span.SpanKind.SPAN_KIND_CLIENT);
                // Spring Security adds an internal span between HTTP and JDBC.
                assertThat(descendsFrom(span, server, spans)).isTrue();
                assertThat(span.getAttributesList()).anySatisfy(attribute -> {
                    assertThat(attribute.getKey()).isEqualTo("db.system.name");
                    assertThat(attribute.getValue().getStringValue()).isEqualTo("oracle.db");
                });
            });
        });
        // The duplicate-ISBN request emitted a real Hibernate warning; verify it crossed the OTLP HTTP boundary.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(logs).anySatisfy(body -> assertThat(new String(body, StandardCharsets.UTF_8))
                        .contains("ORA-00001", "0000000000103")));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(metrics).anySatisfy(body -> assertThat(new String(body, StandardCharsets.UTF_8))
                        .contains("ucp.connections.borrowed", "db.client.connection.count", "http.server.requests")));
    }

    private List<Span> exportedSpans() throws IOException {
        List<Span> spans = new ArrayList<>();
        for (byte[] body : traces) {
            for (var resource : ExportTraceServiceRequest.parseFrom(body).getResourceSpansList()) {
                for (var scope : resource.getScopeSpansList()) {
                    spans.addAll(scope.getSpansList());
                }
            }
        }
        return spans;
    }

    private boolean descendsFrom(Span span, Span ancestor, List<Span> spans) {
        for (int depth = 0; depth < spans.size(); depth++) {
            var parentId = span.getParentSpanId();
            if (parentId.equals(ancestor.getSpanId())) {
                return true;
            }
            span = spans.stream().filter(candidate -> candidate.getSpanId().equals(parentId))
                    .findFirst().orElse(null);
            if (span == null) {
                return false;
            }
        }
        return false;
    }

    private long book(String isbn, String title, List<Long> authors, long publisher, String synopsis) throws Exception {
        long id = create("/api/books", Map.of("isbn", isbn, "title", title, "authorIds", authors, "publisherId", publisher));
        if (synopsis != null) {
            index(id, synopsis);
        }
        return id;
    }

    private void index(long book, String synopsis) throws Exception {
        assertThat(request("PUT", "/api/books/" + book + "/synopsis", Map.of("synopsis", synopsis), true).statusCode()).isEqualTo(204);
    }

    private void relate(long source, long target) throws Exception {
        assertThat(request("PUT", "/api/books/" + source + "/related-books/" + target, null, true).statusCode()).isEqualTo(204);
    }

    private long create(String path, Object body) throws Exception {
        var response = request("POST", path, body, true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        assertThat(get(response.headers().firstValue("Location").orElseThrow()).get("id").asLong())
                .isEqualTo(mapper.readTree(response.body()).get("id").asLong());
        return mapper.readTree(response.body()).get("id").asLong();
    }

    private JsonNode discover(Scenario scenario) throws Exception {
        return get("/api/discovery?seedBookId=" + scenario.seed()
                + "&q=" + encode("How can I build dependable Java order services using pooled database connections?")
                + "&longitude=-0.1276&latitude=51.5072&radiusKm=5");
    }

    private JsonNode get(String path) throws Exception { return json("GET", path, null, 200); }

    private JsonNode json(String method, String path, Object body, int status) throws Exception {
        var response = request(method, path, body, true);
        assertThat(response.statusCode()).as(method + " " + path + ": " + response.body()).isEqualTo(status);
        return mapper.readTree(response.body());
    }

    private HttpResponse<String> request(String method, String path, Object body, boolean authenticated) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        if (authenticated) {
            request.header("Authorization", credentials());
        }
        String text = body instanceof String raw ? raw : mapper.writeValueAsString(body);
        return http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(text)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String base() { return "http://127.0.0.1:" + port; }
    private String stockPath(long store, long book) { return "/api/stores/" + store + "/books/" + book; }
    private String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    private String credentials() {
        return "Basic " + Base64.getEncoder().encodeToString("developer:test-api-password".getBytes(StandardCharsets.UTF_8));
    }

    private static HttpServer startCollector() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/logs", exchange -> receive(exchange, logs));
            server.createContext("/v1/traces", exchange -> receive(exchange, traces));
            server.createContext("/v1/metrics", exchange -> receive(exchange, metrics));
            server.start();
            return server;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void receive(HttpExchange exchange, Queue<byte[]> records) throws IOException {
        try (exchange) {
            records.add(exchange.getRequestBody().readAllBytes());
            exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
            exchange.sendResponseHeaders(200, -1);
        }
    }

    public static class CollectorCleanup extends AbstractTestExecutionListener {
        @Override
        public int getOrder() { return 0; }

        @Override
        public void afterTestClass(TestContext context) {
            // After-class listeners run in reverse order: Spring closes the app and flushes the SDK first.
            collector.stop(0);
        }
    }
}
