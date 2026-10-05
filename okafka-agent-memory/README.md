---
name: okafka-agent-memory
description: Build durable, owner-scoped agent memory from OKafka events with Oracle AI Database and OCI Generative AI.
tags:
  - Java
  - OKafka
  - TxEventQ
  - Spring AI
  - Vector Search
  - Testcontainers
---

# OKafka event-to-memory lab

Publish a transcript through OKafka, extract useful facts with OCI Generative AI, and search the resulting memories in Oracle AI Database. The lab uses three event consumers, two durable tables, and Spring AI chat and embedding models. OKafka delivers events through Oracle AI Database Transactional Event Queues (TxEventQ).

An accepted transcript does not automatically become a memory. The model may extract no useful facts, or the judge may reject every candidate. Those transcripts finish as `NO_MEMORY`; they do not emit embedding events.

## Run the lab

You need Java 21 or later, Maven, Docker, and OCI credentials in `~/.oci/config`. The configured OCI profile must have access to the compartment and the chat and embedding models in its region. The checked-in defaults select:

| Setting | Default |
| --- | --- |
| OCI authentication / profile | File authentication, `~/.oci/config`, `DEFAULT` |
| Chat model | `cohere.command-a-03-2025` |
| Embedding model / dimensions | `cohere.embed-v4.0`, 1536 |
| Admission threshold | 70; only scores **greater than 70** are admitted |
| Oracle AI Database connection | `localhost:1521/FREEPDB1`, `TESTUSER` / `Welcome123#` |
| Search API / owner scope | `http://localhost:8080`, `user:demo` |

Run the following commands from `okafka-agent-memory/`. In the application terminal, export your compartment ID, start the database, and leave the application running:

```sh
export OCI_COMPARTMENT_ID=YOUR_COMPARTMENT_OCID
docker compose up -d --wait --force-recreate
mvn spring-boot:run
```

Compose uses `container-registry.oracle.com/database/free:latest`, the official Oracle AI Database Free image. Its startup script creates `TESTUSER`, grants OKafka access, and installs the schema. `ORACLE_PWD` sets the administrator password (default `Welcome12345`); the lab user retains `Welcome123#`. Its health check verifies that the lab tables and required columns can be queried. The application creates its three topics and starts the consumers.

Database files live in the container with no persistent Docker volume or host data directory. The read-only mounts contain initialization scripts only. `--force-recreate` creates a clean database each time you run the startup command.

In another terminal, also export the compartment ID before starting the producer:

```sh
export OCI_COMPARTMENT_ID=YOUR_COMPARTMENT_OCID
mvn -q exec:java \
  -Dexec.mainClass=com.example.okafkamemory.DemoProducer \
  -Dexec.args='My Oracle AI Database application stores UUIDs as RAW(16).'
```

The producer prints a source event UUID and exits. It wraps the text in `{"message":"My Oracle AI Database application stores UUIDs as RAW(16)."}`, uses owner scope `user:demo`, grants storage permission, and sends an OSON `IncomingEvent`. Each invocation generates a new source UUID. The producer disables background processing in its own process; the separately running application processes the event.

Search after the embedding is stored:

```sh
curl -s http://localhost:8080/api/memories/search \
  -H 'Content-Type: application/json' \
  -d '{"query":"Oracle AI Database UUID RAW(16)","limit":5}'
```

The response contains `memoryId`, `memoryText`, `metadata`, and total, vector, text, and recency scores. The model can rephrase the fact. If the response is empty, inspect the transcript state before assuming that processing failed: an embedding may still be pending, or the transcript may have finished as `NO_MEMORY`.

The HTTP API exposes search only. Creation happens through incoming OKafka events.

## Explore memory creation and rejection

Keep the application running while publishing these examples. All of them enter through `MEMORY_INCOMING`; the application decides which candidates become memories. Text examples describe the intended model behavior, not fixed scores or guaranteed row counts.

### Create technical memories

Admission is scoped to useful, specific technical knowledge and project context about OKafka and Oracle AI Database. No command prefix is required. Publish facts such as:

