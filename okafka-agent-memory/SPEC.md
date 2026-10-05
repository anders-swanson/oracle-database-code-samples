# OKafka event-to-memory lab

## Goal

Teach an event-driven path from an OKafka transcript to searchable, owner-scoped memory. Oracle AI Database TxEventQ carries each stage event; OCI Generative AI extracts facts, judges candidates, and generates embeddings. There is no SQL polling worker or separate Kafka broker.

## Learner path

1. Configure `~/.oci/config` and export `OCI_COMPARTMENT_ID`.
2. `docker compose up -d --wait` creates Oracle AI Database Free and the application schema.
3. `mvn spring-boot:run` starts four stage consumers and the search API.
4. Run the separate `DemoProducer` process to publish a fact and print its UUID source event ID.
5. Inspect the three tables, then search after the embedding stage completes.

`memory.background-processing.enabled` defaults to `true`. The producer sets it to `false` only in its own process. The search API remains available when consumers are disabled.

## Event pipeline

| Topic | Event | Durable changes and output |
| --- | --- | --- |
| `MEMORY_INCOMING` | `IncomingEvent` | Log and drop events that fail storage permission, owner scope, or payload prerequisites without retaining a receipt. Insert accepted transcripts once per source ID and publish `TranscriptReady` only for new rows. |
| `MEMORY_TRANSCRIPTS` | `TranscriptReady(transcriptId)` | Extract facts with OCI, judge each against the source transcript, persist only candidates scoring above the configured threshold, mark preparation complete, and publish `CandidateReady` events. |
| `MEMORY_CANDIDATES` | `CandidateReady(candidateId)` | Promote an admitted candidate by inserting memory text and publishing `MemoryReadyForEmbedding`. |
| `MEMORY_EMBEDDINGS` | `MemoryReadyForEmbedding(candidateId)` | Embed stored memory text with OCI, persist the vector, and mark the candidate complete. |

Internal OSON events carry IDs. The Kafka key matches the canonical UUID or decimal transcript ID. Topics are configurable, have one partition in this example, and use `<topic>_PROCESSOR` consumer groups. Each consumer polls at most one record so a failure is isolated to one stage event.

## Transaction ownership and retries

`TransactionalEventConsumer` owns its consumer, polling thread, and transactional producer. The producer uses the connection obtained from `consumer.getDBConnection()`. Each repository exposes `from(Connection)`, using `SingleConnectionJdbcClientFactory` to wrap that connection with close suppression. Services use these factories and never start a separate pooled transaction for processing.

`producer.commitTransaction()` commits the consumed input, all relational changes, and all emitted stage events together. `abortTransaction()` rolls them back for redelivery. Stable IDs, unique constraints, and status checks also make explicitly duplicated events harmless. Exactly-once processing applies to the database and queue effects of a stage. External OCI calls may repeat after rollback.

Processing failures pause for one second before retrying. A permanently failing event blocks its stage's partition until corrected; this lab does not provide dead-letter routing. Fatal connection or rollback failures stop the affected consumer. Restarting the application resumes from committed queue state. No `next_attempt_at` fields or SQL scans schedule work.

## Durable data

The three tables retain accepted data only. Dropped events are acknowledged after a warning containing the source ID and reason; their payloads are not logged. With no rejection ledger, a later allowed event can reuse a previously dropped ID. A unique transcript source ID prevents duplicate accepted inserts and handoff events.

| Table | Purpose | States |
| --- | --- | --- |
| `transcripts` | Original native `JSON` payload, owner, source UUID, and numeric transcript ID | `READY`, `DONE`, or `NO_MEMORY`. |
| `candidate_work` | Candidate UUID, transcript provenance, evidence, proposed fact, and judge score | `READY`, `PENDING_EMBEDDING`, or `COMPLETE`. |
| `event_memories` | Memory UUID, candidate link, owner, immutable text, vector, status, and expiry | A null vector is durable but excluded from search. |

Source event IDs, candidate IDs, and memory IDs use `RAW(16)` and the shared JDBC UUID encoding. Candidate IDs are random UUIDs; memory IDs derive from candidate IDs.

Extraction and judging are separate Spring AI `ChatModel` calls. The judge evaluates source support, attribution, durability, usefulness, and sensitivity and returns an integer score from 0 to 100. `memory.candidates.score-threshold` defaults to 70 in `application.yml`; only scores strictly above it are persisted. Accepted scores are stored in `candidate_work.judge_score`. Low scores are discarded, and a transcript with no admitted candidates becomes `NO_MEMORY`. Malformed, missing, fractional, or out-of-range scores fail the transaction for retry. Exact evidence matching, regex secret filtering, and prefix-based admission are removed. Curation promotes admitted candidates without another admission decision.

## Retrieval and model configuration

Search accepts `query` and `limit`. Owner scope comes from trusted configuration. SQL filters active, unexpired, owner-matching memories with non-null vectors of the query's dimensions. The score combines vector similarity, text matching, and recency. Responses include memory ID, text, status metadata, and scores, never source transcripts.

All memory and search vectors use one embedding model configured under Spring AI. If that model changes, all existing memory data must be re-embedded, even when dimensions stay the same. Re-embedding is not automatic.

## Validation and compatibility

Deterministic database tests use test-only model fixtures. Live integration tests run the real four-topic OKafka pipeline with OCI, verify rollback/redelivery, check that rows and emitted events commit together, and exercise embedding-event retries.

Earlier SQL polling data needs migration plus stage-event backfill, or a disposable volume reset and republication. The README documents the reset. Native JSON transcripts, required judge scores, source IDs, embedding metadata, and retry-field schema changes are also incompatible with earlier volumes.

The example assumes one configured owner and local sample credentials. Production authentication, dead-letter handling, retention/deletion, and human review remain outside its scope.
