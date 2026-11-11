# Lab: find your next book, then change what is available

A reader has finished **Demo Java Foundations** and wants a book about reliable Java services, available within five kilometers. Use the application's HTTP APIs to answer that request, sell the last copies at the closest store, and add a new recommendation to the catalog.

Along the way, you will see graph relationships choose candidates, vector search rank their synopses, spatial queries find stores, and inventory decide what can actually be bought. A JSON duality document shows the same catalog and stock as nested JSON in Oracle AI Database.

Allow about 30 minutes. All people, books, identifiers, stores, and coordinates in this lab are fictional test data.

## 1. Start with a fresh catalog

Follow **Start** in the [README](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/oracle-reference-spring-boot-app/README.md). You need Java 21+, Maven, Docker Compose, `curl`, and `jq`.

Run the app in one terminal and use a second terminal for the commands below. If you have already changed the sample data, restart the app first: startup resets this sample's schema and reloads its seed data. Only do this against your disposable lab schema.

Each command uses HTTP Basic authentication with the default username `developer`. Replace `<API password>` with the password used to start the app. If you configured another username, address, or port, replace those values too.

```bash
curl -fsS -u 'developer:<API password>' \
  'http://127.0.0.1:8080/api/books?page=0&size=100' \
  | jq '.items[] | {id, title}'
```

Expect eight seeded books. The examples below use the following IDs from a fresh catalog. Check your responses and substitute different IDs if needed:

| Resource | ID used below |
| --- | --- |
| Demo Java Foundations | `1` |
| Demo Reliable Java Services | `3` |
| Demo Willow Quay Books | `1` |
| Demo Cloudbank Books | `2` |

If a command fails, resolve the error before continuing. A `401` usually means the API credentials differ from the app's configuration.

## 2. Ask three smaller questions

**Which books does the catalog recommend?**

```bash
curl -fsS -u 'developer:<API password>' \
  'http://127.0.0.1:8080/api/books/1/related-books' | jq
```

Expect candidate IDs `[2, 3, 4, 6, 7, 8]`. Compare these with the catalog listing to find six candidates: Software Design, Reliable Java Services, Gardening for Beginners, Distributed Java Systems, Transactional Java Applications, and Connection Pooling in Practice (each with the `Demo` prefix).

Reliable Java Services is reached in two hops: Java Foundations → Software Design → Reliable Java Services. Recommendations are directed, and the API traverses one or two edges. Java Concurrency Handbook is outside that graph neighborhood even though its subject is relevant.

**Which books match the reader's description?**

```bash
curl -fsS -u 'developer:<API password>' --get \
  'http://127.0.0.1:8080/api/books/semantic-search' \
  --data-urlencode 'q=reliable Java services with database transactions and connection pooling' \
  --data-urlencode 'limit=50' | jq
```

This returns book IDs and cosine similarity scores, highest first. It searches all embedded books without considering recommendations, location, or stock. All eight seeded books have embeddings. Scores express similarity, not a probability or a pass/fail threshold.

**Which stores are nearby?**

```bash
curl -fsS -u 'developer:<API password>' --get \
  'http://127.0.0.1:8080/api/stores' \
  --data-urlencode 'longitude=-30.0000' \
  --data-urlencode 'latitude=35.0000' \
  --data-urlencode 'radiusKm=5' \
  | jq '.[] | {storeId, storeName, distanceKm}'
```

Willow Quay is at the search point; Cloudbank is roughly 1.4 kilometers away. Far Harbor is outside the five-kilometer radius. Coordinates use longitude first, latitude second.

## 3. Answer the reader's full request

```bash
curl -fsS -u 'developer:<API password>' --get \
  'http://127.0.0.1:8080/api/discovery' \
  --data-urlencode 'seedBookId=1' \
  --data-urlencode 'q=reliable Java services with database transactions and connection pooling' \
  --data-urlencode 'longitude=-30.0000' \
  --data-urlencode 'latitude=35.0000' \
  --data-urlencode 'radiusKm=5' \
  --data-urlencode 'limit=50' \
  | jq '.[] | {title, similarity, storeName, distanceKm, quantity}'
```

On a fresh catalog, expect exactly two books: Demo Reliable Java Services and Demo Gardening for Beginners. Both select Willow Quay as their closest in-stock store. Compare their scores and order with the standalone semantic search.

