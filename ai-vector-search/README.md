# Similarity Search using Oracle Database 23ai

This code sample demonstrates how to use Oracle Database 23ai as a vector store for similarity search on text embeddings.

The [OracleVectorSample](src/main/java/com/example/OracleVectorSample.java) implements a vector store abstraction that supports inserting embeddings into the database, and querying embeddings.

To learn more about Vector Database, read my article [Intro to Vector Databases](https://andersswanson.dev/2025/06/23/whats-a-vector-database/)

## Running the sample

Prerequisites:
- Maven
- Java 21+
- A docker environment to support TestContainers

Run the sample from the project root directory:

```shell
mvn integration-test
```