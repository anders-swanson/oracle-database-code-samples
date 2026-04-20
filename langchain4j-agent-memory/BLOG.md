# Durable Agent Memory with LangChain4j and Oracle AI Database

One of the easiest ways to make an agent feel impressive in a demo is to give it a clever prompt.

One of the easiest ways to make that same agent fall apart in production is to give it no durable memory.

That is the problem this `langchain4j-agent-memory` sample is trying to solve.

In this module, we use LangChain4j to build a small ops handoff assistant backed by Oracle AI Database. The assistant can search prior incidents, runbooks, decisions, and shift handoffs. It can also write new handoff notes back into the database so they become part of the next conversation. On top of that, it stores an append-only transcript of the conversation, including tool interactions.

This is not “chat history in a list.”

It is a more useful pattern:

- durable JSON memory documents in Oracle AI Database
- vector embeddings in a `VECTOR` column
- Oracle Text search over the same JSON document
- hybrid ranking that blends semantic and exact-match retrieval
- append-only transcript logging by conversation ID

If you are building agents in Java, this is a practical pattern worth knowing.

## What the sample actually builds

The sample models a fictional commerce operations assistant. Its seeded memory set includes runbooks, incident reviews, architecture decisions, and shift handoffs for services like `checkout`, `payments`, `inventory`, and `identity`.

That gives us a realistic retrieval problem.

Sometimes the operator remembers the exact reference:

```text
What happened during INC4721 after CHG2145?
```

Sometimes they only remember the shape of the incident:

```text
Customers could browse but orders failed right after the checkout rollout.
```

Those are not the same search problem.

Exact references like `INC4721` and `CHG2145` are where Oracle Text shines. Paraphrases are where embeddings and vector similarity help. This sample combines both so the assistant is not forced into a false choice between full-text retrieval and semantic retrieval.

## Why not just use chat memory?

Because chat memory and durable memory solve different problems.

LangChain4j’s in-memory chat window is useful for local conversational context. In this sample, the active window is still capped at 20 messages. That is enough to keep the current exchange coherent, but it is not a long-term memory system.

Operational memory has different requirements:

- it should survive process restarts
- it should be queryable across conversations
- it should support structured metadata like service, environment, incident ID, and change ticket
- it should be searchable both semantically and exactly
- it should allow writeback when the agent learns something worth preserving

That starts to look a lot more like a database problem than a prompt engineering problem.

## Run the sample

You will need Java 21+, Maven, Docker, and an `OPENAI_API_KEY`.

From the repository root:

```bash
export OPENAI_API_KEY=<your key>
mvn test -pl langchain4j-agent-memory
```

The integration test spins up Oracle AI Database Free with Testcontainers, initializes the schema, loads the seed memories, and validates retrieval behavior.

To run the live terminal app:

```bash
export OPENAI_API_KEY=<your key>
mvn -pl langchain4j-agent-memory compile exec:java \
  -Dexec.args="jdbc:oracle:thin:@localhost:1521/freepdb1 testuser testpwd"
```

Once it starts, try prompts like:

- `What happened during the checkout incident after CHG2145?`
- `Which runbook section should I use for the checkout rollback?`
- `Draft a next-shift handoff and remember it.`

## The memory model is intentionally simple

Each durable memory is stored as a JSON document with a little structure around it:

- `memoryKind`
- `title`
- `summary`
- `body`
- `service`
- `environment`
- `severity`
- optional `incidentId`
- optional `changeTicket`
- tags, timestamp, and source metadata

That is enough structure to support real retrieval without turning the sample into a giant schema exercise.

The corresponding table is also straightforward:

```sql
create table if not exists agent_memories (
    id number generated always as identity primary key,
    memory_kind varchar2(30) not null,
    title varchar2(200) not null,
    memory_doc json not null,
    embedding vector(%d, FLOAT32) annotations(Distance 'COSINE', IndexType 'IVF')
)
```

There are two important design choices here.

First, the memory document stays in native JSON form. That keeps the write path simple and makes it easy to evolve the payload.

Second, the embedding lives right beside it in the same table. That makes hybrid retrieval much easier to reason about than scattering memory across multiple stores.

## Hybrid retrieval is the real point

The most interesting part of this sample is not “LangChain4j can call a tool.” That part is easy.

The interesting part is how the tool searches memory.

The `MemoryRepository` runs two retrieval branches:

1. Vector search over the `embedding` column using cosine distance.
2. Oracle Text search over the JSON payload using `json_textcontains`.

Then it fuses the results into one ranked list.

Here is the vector query:

```sql
select id,
       memory_kind,
       title,
       memory_doc,
       (1 - vector_distance(embedding, ?, COSINE)) as vector_score
from agent_memories
order by vector_score desc, id
fetch first ? rows only
```

And here is the text query:

```sql
select id,
       memory_kind,
       title,
       memory_doc,
       score(1) as text_score
from agent_memories
where json_textcontains(memory_doc, '$', ?, 1)
order by score(1) desc, id
fetch first ? rows only
```

This is a good pattern for developer-facing agent memory because real operator prompts are messy. People mix IDs, service names, fragments of runbook wording, half-remembered incidents, and plain English symptoms in the same question.

Pure vector search is often too fuzzy for ticket IDs.

Pure text search is often too brittle for paraphrases.

Hybrid retrieval handles both.

## Query hints make the text search better

The sample does one more useful thing before the text search runs: it extracts query hints.