Why only these two?

| Seeded book | What happens in discovery |
| --- | --- |
| Demo Reliable Java Services | Related through two hops; positive stock nearby; eligible |
| Demo Gardening for Beginners | Directly related; positive stock nearby; eligible even with a weaker semantic match |
| Demo Software Design | Related, but has no inventory entry |
| Demo Java Concurrency Handbook | Embedded and in stock nearby, but outside the seed's recommendation neighborhood |
| Demo Distributed Java Systems | Related and in stock, but only at distant Far Harbor |
| Demo Transactional Java Applications | Related, but its inventory quantity is zero |
| Demo Connection Pooling in Practice | Related, but its inventory quantity is zero |
| Demo Java Foundations | The seed itself is excluded |

Discovery filters by graph reachability, an available embedding, store radius, and positive stock. It then ranks the eligible books by semantic distance and returns one nearest in-stock store per book. There is no minimum similarity cutoff, which explains the gardening result.

## 4. Open a result as a document

```bash
curl -fsS -u 'developer:<API password>' \
  'http://127.0.0.1:8080/api/books/3/document' \
  | jq '{title, authors, publisher, availability}'
```

The document contains two authors, a publisher, and availability at Willow Quay (four copies) and Cloudbank (eight copies). The discovery result's top-level store is just Willow Quay; its embedded document includes all inventory entries for that book.

The document is a read-only JSON relational duality view over the relational catalog and inventory. The discovery query joins that document with the graph, spatial, vector, and stock results in one SQL statement with one consistent snapshot. Separate HTTP requests can observe different committed states if another client changes inventory between requests.

## 5. Sell out the closest store

Read the current stock quantity and version:

```bash
curl -fsS -u 'developer:<API password>' \
  'http://127.0.0.1:8080/api/stores/1/books/3' | jq '{quantity, version}'
```

On a fresh catalog, expect quantity `4` and version `0`. Sell those four copies using that version. If your values differ, reset the lab before continuing.

```bash
curl -fsS -u 'developer:<API password>' \
  -H 'Content-Type: application/json' \
  -d '{"delta":-4,"expectedVersion":0}' \
  'http://127.0.0.1:8080/api/stores/1/books/3/stock-adjustments' \
  | jq '{quantity, version}'
```

Rerun the discovery command from step 3, then read the document and audit history:

```bash
curl -fsS -u 'developer:<API password>' \
  'http://127.0.0.1:8080/api/books/3/document' | jq '.availability'

curl -fsS -u 'developer:<API password>' \
  'http://127.0.0.1:8080/api/stores/1/books/3/stock-adjustments?limit=20' | jq
```

Expect quantity `0`, version `1` at Willow Quay. Reliable Java Services remains discoverable, now at Cloudbank with eight copies. Its JSON document still includes Willow Quay with zero stock. The audit history contains a `delta` of `-4` and `resultingQuantity` of `0`.

JPA changes the versioned stock row while JDBC inserts the audit record in the same Spring transaction. Both writes commit or both roll back. Reading the document and discovery again requires no synchronization step.

Now simulate a second checkout using the version read before the sale. This call deliberately omits `curl -f` so you can see the expected error body and status:

```bash
curl -sS -u 'developer:<API password>' \
  -H 'Content-Type: application/json' \
  -d '{"delta":-1,"expectedVersion":0}' \
  -w '\nHTTP %{http_code}\n' \
  'http://127.0.0.1:8080/api/stores/1/books/3/stock-adjustments'

curl -fsS -u 'developer:<API password>' \
  'http://127.0.0.1:8080/api/stores/1/books/3' | jq '{quantity, version}'

curl -fsS -u 'developer:<API password>' \
  'http://127.0.0.1:8080/api/stores/1/books/3/stock-adjustments?limit=20' | jq
```

Expect `409 Conflict`. Quantity and version remain `0` and `1`, and there is still only one audit entry. A client must reload current stock before attempting another adjustment.

## 6. Publish a new recommendation through the APIs

Create a fictional author, publisher, and book. Run this section once per fresh lab; repeating the book creation with the same identifier returns `409`.

```bash
curl -fsS -u 'developer:<API password>' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Lab Rowan Page","biography":"Fictional author of Java service guides."}' \
  'http://127.0.0.1:8080/api/authors' | jq

curl -fsS -u 'developer:<API password>' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Lab Service Press","countryCode":"US"}' \
  'http://127.0.0.1:8080/api/publishers' | jq
```

