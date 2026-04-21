---
name: json/jpa-duality-views
description: Spring Boot JPA sample for generating and using JSON Relational Duality Views.
tags:
  - Database
  - Duality Views
  - JPA
  - Java
  - JSON
  - SpringBoot
  - Testcontainers
blog_post: "https://andersswanson.dev/2026/02/25/generate-json-relational-duality-views-from-jpa-entities/"
---

# Spring Boot JSON Relational Duality Views

This sample shows how to generate and interact with JSON Relational Duality Views (JDVs) directly from familiar Spring Data JPA entities. It demonstrates:

- Annotating entities with `@JsonRelationalDualityView` to let Oracle AI Database generate bidirectional JSON documents over relational tables.
- Using the Oracle JSON-B binding (`JSONB`) to persist and retrieve strongly typed entities via JDVs.
- Bootstrapping schema and duality view lifecycle through Spring Boot configuration.
- Exercising CRUD-style flows with integration tests powered by Testcontainers and Oracle AI Database Free.

## Prerequisites

- Java 2.
- Maven 3.9+.
- Docker Desktop or another OCI-compatible container runtime (required for the Testcontainers-based integration tests).

## Project Layout

- `src/main/java/com/example/jdv/movie`: JPA aggregate for the movie catalog (`Actor`, `Movie`, `Director`, and `DirectorBio`). Each entity illustrates different relationship mappings (one-to-many, many-to-many, and one-to-one) and how those translate into JDVs.
- `src/main/java/com/example/jdv/controller/JDVController.java`: Minimal REST controller that persists entities through JDVs using `JSONB` to serialize to Oracle OSON payloads.
- `src/main/resources/application.yaml`: Enables `spring.jpa.dv.ddl-auto=create-drop` so duality views are managed alongside the schema and configures the Oracle UCP datasource.
- `src/test/java/com/example/jdv/ApplicationTest.java`: End-to-end test that boots Oracle AI Database Free inside a Testcontainers-managed container.

## Configure the Database Connection

Set the following environment variables (or override them via the `application.yaml`) before running the sample:

- `DB_URL` – JDBC connect string, for example `jdbc:oracle:thin:@localhost:1521/freepdb1`.
- `DB_USERNAME` – database user with privileges to create tables, JSON types, and duality views.
- `DB_PASSWORD` – password for the user above.

If these variables are omitted, the sample defaults to `testuser` / `testpwd` against `localhost` as shown in `src/main/resources/application.yaml`.

## Run the Application

From the repository root:

```bash
mvn -pl json/jpa-duality-views -am spring-boot:run
```

Spring Boot will start on port 8080. The JDV lifecycle listener (`com.oracle.spring.json.duality.builder`) inspects the annotated entities during startup and creates the JSON Relational Duality Views in the connected database.

### Example Requests

Create a movie:

```bash
curl -X POST http://localhost:8080/movie \
  -H 'Content-Type: application/json' \
  -d '{
        "movieId": "2f273c9c-754f-4cb1-a91a-3f5963b73e14",
        "title": "The Duality",
        "genre": "sci-fi",
        "releaseYear": 2024
      }'
```

Create an actor and link it to the movie:

```bash
curl -X POST http://localhost:8080/actor \
  -H 'Content-Type: application/json' \
  -d '{
        "actorId": "9c4a2f4b-7c0b-42fb-a018-7fb872c8045e",
        "firstName": "Ada",
        "lastName": "Lovelace",
        "movies": [
          {
            "movieId": "2f273c9c-754f-4cb1-a91a-3f5963b73e14"
          }
        ]
      }'
```

Because the controller persists through the JDV, the relational tables (`actor`, `movie`, `movie_actor`) stay synchronized without writing SQL manually.

### Inspect the Generated Duality Views

After the first run, connect to the database and query the duality view that corresponds to the `Movie` entity:

```sql
select json_serialize(data pretty) as movie_json
from movie_dv;
```

This returns a nested JSON document that includes the movie metadata as well as linked actors, reflecting the structure of the entity graph.

## Run the Tests

Pull the Oracle AI Database Free image ahead of time to prevent timeouts:

```bash
docker pull gvenzl/oracle-free:23.26.1-slim-faststart
```

Then execute:

```bash
mvn json/jpa-duality-views test
```

`ApplicationTest` spins up Oracle AI Database Free in a container, boots the Spring context, and exercises the controller methods to verify that JDV inserts and lookups operate bidirectionally.

## Key Concepts Illustrated

- **Annotation-driven JDV creation**: `Movie` and `Actor` are decorated with `@JsonRelationalDualityView`, instructing the starter to generate read/write views that mirror the entity schema. Nested relationships (such as the `Actor.movies` collection) obtain their own embedded JDVs by naming them via the annotation.
- **JSONB serialization**: `JDVController` converts entities into Oracle OSON payloads with `JSONB.toOSON` before inserting through the JDV. Retrieval uses `JSONBRowMapper` to hydrate domain objects from JSON results.
- **Schema + JDV lifecycle management**: Both the relational schema (`spring.jpa.hibernate.ddl-auto=create-drop`) and the JDVs (`spring.jpa.dv.ddl-auto=create-drop`) are managed automatically during app startup and shutdown.
- **Testcontainers integration**: The sample demonstrates how to wire the Testcontainers `OracleContainer` via Spring Boot's `@ServiceConnection`, providing a reproducible environment for integration tests.

## Next Steps

- Extend the sample by exposing additional REST operations (GET by ID, list, delete) or by adding DTO mapping on the controller layer.
- Explore how nested duality views behave by uncommenting the annotations on `Director` and `DirectorBio` for richer JSON documents.
- Combine this module with the `news-event-streaming` sample to stream JDV payloads through messaging APIs.
