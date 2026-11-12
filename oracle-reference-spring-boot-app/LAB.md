# Find a book nearby. Then sell the last copy.

> “I liked this Java book. Find me something similar that I can buy within five kilometers.”

Start with that request. Get a recommendation, sell the nearest store's remaining copies, and repeat the search. Will the book disappear, stay at the sold-out store, or move to another store?

This Spring Boot lab uses Oracle AI Database and simple `curl` and `jq` commands. The main walkthrough ends with the changed result and an explanation of how it works. Optional exercises let you explore ranking, conflicting updates, catalog creation, and telemetry.

All people, books, identifiers, stores, and coordinates are fictional test data.

## 1. Start the app and check the catalog

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

## 2. Find something you can buy nearby

You finished **Demo Java Foundations** (book `1`). Ask for related books about reliable Java services, available within five kilometers of the test location:

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

On a fresh catalog, expect two results: **Demo Reliable Java Services** and **Demo Gardening for Beginners**. Both select **Demo Willow Quay Books**. Reliable Java Services has four copies there.

Keep that result in mind: **Reliable Java Services → Willow Quay → four copies**. The response includes similarity scores, but you do not need to interpret those yet.

The gardening book looks unexpected. Leave it there for now; step 4 explains why it qualifies.

## 3. The closest store sells its last copies

Another customer buys the four remaining copies of Reliable Java Services at Willow Quay. Should that make the book unavailable everywhere?

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

Now repeat the same search. Before running it, predict which store will appear for Reliable Java Services:

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

Expect quantity `0`, version `1` at Willow Quay. Reliable Java Services remains discoverable, now at Cloudbank with eight copies. The book has moved to the next nearest store with stock. You changed inventory; you did not change its synopsis or recommendations.

Read the book as a nested JSON document:

```bash
curl -fsS -u 'developer:<API password>' \
  'http://127.0.0.1:8080/api/books/3/document' \
  | jq '{title, authors, publisher, availability}'
```

Expect Willow Quay with quantity `0`, version `1`, and Cloudbank with quantity `8`. The document includes both stores; discovery selects the nearest one with positive stock. It also includes the book's two authors and publisher.

Check the record of the sale:

```bash
curl -fsS -u 'developer:<API password>' \
  'http://127.0.0.1:8080/api/stores/1/books/3/stock-adjustments?limit=20' | jq
```

Expect one entry with `delta: -4` and `resultingQuantity: 0`.

JPA changes the versioned stock row while JDBC inserts the audit record in the same Spring transaction. Both writes commit or both roll back. Reading the document and discovery again requires no synchronization step.

## 4. Why did these books appear?

The search combines four decisions over shared data:

1. **Recommendations choose candidates.** A directed graph traversal finds books one or two recommendation edges from Java Foundations.
2. **Location and stock determine availability.** Stores must be within five kilometers and have positive inventory.
3. **Meaning determines ranking.** Vector distance compares the query with each eligible book's synopsis. The app generates embeddings locally, without an external embedding service.
4. **The nearest available store represents each book.** A JSON duality view supplies its nested catalog and inventory document.

Reliable Java Services is two recommendations away:

**Java Foundations → Software Design → Reliable Java Services**

See the candidate IDs:

```bash
curl -fsS -u 'developer:<API password>' \
  'http://127.0.0.1:8080/api/books/1/related-books' | jq
```

Expect `[2, 3, 4, 6, 7, 8]`. This API returns graph candidates without checking stock or location. Compare those IDs with the catalog from step 1.

**Why is gardening included?** It has a recommendation edge and available stock nearby. Similarity ranks eligible books, but the API has no minimum similarity cutoff. A weaker match can still appear. For an application that should hide weak matches, deciding and validating a relevance cutoff would be a separate design step.

Here is how every seeded book behaves on the initial, fresh catalog:

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

The combined discovery result is assembled by [one SQL statement](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/oracle-reference-spring-boot-app/src/main/java/com/example/reference/discovery/DiscoveryService.java) with one consistent snapshot. Graph and JSON duality describe the same relational rows used for inventory; the app needs no synchronization service between those representations. The endpoint also checks the seed exists before running that statement, so the whole HTTP request can contain other database calls.

Separate HTTP requests can see different committed states if another client changes stock between them. This sample demonstrates the behavior on a small catalog; it does not establish performance at production scale.

You have completed the main walkthrough: a search selected Willow Quay, a committed sale exhausted its stock, and the next search selected Cloudbank. Continue with any of the optional exercises below, or go to **Reset or stop**.

## Optional: separate relevance from availability

Try the two underlying searches independently. Predict whether each one will exclude a sold-out book before running it.

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

## Optional: reject a checkout with an old version

Run this immediately after the sale in step 3, before making further stock changes.

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

## Optional: publish a new recommendation through the APIs

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

The recommendation update also returns `204` with no response body. The graph now includes the new ID. Rerun discovery from step 2: the book is still absent because it has no available inventory. Add two copies at Willow Quay:

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

## Optional: inspect a request in Grafana

With the README's default observability configuration, rerun the discovery command from step 2 and open [Grafana](http://127.0.0.1:3001). In Explore, select Tempo, search for service `oracle-reference-spring-boot-app`, and open a recent `/api/discovery` trace. Allow a short delay for export.

Look for the HTTP server span and Oracle JDBC client spans beneath it. The endpoint also checks that the seed exists, so the entire HTTP request can contain database calls in addition to the combined discovery statement. Local query embedding happens before that statement borrows a database connection. Database-server spans depend on the connected Oracle AI Database's support.

Switch to Loki to inspect the service's application logs. You can also inspect a UCP connection counter through the API:

```bash
curl -fsS -u 'developer:<API password>' \
  'http://127.0.0.1:8080/actuator/metrics/ucp.connections.borrowed' | jq
```

This reports pool activity across the running app, rather than a per-request count. The [README's observability section](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/oracle-reference-spring-boot-app/README.md#observability) covers export settings and other pool metrics.

## Reset or stop

To repeat the lab, stop and restart the app, then rerun from step 1 and check the resource IDs again. Startup removes the lab's catalog additions, inventory changes, and audit history along with the rest of the sample schema data.

When finished, stop Spring Boot with Ctrl-C and follow the README’s shutdown instructions to stop Compose.

Compose retains its volumes. The next application startup resets the sample schema regardless of those retained volumes.
