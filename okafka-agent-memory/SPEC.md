# OKafka event-to-memory lab

## Goal

Teach an event-driven path from an OKafka transcript to searchable, owner-scoped memory. Oracle AI Database TxEventQ carries each stage event; OCI Generative AI extracts facts and generates embeddings. There is no SQL polling worker or separate Kafka broker.

## Learner path

1. Configure `~/.oci/config` and export `OCI_COMPARTMENT_ID`.
2. `docker compose up -d --wait` creates Oracle AI Database Free and the application schema.
3. `mvn spring-boot:run` starts four stage consumers and the search API.
4. Run the separate `DemoProducer` process to publish a fact and print its UUID source event ID.
5. Inspect the four tables, then search after the embedding stage completes.

`memory.background-processing.enabled` defaults to `true`. The producer sets it to `false` only in its own process. The search API remains available when consumers are disabled.

## Event pipeline

| Topic | Event | Durable changes and output |
| --- | --- | --- |
| `MEMORY_INCOMING` | `IncomingEvent` | Store a receipt; accepted events also store a transcript and publish `TranscriptReady`. Duplicate source IDs return the original receipt without publishing another handoff. |
| `MEMORY_TRANSCRIPTS` | `TranscriptReady(transcriptId)` | Extract facts with OCI, check evidence and sensitive content, persist zero or many candidates, mark preparation complete, and publish `CandidateReady` events. |
| `MEMORY_CANDIDATES` | `CandidateReady(candidateId)` | Curate a ready candidate; promotion inserts memory text and publishes `MemoryReadyForEmbedding`. Review and rejected candidates have no next event. |
| `MEMORY_EMBEDDINGS` | `MemoryReadyForEmbedding(candidateId)` | Embed stored memory text with OCI, persist the vector, and mark the candidate complete. |

Internal OSON events carry IDs. The Kafka key matches the canonical UUID or decimal transcript ID. Topics are configurable, have one partition in this example, and use `<topic>_PROCESSOR` consumer groups. Each consumer polls at most one record so a failure is isolated to one stage event.

## Transaction ownership and retries

`TransactionalEventConsumer` owns its consumer, polling thread, and transactional producer. The producer uses the connection obtained from `consumer.getDBConnection()`. `SingleConnectionJdbcClientFactory` wraps that connection with close suppression. Services create their repositories against this client and never start a separate pooled transaction for processing.

`producer.commitTransaction()` commits the consumed input, all relational changes, and all emitted stage events together. `abortTransaction()` rolls them back for redelivery. Stable IDs, unique constraints, and status checks also make explicitly duplicated events harmless. Exactly-once processing applies to the database and queue effects of a stage. External OCI calls may repeat after rollback.

Processing failures pause for one second before retrying. A permanently failing event blocks its stage's partition until corrected; this lab does not provide dead-letter routing. Fatal connection or rollback failures stop the affected consumer. Restarting the application resumes from committed queue state. No `next_attempt_at` fields or SQL scans schedule work.

## Durable data

| Table | Purpose | States |
| --- | --- | --- |
| `intake_outcomes` | UUID source event receipt and optional transcript link | `ACCEPTED` or `FILTERED`; `PENDING` exists only within the transaction. |
| `transcripts` | Original JSON, owner, source UUID, and numeric transcript ID | `READY`, `DONE`, or `NO_MEMORY`. |
| `candidate_work` | Candidate UUID, transcript provenance, evidence, and proposed fact | `READY`, `REVIEW`, `REJECTED`, `PENDING_EMBEDDING`, or `COMPLETE`. |
| `event_memories` | Memory UUID, candidate link, owner, immutable text, vector, status, and expiry | A null vector is durable but excluded from search. |

Source event IDs, candidate IDs, and memory IDs use `RAW(16)` and the shared JDBC UUID encoding. Candidate IDs derive from source event ID and fact text; memory IDs derive from candidate IDs.

Curation retains the existing demonstration prefix policy: `maybe ` requests review, `reject ` rejects, and other candidates are promoted. These are not a production policy and there is no review UI.

## Retrieval and model configuration

Search accepts `query` and `limit`. Owner scope comes from trusted configuration. SQL filters active, unexpired, owner-matching memories with non-null vectors of the query's dimensions. The score combines vector similarity, text matching, and recency. Responses include memory ID, text, status metadata, and scores, never source transcripts.

All memory and search vectors use one embedding model configured under Spring AI. If that model changes, all existing memory data must be re-embedded, even when dimensions stay the same. Re-embedding is not automatic.

## Validation and compatibility

Deterministic database tests use test-only model fixtures. Live integration tests run the real four-topic OKafka pipeline with OCI, verify rollback/redelivery, check that rows and emitted events commit together, and exercise embedding-event retries.

Earlier SQL polling data needs migration plus stage-event backfill, or a disposable volume reset and republication. The README documents the reset. Source IDs, embedding metadata, and retry-field schema changes are also incompatible with earlier volumes.

The example assumes one configured owner and local sample credentials. Production authentication, dead-letter handling, retention/deletion, and human review remain outside its scope.