```sh
mvn -q exec:java -Dexec.mainClass=com.example.okafkamemory.DemoProducer \
  -Dexec.args='OKafka delivers events through Oracle AI Database Transactional Event Queues (TxEventQ).'

mvn -q exec:java -Dexec.mainClass=com.example.okafkamemory.DemoProducer \
  -Dexec.args='My OKafka consumers use consumer.getDBConnection() for transactional writes.'

mvn -q exec:java -Dexec.mainClass=com.example.okafkamemory.DemoProducer \
  -Dexec.args='My Oracle AI Database application stores UUIDs as RAW(16).'

mvn -q exec:java -Dexec.mainClass=com.example.okafkamemory.DemoProducer \
  -Dexec.args='My Oracle AI Database vector search uses the same embedding model for stored vectors and queries.'

mvn -q exec:java -Dexec.mainClass=com.example.okafkamemory.DemoProducer \
  -Dexec.args='My application uses OKafka with TxEventQ. My Oracle AI Database application stores UUIDs as RAW(16).'
```

| Input | Intended memory | What to inspect |
| --- | --- | --- |
| OKafka and TxEventQ | Durable knowledge about queue technology | A `DONE` transcript with supported technical context |
| Consumer connection | Transaction implementation decision | Evidence and a memory about `consumer.getDBConnection()` |
| UUID storage | Oracle AI Database data representation | A memory about UUIDs and `RAW(16)` |
| Embedding model consistency | Vector search implementation | The relationship between stored vectors and query embeddings |
| Queue context plus UUID storage | Multiple technical facts | Separate candidates; each admitted memory receives its own UUID and embedding event |

Search with a request such as `{"query":"OKafka transaction connection","limit":5}`. Search ranks eligible memories and can return other technical facts as well.

There is no semantic deduplication or automatic replacement of previous facts. Publishing the same text twice with different source UUIDs can create two memories. Publishing a changed implementation decision does not deactivate the earlier memory.

### See transcripts finish without a memory

Unrelated preferences, generic development context, greetings, temporary requests, uncertain speculation, and vague aspirations are excluded:

```sh
mvn -q exec:java -Dexec.mainClass=com.example.okafkamemory.DemoProducer \
  -Dexec.args='My favorite color is teal and I prefer dark mode.'

mvn -q exec:java -Dexec.mainClass=com.example.okafkamemory.DemoProducer \
  -Dexec.args='I use Java for my backend projects.'

mvn -q exec:java -Dexec.mainClass=com.example.okafkamemory.DemoProducer \
  -Dexec.args='Please check my OKafka queue today.'

mvn -q exec:java -Dexec.mainClass=com.example.okafkamemory.DemoProducer \
  -Dexec.args='Maybe OKafka uses a separate Kafka broker, but I am not sure.'
```

| Reason | Example | Intended behavior |
| --- | --- | --- |
| Unrelated personal preference | Favorite colors, dark mode, concise explanations | Exclude details that do not supply domain knowledge |
| Unrelated development context | `I use Java for my backend projects` | Exclude generic context with no connection to OKafka or Oracle AI Database |
| Greeting | `hello` | Extract no durable fact |
| Temporary request | `Please check my OKafka queue today` | Exclude one-time tasks even when they mention the domain |
| Uncertain speculation | `Maybe OKafka uses a separate Kafka broker, but I am not sure` | Extract nothing useful or assign a low admission score |
| Vague aspiration | `I want resilient OKafka memory processing` | Exclude wishes without specific technical context |
| Assistant claim | Only an assistant states that the user stores UUIDs as `RAW(16)` | Do not use assistant content as user evidence |
| Sensitive secret | `My Oracle AI Database demo password is fake-demo-password` | Exclude secrets even when related to the domain |

For a mixed input such as `My Oracle AI Database application stores UUIDs as RAW(16). My favorite color is teal.`, retain only the UUID storage fact as memory. The original accepted transcript still contains both statements.

For these inputs, intake can still store a transcript. If extraction returns no candidates, or every candidate is skipped or rejected, the committed state is `NO_MEMORY` with no memory rows and no embedding handoff. A transcript with some admitted and some rejected candidates becomes `DONE` and stores only the admitted candidates.

The model's output and scores can vary between runs. A score of 70 is rejected at the default threshold, while 71 is admitted. Scores are not a fixed mapping from phrases. Rejected candidates and their scores are not stored, so `NO_MEMORY` alone does not tell you whether extraction returned nothing or the judge rejected all candidates.

**An accepted transcript retains its original payload, even when a secret or another candidate is excluded from memory.** Model instructions do not provide deterministic secret filtering. Use fabricated data in these examples; an application that must exclude sensitive source data needs to enforce that before publication or intake.

### Inspect each result

