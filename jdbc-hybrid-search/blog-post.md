[Hybrid search](https://en.wikipedia.org/wiki/Hybrid_search) is one of those ideas that sounds more complicated than it is. In practice, it means combining multiple ranking and filtering signals in one query so you get results that are both semantically relevant and operationally useful.

That matters because vector similarity alone is rarely enough. A result might be semantically close to a query, but still be the wrong category, the wrong audience, or outside the user’s budget. In a real application, you usually want all of those constraints working together.

[Oracle AI Database includes first-class vector support](https://www.oracle.com/database/ai-vector-search/) directly inside the database engine, alongside relational columns and JSON. That makes it a strong fit for hybrid search, because you can rank by vector distance and filter by ordinary SQL predicates in the same statement. In this article, we’ll do exactly that from plain Java over JDBC using the sample [`jdbc-hybrid-search`](https://github.com/anders-swanson/oracle-database-java-samples/tree/main/jdbc-hybrid-search) module.

The sample loads a small catalog of product-style documents, embeds each document with a local MiniLM model, stores the embeddings in a `VECTOR` column, runs a hybrid query, and writes an SVG diagram so you can visualize cosine distance from the top tutorial.

We’ll learn:

- What hybrid search is and why it matters
- How to store vector embeddings in Oracle AI Database with JDBC
- How to combine `VECTOR_DISTANCE`, relational filters, and JSON predicates in one SQL statement
- Why the sample stores embeddings as `VECTOR(384, FLOAT32)`
- How to run the module locally with Maven and inspect the generated distance map

## Hybrid search primer

At a high level, hybrid search combines semantic similarity with structured filtering.

Semantic similarity answers questions like:

- Which documents mean something similar to this query?
- Which tutorial is closest in intent, even if it does not share the same exact words?

Structured filtering answers questions like:

- Is this result in the right category?
- Is it under the budget cap?
- Is it meant for beginners?
- Does it have the topic tag I care about?

If you use only lexical search, you miss meaning. If you use only vector search, you often lose control. Hybrid search keeps both.

This sample keeps the idea deliberately simple:

- Rank rows with `VECTOR_DISTANCE`
- Filter by relational columns such as `category` and `price`
- Filter by JSON metadata such as `audience` and `topics`

That is enough to demonstrate the core pattern you will use in many production systems.

## Sample overview

The module centers on a few small classes:

- `HybridSearchSample`, which drives the end-to-end flow
- `SampleDataLoader`, which recreates the schema and seeds the sample catalog
- `VectorUtil`, which embeds text and converts it into an Oracle `VECTOR`
- `DiagramRepository` and `DiagramGenerator`, which read back the stored documents and write the SVG distance map

The sample data models a catalog of tutorials and reference material. A few example titles are:

- Oracle Vector Search for Beginners
- Budget-Friendly Hybrid Search Recipes
- Production Hybrid Search Tuning
- Beginner Text Search with Oracle Text

That gives us enough variety to show:

- semantic similarity between documents
- relational filtering by category and price
- JSON filtering by audience and topic
- a simple radial visualization of cosine distance from the center tutorial

## The schema: relational data, JSON metadata, and one vector column

The core table is small and readable:

```sql
create table hybrid_documents (
    id number generated always as identity primary key,
    title varchar2(200) not null unique,
    content clob not null,
    category varchar2(30) not null,
    price number(10,2) not null,
    metadata json not null,
    embedding vector(384, FLOAT32) annotations(Distance 'COSINE', IndexType 'IVF')
)
```

This is the key idea of the sample. A vector column is not a separate subsystem. It lives in an ordinary application table right next to your other business data.

The module also creates:

- a vector index on `embedding`
- a conventional B-tree index on `(category, price)`

That combination matches the hybrid-search story well. The vector column supports semantic ranking, while the relational index supports cheap structured filtering.

## Why `FLOAT32` is a good fit here

The sample uses:

```sql
embedding vector(384, FLOAT32)
```

That choice is deliberate. The local MiniLM embedding model already produces `float[]` values in Java, so storing the vector as `FLOAT32` keeps the in-memory representation and the database representation aligned.

In `VectorUtil`, the sample does two things:

1. embed text into a `float[]`
2. normalize that vector before writing it as an Oracle `VECTOR`

```java
static VECTOR embedText(String text) throws SQLException {
    return toOracleVector(embeddingForText(text));
}

private static VECTOR toOracleVector(float[] vector) throws SQLException {
    return VECTOR.ofFloat32Values(normalize(vector));
}
```

That normalization is important because the query uses cosine distance. By normalizing once before write, the sample keeps the stored representation aligned with cosine-based similarity logic.

## Full code walkthrough

Let’s walk through the sample from top to bottom.

### 1. Build a JDBC data source

The `main` method accepts three arguments:

- JDBC URL
- username
- password

It creates a plain Oracle JDBC datasource:

```java
OracleDataSource ds = new OracleDataSource();
ds.setURL(url);
ds.setUser(username);
ds.setPassword(password);
```

There is nothing vector-specific here, and that is part of the point. Hybrid search fits naturally into an ordinary JDBC application.

### 2. Recreate the schema and load sample data

The sample begins by rebuilding the schema from `schema.sql` and loading `documents.json`:

```java
List<Document> documents = SampleDataLoader.loadSampleData(connection);
```

Inside `SampleDataLoader`, the sample:

1. drops and recreates the table and indexes
2. reads the JSON catalog from `src/main/resources/documents.json`
3. embeds each document’s content
4. inserts the document, metadata, and vector into the database

The insert SQL is simple:

```sql
insert into hybrid_documents (title, content, category, price, metadata, embedding)
values (?, ?, ?, ?, ?, ?)
```

And the embedding write is equally direct:

```java
statement.setObject(6, VectorUtil.embedText(document.content()));
```

That is a good teaching detail. There is no extra service layer here. You can see the exact moment where document text becomes an Oracle vector value.

### 3. Model the hybrid query with one request object

The sample wraps the search inputs in a `SearchRequest` record:

```java
SearchRequest request = new SearchRequest(
        "oracle jdbc vector search for beginners",
        "tutorial",
        50.0,
        "beginner",
        "vector",
        3,
        0.70d
);
```

This request captures all three kinds of search signal in one place:

- free-text query for semantic matching
- relational filters like category and max price
- JSON metadata filters like audience and topic

That makes the sample easier to follow than passing seven loose parameters around.

### 4. Rank by vector similarity and filter by structured data

The most important part of the sample is the SQL itself:

```sql
select id, title, category, price, audience, score
from (
    select id,
           title,
           category,
           price,
           json_value(metadata, '$.audience') as audience,
           (1 - vector_distance(embedding, ?, COSINE)) as score
    from hybrid_documents
    where category = ?
      and price <= ?
      and json_value(metadata, '$.audience') = ?
      and json_exists(metadata, '$.topics[*]?(@ == $topic)' passing ? as "topic")
)
where score >= ?
order by score desc, price, title
fetch first ? rows only
```

This query is the heart of the article.

There are three things happening together:

1. `VECTOR_DISTANCE(embedding, ?, COSINE)` measures semantic distance between the stored document vector and the query vector
2. relational predicates constrain the result set with `category = ?` and `price <= ?`
3. JSON predicates constrain the metadata with `JSON_VALUE` and `JSON_EXISTS`

The sample turns cosine distance into a score with:

```sql
(1 - vector_distance(embedding, ?, COSINE)) as score
```

That is a useful teaching trick because higher scores are easier to read than lower distances.

### 5. Bind the query vector from Java

The `search(...)` method embeds the search text and binds the resulting `VECTOR` directly into the prepared statement:

```java
statement.setObject(1, VectorUtil.embedText(request.text()));
```

Then it binds the relational and JSON inputs in the normal JDBC way:

```java
statement.setString(2, request.category());
statement.setDouble(3, request.maxPrice());
statement.setString(4, request.audience());
statement.setString(5, request.topic());
statement.setDouble(6, request.minScore());
statement.setInt(7, request.maxResults());
```

This is one of the nicest aspects of the sample. The vector parameter is just one more bound value in a prepared statement. You do not have to leave JDBC or introduce a separate vector client library.

### 6. Understand the result set

For the built-in request, the sample returns two results:

```text
Oracle Vector Search for Beginners | category=tutorial | price=0.00 | audience=beginner | score=0.7782
Budget-Friendly Hybrid Search Recipes | category=tutorial | price=29.00 | audience=beginner | score=0.7037
```

That result tells a good hybrid-search story.

The top result is the exact beginner tutorial you would expect. The second result is still semantically related, still a tutorial, still aimed at beginners, and still under the price cap. Documents that may be semantically related but are too expensive, too advanced, or not tagged with the `vector` topic are filtered out before they can rank.

### 7. Generate a simple cosine-distance diagram

The `DiagramGenerator` class is a useful addition because it lets you see the semantic relationships instead of only reading scores from the console.

The generator reads the stored documents and a few pairwise cosine distances back out through `DiagramRepository`. It then lays the documents out in a radial chart where:

- point `1` is always the center tutorial
- point radius is scaled from cosine distance to the center
- angle is used only to separate labels

That last point matters. This is not a full geometric projection of embedding space. It is a teaching visualization of “how far is each document from the center tutorial under cosine distance?”

The highlighted distance measurements make that explicit:

- Oracle Vector Search for Beginners -> Budget-Friendly Hybrid Search Recipes
- Oracle Vector Search for Beginners -> Production Hybrid Search Tuning
- Oracle Vector Search for Beginners -> Beginner Text Search with Oracle Text

That gives you a compact visual intuition for which documents stay close to the center topic and which ones drift farther away.

### 8. Verify the module end to end with Testcontainers

The test class, `HybridSearchSampleTest`, uses `gvenzl/oracle-free:23.26.1-full-faststart` through Testcontainers and runs the application end to end.

That means the sample is not just a code sketch. It actually:

- starts Oracle AI Database Free
- recreates the schema
- loads the sample catalog
- runs the hybrid query
- generates the SVG distance map

That is a strong pattern for sample code because readers can run exactly what they are reading.

## Why this sample works well as an introduction

What I like about this module is that it introduces hybrid search without hiding the mechanics.

You can see every major piece directly:

- how documents are loaded
- how embeddings are created
- how vectors are stored
- how a hybrid query is written
- how the result set is interpreted
- how cosine distance can be visualized

It also avoids a common trap in vector-search examples: pretending the vector search exists in isolation. In real systems, structured filters almost always matter. Category, budget, audience, and metadata are not afterthoughts. They are part of the search behavior users actually need.

## How to run the sample

You have two easy ways to run this module.

### Option 1: run the test

From the repository root:

```bash
mvn -pl jdbc-hybrid-search test
```

The test starts Oracle AI Database Free with Testcontainers, runs the sample end to end, and generates the SVG diagram.

You should see output similar to:

```text
Loaded documents: 12
Hybrid search for: oracle jdbc vector search for beginners
Oracle Vector Search for Beginners | category=tutorial | price=0.00 | audience=beginner | score=0.7782
Budget-Friendly Hybrid Search Recipes | category=tutorial | price=29.00 | audience=beginner | score=0.7037
Hybrid search diagram written to: ./jdbc-hybrid-search/hybrid-search-diagram.svg
```

### Option 2: run the application directly

If you already have an Oracle AI Database instance available, run the `main` class with your JDBC connection settings:

```bash
mvn -pl jdbc-hybrid-search exec:java \
  -Dexec.args="jdbc:oracle:thin:@localhost:1521/freepdb1 testuser testpwd"
```

The application will:

1. recreate the schema
2. load the sample documents
3. run the hybrid search
4. print the results
5. write `hybrid-search-diagram.svg` into the `jdbc-hybrid-search` module directory

## Closing thoughts

Hybrid search in Oracle AI Database does not require a separate stack. This sample shows that you can embed text locally, store vectors in a normal table, combine semantic ranking with relational and JSON filters, and run everything from plain JDBC.

That is a practical mental model for a lot of modern applications:

- learning portals
- internal knowledge bases
- product catalogs
- documentation search
- support tooling

If you want a compact sample that demonstrates how vector search becomes more useful when it works with the rest of your schema instead of around it, `jdbc-hybrid-search` is a good place to start.
