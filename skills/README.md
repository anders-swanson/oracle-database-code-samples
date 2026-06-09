---
name: skills
description: Reusable Codex skills for building, validating, and documenting Oracle AI Database samples.
tags:
  - AI
  - Oracle AI Database
  - Skills
blog_post: "https://andersswanson.dev/2026/05/20/an-agent-skill-that-uses-kafka-java-apis-for-oracle-ai-database/"
---

# Skills

This directory contains reusable Codex skills for repeatable Oracle AI Database sample workflows. Each skill packages project-specific instructions, references, and validation expectations so future work can start from the same operating model.

| Skill | Use When | Covers | Validation Expectations |
| --- | --- | --- | --- |
| [OKafka Java Code](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/skills/okafka-java-code/SKILL.md) | Building or reviewing Java samples that use Oracle AI Database Transactional Event Queues through Oracle Kafka APIs/OKafka. | Topic creation, OKafka authentication, `ojdbc.properties` and wallet setup, OSON JSON event serialization, producers, consumers, Spring-managed clients, and transactional produce/consume flows. | Prefer Oracle AI Database Free/Testcontainers integration tests that create a topic, produce records, consume records, and verify the TxEventQ backing table or related database side effect. |