Connect to SQL*Plus in the running container:

```sh
docker compose exec oracle-free sqlplus 'TESTUSER/Welcome123#@//localhost:1521/FREEPDB1'
```

Paste the UUID printed by a producer into this query:

```sql
SET LINESIZE 220
SET LONG 10000
DEFINE source_event_id = 'PASTE_SOURCE_EVENT_UUID'

SELECT t.transcript_id, t.preparation_status,
       RAWTOHEX(m.id) AS memory_id, m.memory_text, m.judge_score,
       VECTOR_DIMENSION_COUNT(m.embedding) AS dimensions
FROM transcripts t
LEFT JOIN event_memories m ON m.transcript_id = t.transcript_id
WHERE t.source_event_id = HEXTORAW(REPLACE('&source_event_id', '-', ''))
ORDER BY m.id;
```

| Observation | Meaning |
| --- | --- |
| No transcript row, plus a drop warning | Intake rejected the event before storing it |
| `READY` | The transcript is awaiting preparation or its preparation transaction was rolled back |
| `NO_MEMORY`, null memory columns | Preparation completed without an admitted memory; no embedding event is expected |
| `DONE`, memory row, null dimensions | A memory was admitted, but its embedding has not committed yet |
| `DONE`, dimensions 1536 | The default model's embedding has committed; the memory can participate in search if its scope, status, and expiry also qualify |

A missing row by itself is not proof of a policy rejection: the event may still be waiting, or a consumer may have failed. Check the application logs for the drop warning, rollback, or stopped-stage error. The source UUID is stored as `RAW(16)`; `RAWTOHEX` displays 32 hexadecimal characters without hyphens.

Memories belonging to another owner, marked `inactive`, expired, missing an embedding, or having the wrong embedding dimensions are excluded from search. Their table rows remain stored. This is retrieval filtering, not candidate rejection or deletion.

## How the pipeline works

| Topic | Event | Work committed by its consumer |
| --- | --- | --- |
| `MEMORY_INCOMING` | `IncomingEvent(sourceEventId, ownerScope, transcriptPayload, storageAllowed)` | Apply intake prerequisites; store a new accepted transcript and publish `TranscriptReady(transcriptId)` |
| `MEMORY_TRANSCRIPTS` | `TranscriptReady(transcriptId)` | Extract candidates, judge each, store admitted memories, set `DONE` or `NO_MEMORY`, and publish one `MemoryReadyForEmbedding(memoryId)` per admitted memory |
| `MEMORY_EMBEDDINGS` | `MemoryReadyForEmbedding(memoryId)` | Embed the stored memory text and update its vector |

There is no separate candidate topic or candidate table. Candidates exist during preparation; only admitted memories are persisted. Internal events carry IDs, and consumers load the corresponding rows. Work is delivered by events, not by scanning SQL tables for ready rows.

`transcripts` stores the source UUID, owner scope, native JSON payload, and preparation state. `event_memories` stores its own UUID, transcript link, owner scope, evidence, memory text, judge score, embedding, status, and optional expiry. Transcript IDs are generated numeric values; source and memory IDs are Java UUIDs stored as `RAW(16)`.

Each stage uses `consumer.getDBConnection()` for both repository writes and a transactional OKafka producer. `commitTransaction()` commits consumption, relational writes, and emitted next-stage events together. `abortTransaction()` rolls them back together. The `from(Connection)` repository factories preserve ownership of that connection. Pooled UCP connections serve independent reads such as search.

The atomic guarantee covers the database and queue effects of a stage. OCI requests can repeat after rollback. A stage retries a handler failure after a one-second pause; a poll, setup, or rollback failure can stop the consumer and is logged as such. Each topic has one partition, and a repeatedly failing event can block later events on that stage. The lab has no dead-letter workflow.

Candidate objects with null or blank text/evidence are skipped. Candidates scoring at or below the threshold are discarded. Invalid model JSON or a missing, fractional, or out-of-range judge score fails the preparation transaction; the transcript remains eligible for retry rather than being committed as `NO_MEMORY`. A failed embedding update leaves the admitted row's vector null until its event succeeds. Replaying an embedding event can replace an existing vector.

Configuration lives in [application.yml](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/okafka-agent-memory/src/main/resources/application.yml). The incoming topic is `memory.intake.topic`; the remaining topics are `memory.events.transcripts` and `memory.events.embeddings`. Each consumer uses group `<topic>_PROCESSOR`, disables auto-commit, and processes at most one record per poll. `memory.background-processing.enabled=false` disables the stage consumers.

