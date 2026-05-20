---
name: okafka-java-code
description: Build Java code that uses Oracle AI Database Transactional Event Queues through Oracle Kafka APIs/OKafka. Use when implementing or reviewing topic creation, OKafka authentication, ojdbc.properties and wallet configuration, OSON JSON event serialization, producer and consumer clients, Spring-managed OKafka beans, Testcontainers validation, or transactional produce/consume flows using org.oracle.okafka and standard Kafka Java APIs.
---

# OKafka Java Code

## Core Workflow

1. Inspect the target project first: Java version, Maven/Gradle style, Spring vs plain Java, existing Oracle JDBC/Testcontainers setup, serialization choices, and current lifecycle ownership.
2. Add or verify the `com.oracle.database.messaging:okafka` dependency. Keep existing project dependency management if a parent POM already defines the version.
3. Build a shared base `Properties` object for Oracle AI Database connectivity, then copy it before adding producer-, consumer-, or admin-specific properties.
4. Create topics through standard Kafka `Admin` APIs backed by `org.oracle.okafka.clients.admin.AdminClient`; use replication factor `0`.
5. Prefer OSON serialization for JSON event payloads. Use String serializers only for simple text demos or when the target contract is already string-based.
6. Prefer standard Kafka interfaces (`Producer`, `Consumer`, `Admin`, `ProducerRecord`, `NewTopic`) at boundaries, with OKafka concrete classes only at construction or when Oracle-specific access is needed.
7. For transactions, use OKafka concrete producer/consumer types because `getDBConnection()` is Oracle-specific and lets Kafka records and database writes share the same transaction.
8. Validate with an integration test or runnable smoke path that creates the topic, produces, consumes, and queries the TxEventQ backing table or related database side effect.

## Reference Loading

Load only the reference needed for the current task:

- `references/source-map.md`: source sample paths and exact upstream commit used for this skill.
- `references/dependencies.md`: Maven artifact coordinates for OKafka and OSON support.
- `references/authentication-and-properties.md`: `AuthenticationExample.java`, `ojdbc.properties`, PLAINTEXT vs SSL, wallet/TNS setup.
- `references/topics-and-admin.md`: topic creation with `AdminClient` and `NewTopic`.
- `references/producer-consumer.md`: simple producer and consumer setup, lifecycle, Spring bean guidance.
- `references/oson-serialization.md`: preferred OSON JSON event serialization patterns and source examples.
- `references/transactions.md`: transactional producer and transactional consumer patterns using `getDBConnection()`.
- `references/testing-and-troubleshooting.md`: Testcontainers setup, grants, validation commands, common failure checks.

## Implementation Rules

- Use `oracle.service.name`, `oracle.net.tns_admin`, and `security.protocol` in the base properties. Add `bootstrap.servers` for `PLAINTEXT`; add `tns.alias` for `SSL`.
- Keep `ojdbc.properties` in the directory passed as `oracle.net.tns_admin`; for local PLAINTEXT tests it can contain `user` and `password`.
- Copy shared `Properties` before mutating per-client settings. Do not reuse one mutable properties object across admin, producer, and consumer clients.
- For JSON event producers, prefer OSON serializers built on `com.oracle.spring.json.jsonb.JSONB` and consume as `byte[]` plus `JSONB.fromOSON(...)`, or use `OSONKafkaSerializationFactory` in Spring apps.
- For non-JSON producers, set serializers and usually `enable.idempotence=true`; add `oracle.transactional.producer=true` only for transactional producers.
- For consumers, set deserializers, `group.id`, `enable.auto.commit=false`, `auto.offset.reset=earliest` when consuming preexisting test records, and commit only after processing succeeds.
- In Spring apps, prefer raw `KafkaProducer` and `KafkaConsumer` beans with lifecycle ownership in configuration. Put `destroyMethod="close"` on raw clients when Spring owns shutdown; for a long-running polling thread that owns final close, set the consumer bean `destroyMethod=""` and stop the thread cooperatively.
- For transactional flows, wrap `beginTransaction()`/`commitTransaction()`/`abortTransaction()` around both `send(...)` and SQL work done through `producer.getDBConnection()`. For consumer transactions, write through `consumer.getDBConnection()` and call `commitSync()` only after database work succeeds.
- Use source examples as patterns, not exact text. Correct sample-specific typos such as `TransationalProducer` when creating new code.

## Validation

Prefer a focused integration test that runs against Oracle Free/Testcontainers when the project already supports containerized tests. At minimum, compile the module and document any validation that could not run because Docker, credentials, wallet files, or Oracle services were unavailable.
