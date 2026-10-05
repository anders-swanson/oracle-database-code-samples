---
name: okafka-agent-memory
description: Build durable, owner-scoped agent memory from OKafka events with Oracle AI Database and Spring AI.
tags:
  - Java
  - OKafka
  - TxEventQ
  - Spring AI
  - Vector Search
  - Testcontainers
---

# OKafka event-to-memory lab

Send one fact through OKafka, watch it become a memory, then search it. OCI Generative AI extracts facts, judges candidates, and generates embeddings, using credentials from `~/.oci/config`. OKafka uses Oracle AI Database TxEventQ; there is no separate Kafka broker.

## Start → send → search

Use Java 21 or later, Maven, Docker, and OCI credentials with access to Generative AI in a region offering the configured chat and embedding models. Run these commands from this module directory. Start the database and leave the application running in a second terminal:

```sh
export OCI_COMPARTMENT_ID=YOUR_COMPARTMENT_OCID
docker compose up -d --wait
mvn spring-boot:run
```

In a third terminal, publish a fact. This producer runs as a separate process and exits after publishing; the application in the second terminal consumes the event and builds the memory. The command prints its source event ID:

```sh
mvn -q exec:java -Dexec.mainClass=com.example.okafkamemory.DemoProducer -Dexec.args='I prefer dark mode'
```

Search until the embedding consumer has added the vector:

```sh
curl -s http://localhost:8080/api/memories/search \
  -H 'Content-Type: application/json' \
  -d '{"query":"dark mode","limit":5}'
```

The response should include the dark-mode preference with vector, text, and recency scores. OCI may rephrase the extracted fact. If the first search returns `[]`, wait a moment and repeat it. Try another fact by changing `-Dexec.args`; the producer adds `remember ` to make the request explicit.

`memory.background-processing.enabled` defaults to `true` and starts all three OKafka stage consumers. The producer sets it to `false` in its own process so it only publishes events. This setting does not affect the separately running application or disable the search API.

## Follow the events

Each stage has an OKafka consumer and publishes the next event after completing its work:

| Topic | Event | Consumer action |
| --- | --- | --- |
| `MEMORY_INCOMING` | `IncomingEvent` with the transcript payload | Log a warning and drop events that fail prerequisites. Store accepted transcripts once per source ID and publish `TranscriptReady` only for new rows. |
| `MEMORY_TRANSCRIPTS` | `TranscriptReady(transcriptId)` | Use OCI to extract facts and judge each against the source transcript. Persist admitted memories with evidence, judge scores, and transcript provenance, then publish one `MemoryReadyForEmbedding` per memory. |
| `MEMORY_EMBEDDINGS` | `MemoryReadyForEmbedding(memoryId)` | Use OCI to embed the stored memory text, save or replace its vector. |

Internal events carry IDs; consumers load the corresponding rows. No worker scans SQL tables for ready work. Configure the incoming topic with `memory.intake.topic` and the other topics with `memory.events.*`. Each topic has one partition in this lab and a consumer group named `<topic>_PROCESSOR`.

Each stage creates a transactional producer on `consumer.getDBConnection()`. The same connection backs its `JdbcClient`. One `producer.commitTransaction()` atomically commits consumption of the input, database changes, and all next-stage events. `abortTransaction()` rolls them all back together. This is exactly-once processing of the database and queue effects at each stage. OCI calls can repeat after rollback; they are outside the database's atomic guarantee.

Each repository has a `from(Connection)` factory, for example `JdbcTranscriptRepository.from(connection)` or `JdbcMemoryRepository.from(connection)`. These factories use the caller's connection without committing, rolling back, or closing it. The `JdbcClient` constructors also remain available for pooled access, including the search API.

A failed stage event is rolled back and retried after a one-second pause. Other stages keep running, but a repeatedly failing event can block later events on its stage's partition. This example has no dead-letter or human-review workflow.

## Inspect the two durable tables

| Table | What to look for |
| --- | --- |
| `transcripts` | The accepted JSON payload, numeric ID, and `READY`, `DONE`, or `NO_MEMORY` preparation state. |
| `event_memories` | Admitted text, transcript provenance, evidence, judge score, and vector. A null vector is durable but excluded from search. |

Inspect them with SQL*Plus in the database container. Use `RAWTOHEX(source_event_id)` to inspect the UUID as 32 hexadecimal characters without hyphens:

```sh
docker compose exec oracle-free sqlplus 'TESTUSER/Welcome123#@//localhost:1521/FREEPDB1'
```

```sql
SELECT transcript_id, RAWTOHEX(source_event_id) AS source_event_id, preparation_status FROM transcripts;
SELECT RAWTOHEX(id), transcript_id, owner_scope, memory_text, judge_score, VECTOR_DIMENSION_COUNT(embedding) AS dimensions FROM event_memories;
EXIT;
```

The two tables retain provenance and processing state; the topics deliver the work. Preparation stores admitted memories and their embedding handoffs in the same transaction. Embedding readiness is represented only by the memory's vector: search excludes null vectors. The embedding stage updates the memory directly by its memory ID and can replace an existing vector. Extraction and judging use separate calls to the configured Spring AI `ChatModel`. The judge scores each candidate from 0 to 100 using the source transcript, supporting evidence, attribution, durability, usefulness, and sensitivity. It replaces exact evidence matching, keyword secret filtering, and prefix-based curation. This is a model judgment, not a guaranteed secret detector.