## Search behavior

The search request accepts `query` and `limit` (1–50). The owner is fixed by trusted configuration, `memory.retrieval.owner-scope`; callers do not select it in the request. The API returns admitted memory text and scores, not source transcripts or evidence.

Search embeds the query with the same configured model, filters eligible rows, and ranks them using cosine vector similarity, lexical matches, and recency. The default weights are 0.5, 0.35, and 0.15; recency has a 30-day half-life. There is no minimum similarity cutoff.

The lab assumes one embedding model for both stored vectors and queries and does not record model versions per row. After changing the embedding model, re-embed all memories before searching, even if the dimensions stay the same. There is no automatic re-embedding job; disposable lab data can be reset and republished.

## Run the tests

From the module directory, run the deterministic tests without OCI calls:

```sh
env -u OCI_COMPARTMENT_ID mvn test
```

Docker is still required for the database tests. Test-only chat and embedding fixtures exercise persistence, filtering, candidate thresholds, and retrieval. Without `OCI_COMPARTMENT_ID`, the live pipeline and OCI smoke test classes are skipped.

Run all tests, including the real OCI pipeline, with the compartment set:

```sh
export OCI_COMPARTMENT_ID=YOUR_COMPARTMENT_OCID
mvn test
```

The pipeline tests provision their own Oracle AI Database Free container. They verify producer-to-search processing, intake rollback and redelivery, atomic consume/write/publish handoffs, and embedding failure followed by redelivery through the full incoming-event path. They also publish the documented creation and rejection examples, verify intake drop reasons, reuse a rejected ID, and check duplicate source IDs. The examples assert domain facts are admitted, irrelevant inputs finish as `NO_MEMORY`, and mixed transcripts retain only the relevant facts. All use real OCI extraction, judging, and embeddings through the incoming event path. Inputs live in [memory-pipeline-examples.json](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/okafka-agent-memory/src/test/resources/memory-pipeline-examples.json). These tests do not use the Compose database.

To check only OCI chat, extraction, judging, and embeddings without provisioning a database:

```sh
export OCI_COMPARTMENT_ID=YOUR_COMPARTMENT_OCID
mvn -Dtest=OciGenAiSmokeTest test
```

From the repository root, the full module command is `mvn -pl okafka-agent-memory -am test`.

## Stop, reset, or use another instance

Stop the application with Ctrl-C and remove the database container with `docker compose down`. Removing the container discards all lab data.

To initialize a fresh lab:

```sh
docker compose down
docker compose up -d --wait --force-recreate
```

The prebuilt image runs the lab script through `/opt/oracle/scripts/startup`; the setup hook is skipped for its existing database. The script creates the user and schema when absent and reapplies grants on subsequent starts. `docker compose stop` followed by `start`, or `restart`, reuses the same container and retains its data; recreate the container for a clean setup.

To use another local Oracle AI Database Free instance, provision the sample user, apply [okafka.sql](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/okafka-agent-memory/src/test/resources/okafka.sql) from a SYS session in the target PDB, then apply [schema.sql](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/okafka-agent-memory/src/main/resources/db/schema.sql) as that user. Update the Spring datasource settings and the OKafka host, service, and `ojdbc.properties` credentials together. Start the app from the module directory so its default `oracle.net.tns_admin=.` resolves correctly.

Production authentication, retention, deletion, and human review are outside this lab. The owner scope is a local demonstration of isolation, not an authentication system.

## Code to explore

- [DemoProducer](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/okafka-agent-memory/src/main/java/com/example/okafkamemory/DemoProducer.java): publishes incoming OSON events.
- [IntakePolicy](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/okafka-agent-memory/src/main/java/com/example/okafkamemory/intake/IntakePolicy.java): deterministic intake prerequisites.
- [CandidatePreparationService](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/okafka-agent-memory/src/main/java/com/example/okafkamemory/candidate/CandidatePreparationService.java): extraction, admission, persistence, and embedding handoffs.
- [TransactionalEventConsumer](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/okafka-agent-memory/src/main/java/com/example/okafkamemory/events/TransactionalEventConsumer.java): transaction ownership and retries.
- [MemoryPipelineTest](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/okafka-agent-memory/src/test/java/com/example/okafkamemory/MemoryPipelineTest.java): full event path and failure recovery tests.