`QueryHintExtractor` looks for:

- incident IDs like `INC4721`
- change tickets like `CHG2145`
- known services like `checkout` and `payments`
- useful keywords after stop-word cleanup

Those keywords are then combined into an Oracle Text expression using `ACCUM`.

That gives the text branch a much better chance of recovering the exact operational record the user actually cares about.

It is a small touch, but it makes the sample feel much closer to a real incident assistant than a toy vector demo.

## Rank fusion is lightweight, but effective

Once both branches return hits, `MemorySearchRanker` merges them with reciprocal rank fusion plus a few bonuses:

- a bonus when the incident ID or change ticket matches directly
- a bonus for keyword overlap in the indexed memory text
- a combined `matchedBy` indicator of `VECTOR`, `TEXT`, or `BOTH`

I like this approach because it is easy to explain, easy to debug, and easy to tune.

You do not need a giant reranker stack to get decent results for a narrowly scoped operational assistant. Sometimes a small amount of ranking logic plus good metadata gets you most of the value.

## LangChain4j stays in the orchestration layer

The LangChain4j portion is refreshingly small.

The assistant interface is just:

```java
@SystemMessage("""
        You are an operations handoff assistant backed by Oracle AI Database memory.
        Use searchMemories when prior incidents, runbooks, handoffs, decisions, or change history are relevant.
        When you rely on memory results, include the references in the form [M123].
        If the user asks you to remember or preserve a new handoff or decision, call storeMemory after drafting it.
        Keep answers concise and operational. Mention incident IDs and change tickets when they matter.
        """)
@UserMessage("{{message}}")
String chat(@V("message") String userMessage);
```

That is the right level of abstraction for this sample.

LangChain4j handles chat orchestration and tool wiring. Oracle AI Database handles durable memory, search, and transcript persistence. Each layer is doing the job it is actually good at.

## The writeback path matters

A lot of memory demos only retrieve.

This one also stores new durable memory through the `storeMemory` tool when the user explicitly asks the assistant to preserve a handoff or decision.

That matters because an agent memory system should not just be a read-only archive. If a useful conclusion comes out of a conversation, the system should be able to keep it.

In this sample, writeback creates a new `MemoryDocument`, generates an embedding, and inserts both the JSON payload and vector into `agent_memories`. Because the JSON search index is configured with `sync (on commit)`, newly stored handoffs are searchable immediately after commit.

That last detail is important. Delayed indexing is exactly the kind of thing that makes an agent feel unreliable.

## Transcript logging is separate from memory on purpose

The sample also writes an append-only transcript to `agent_conversation_log`.

That table captures:

- `conversation_id`
- `message_seq`
- role and message type
- message text
- tool name and tool call ID when relevant
- optional JSON context
- creation timestamp

This separation is a good design choice.

Not every conversation message should become durable semantic memory. Some messages are just interaction exhaust. Some are useful enough to log for auditability or replay, but not useful enough to surface in future retrieval.

So the sample keeps two stores:

- a curated durable memory store for retrieval
- an append-only transcript store for observability

That distinction tends to get blurred in agent demos. It should not.

## The tests validate the behavior that matters

The integration tests are worth reading because they verify the actual retrieval patterns we care about:

- exact text search finds the checkout incident for `CHG2145` and `INC4721`
- vector search finds the same incident from a paraphrased outage description
- hybrid fusion marks the strongest result as matched by both channels
- a stored handoff can be found on the next combined search

That is the right test surface for a sample like this. It tests the memory behavior, not just the existence of tables.

## Why this pattern is useful

If you are building internal developer tools, ops copilots, runbook assistants, or support agents, this pattern scales much better than keeping everything in a chat window or a pile of flat files.

A database-backed memory layer gives you:

- durable storage
- structured metadata
- semantic retrieval
- exact text retrieval
- transactional writes
- better auditability

More importantly, it gives you something you can evolve. You can add recency weighting, memory decay, approval workflows, tenant isolation, richer ranking, or summarization jobs later without rewriting the whole system.

That is usually the real difference between an agent demo and an agent application.

## Where I would take it next

If I were extending this sample, I would look at a few things next:

1. Add recency into the rank fusion so fresher handoffs win when two memories are otherwise similar.
2. Separate “candidate transcript facts” from durable memory with an approval or summarization pass.
3. Add service and environment filters directly into retrieval scoring.
4. Store lightweight evaluation data so retrieval quality can be measured over time.

None of that changes the core idea.

The core idea is already solid: use LangChain4j for agent orchestration, and use Oracle AI Database as the durable memory system the agent can actually depend on.

## Code pointers

If you want to explore the implementation, start here:

- [`README.md`](./README.md)
- [`OpsMemoryAgentApplication.java`](./src/main/java/com/example/memory/OpsMemoryAgentApplication.java)
- [`MemoryRepository.java`](./src/main/java/com/example/memory/search/MemoryRepository.java)
- [`MemoryTools.java`](./src/main/java/com/example/memory/agent/MemoryTools.java)
- [`LoggingChatMemory.java`](./src/main/java/com/example/memory/transcript/LoggingChatMemory.java)
- [`MemoryRepositoryIntegrationTest.java`](./src/test/java/com/example/memory/MemoryRepositoryIntegrationTest.java)

That is the full loop: seed durable memory, retrieve it through hybrid search, answer with citations, persist new handoffs, and log the conversation trail.

For an agent memory sample, that is a lot more interesting than “remember the last five messages.”