Note the returned IDs. The next command uses author `5` and publisher `4`, as expected after the fresh seed data. Replace those numbers if your responses differ.

```bash
curl -fsS -u 'developer:<API password>' \
  -H 'Content-Type: application/json' \
  -d '{"isbn":"0000000000091","title":"Lab Dependable Java Services","authorIds":[5],"publisherId":4}' \
  'http://127.0.0.1:8080/api/books' | jq
```

Note the new book's ID. The remaining commands use `9`; replace it if the returned ID differs.

Give the book a synopsis. The app embeds the text locally using the same model used for search queries; you do not need an external embedding service.

```bash
curl -fsS -u 'developer:<API password>' -X PUT \
  -H 'Content-Type: application/json' \
  -d '{"synopsis":"Build reliable Java services with database transactions, connection pooling, and safe inventory updates."}' \
  'http://127.0.0.1:8080/api/books/9/synopsis'
```

A successful synopsis update returns `204` with no response body. Find the new book in semantic search:

```bash
curl -fsS -u 'developer:<API password>' --get \
  'http://127.0.0.1:8080/api/books/semantic-search' \
  --data-urlencode 'q=reliable Java services with database transactions and connection pooling' \
  --data-urlencode 'limit=50' | jq '.[] | select(.bookId == 9)'
```

The new book can now appear in semantic search, but it still lacks a recommendation path and stock. Connect it to the seed:

```bash
curl -fsS -u 'developer:<API password>' -X PUT \
  'http://127.0.0.1:8080/api/books/1/related-books/9'

curl -fsS -u 'developer:<API password>' \
  'http://127.0.0.1:8080/api/books/1/related-books' | jq
```

The recommendation update also returns `204` with no response body. The graph now includes the new ID. Rerun discovery from step 3: the book is still absent because it has no available inventory. Add two copies at Willow Quay:

```bash
curl -fsS -u 'developer:<API password>' \
  -H 'Content-Type: application/json' \
  -d '{"quantity":2}' \
  'http://127.0.0.1:8080/api/stores/1/books/9' | jq
```

Rerun discovery and show the new book's full result:

```bash
curl -fsS -u 'developer:<API password>' --get \
  'http://127.0.0.1:8080/api/discovery' \
  --data-urlencode 'seedBookId=1' \
  --data-urlencode 'q=reliable Java services with database transactions and connection pooling' \
  --data-urlencode 'longitude=-30.0000' \
  --data-urlencode 'latitude=35.0000' \
  --data-urlencode 'radiusKm=5' \
  --data-urlencode 'limit=50' \
  | jq '.[] | select(.bookId == 9)'
```

The new book is now eligible. Its result includes the author and publisher you created and the stock you just added. These API calls populate the relational rows that the graph, vector search, spatial joins, and JSON duality view use together.

## 7. Inspect a request in Grafana

With the README's default observability configuration, rerun the discovery command from step 3 and open [Grafana](http://127.0.0.1:3001). In Explore, select Tempo, search for service `oracle-reference-spring-boot-app`, and open a recent `/api/discovery` trace. Allow a short delay for export.

Look for the HTTP server span and Oracle JDBC client spans beneath it. The endpoint also checks that the seed exists, so the entire HTTP request can contain database calls in addition to the combined discovery statement. Local query embedding happens before that statement borrows a database connection. Database-server spans depend on the connected Oracle AI Database's support.

Switch to Loki to inspect the service's application logs. You can also inspect a UCP connection counter through the API:

```bash
curl -fsS -u 'developer:<API password>' \
  'http://127.0.0.1:8080/actuator/metrics/ucp.connections.borrowed' | jq
```

This reports pool activity across the running app, rather than a per-request count. The [README's observability section](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/oracle-reference-spring-boot-app/README.md#observability) covers export settings and other pool metrics.

## 8. Reset or stop

To repeat the lab, stop and restart the app, then rerun from step 1 and check the resource IDs again. Startup removes the lab's catalog additions, inventory changes, and audit history along with the rest of the sample schema data.

When finished, stop Spring Boot with Ctrl-C and follow the README’s shutdown instructions to stop Compose.

Compose retains its volumes. The next application startup resets the sample schema regardless of those retained volumes.