Configure admission in `application.yml`:

```yaml
memory:
  candidates:
    score-threshold: 70
```

Only scores **strictly greater** than the threshold are persisted; a score of 70 is rejected at the default setting, and 71 is admitted. The threshold must be between 0 and 100. Admitted memories retain their score in `event_memories.judge_score`. Low-scoring candidates are discarded; when none pass, the transcript becomes `NO_MEMORY`. Missing, malformed, fractional, or out-of-range judge scores fail preparation and leave its event eligible for retry. The embedding consumer uses the separate Spring AI `EmbeddingModel` to generate retrieval vectors.

The former candidate table and `MEMORY_CANDIDATES` topic are no longer used. Existing data requires merging evidence, judge scores, and transcript links into memory rows. Old embedding events carrying `candidateId` must be replaced with events carrying the corresponding `memoryId`, or use a disposable reset and republication.

## Verify and reset

`mvn verify` runs the sample with Oracle AI Database Free in Testcontainers, including a real OKafka producer-to-search path. It uses its own database and calls OCI for extraction, judging, and embeddings, so export `OCI_COMPARTMENT_ID` first. Deterministic persistence tests use test-only model fixtures. Integration tests also verify atomic input/output handoffs and event redelivery after a failed embedding write.

```sh
mvn verify
```

Run `mvn test` for deterministic database tests without OCI calls. These tests still require Docker.

Stop the app with Ctrl-C and the database with `docker compose down`. Compose retains its volume between runs. The schema uses `RAW(16)` source event IDs, a native `JSON` transcript column, and a required `event_memories.judge_score` column. It is incompatible with earlier schemas with a separate candidate table, without judge scores, or with CLOB transcripts, VARCHAR2 source IDs, per-memory embedding model columns, SQL-polled retry fields, or ten tables. To discard **disposable demo data** and create a clean schema, run:

```sh
docker compose down --volumes
docker compose up -d --wait
```

Preserving earlier data requires a separate migration and publication of missing stage events; existing READY rows from the SQL polling design will not be scanned automatically. The reset command deletes this Compose project's database volume.

## Use another local Oracle AI Database Free instance

The checked-in defaults connect to `localhost:1521/FREEPDB1` as `TESTUSER` using the sample password in `application.yml` and `ojdbc.properties`. Create that user in your local instance, apply `src/test/resources/okafka.sql` from a SYS session, then run `src/main/resources/db/schema.sql` as `TESTUSER`. Start the app from this directory so OKafka can read `ojdbc.properties`. Change the Spring datasource and OKafka connection properties together when using another host.

The event contract is `IncomingEvent(sourceEventId, ownerScope, transcriptPayload, storageAllowed)`. `transcriptPayload` is a JSON object mapped to `Map<String, Object>`, for example `{"message":"remember I prefer dark mode"}`. The OSON consumer deserializes the complete event, including its structured payload. Intake stores that payload as OSON in `transcripts.event_payload`, a native `JSON` column; the repository maps it back to a Java map without converting through JSON text. Candidate preparation sends the structured transcript and each extracted candidate to the LLM judge. JSON text is generated only when building the model prompt. Older events containing a JSON string in `transcriptPayload` must be republished with an object payload.

`sourceEventId` is a required Java `UUID`, serialized as a UUID string in JSON and stored as `RAW(16)` in `transcripts`. The OKafka key must equal its canonical hyphenated UUID string. Reusing an accepted source ID leaves its original transcript unchanged and emits no additional handoff. Events without storage permission, an owner scope, or a transcript payload are logged with their ID and rejection reason, then acknowledged without writing a row. Dropped IDs are not retained, so a later allowed event can use the same ID. The search request accepts only `query` and `limit`; owner scope is fixed by trusted local configuration as `user:demo`. Source transcripts are never returned by the API. The sample demonstrates atomic consume/write/publish transactions at each stage, plus local identity scoping. OCI extraction and embedding can be retried; exactly-once database and queue effects do not imply exactly-once model calls. Production authentication is outside this lab.

## OCI Generative AI configuration

`application.yml` selects OCI GenAI chat and embedding models with file authentication, the `DEFAULT` profile, and the region from `~/.oci/config`. Both models use `OCI_COMPARTMENT_ID`. The lab assumes one embedding model for all stored memories and search queries; it does not track model names or versions per row. Embedding dimensions come directly from the Spring AI configuration.

**If you change embedding models, re-embed all existing memory data before using search, even when the new model has the same dimensions.** Vectors from different models are not interchangeable. The lab does not automatically re-embed existing data; for disposable lab data, reset the database volume and republish your facts.

The [Oracle Spring AI chat guide](https://oracle.github.io/spring-cloud-oracle/site/docs/spring-ai/oci-genai-chat/), [embedding guide](https://oracle.github.io/spring-cloud-oracle/site/docs/spring-ai/oci-genai-embeddings/), and [OCI model catalog](https://docs.oracle.com/en-us/iaas/Content/generative-ai/pretrained-models.htm) describe provider settings and regional availability.

To check live OCI chat and embeddings without starting a database:

```sh
export OCI_COMPARTMENT_ID=YOUR_COMPARTMENT_OCID
mvn -Poci-smoke verify -Dtest=IncomingEventTest
```

Production identity, retention, deletion, and review workflows are outside this lab.
