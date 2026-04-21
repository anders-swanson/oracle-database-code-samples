# JSON in Oracle AI Database

Samples in this directory demonstrate how Oracle AI Database treats JSON as a first-class data model, including JSON Relational Duality Views and binary JSON payload handling from Java applications.

## Modules

| Sample                                                 | Description                                                                                                                                                                   |
|--------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [jpa-duality-views](./jpa-duality-views/README.md)     | Generate JSON Relational Duality Views from Spring Data JPA entities, persist JSONB payloads, and exercise full CRUD flows backed by Oracle AI Database Free and Testcontainers. |
| [crud-duality-views](./crud-duality-views/README.md)   | Run CRUD operations against a JSON Relational Duality View using Oracle JDBC, OSON serialization, and Testcontainers-powered integration tests.                               |
| [jdbc-json-basic](./jdbc-json-basic/README.md)         | Work with the Oracle JSON data type via JDBC: bind OracleJsonObject payloads, query with SQL/JSON operators, and validate the flow with Testcontainers.                       |
| [jdbc-json-analytics](./jdbc-json-analytics/README.md) | Perform advanced SQL/JSON analytics with JSON_TABLE, JSON_EXISTS, and JSON_ARRAYAGG over nested order documents using plain JDBC.                                             |
| [json-event-streaming](./json-event-streaming/README.md) | Publish and consume Oracle JSON documents over OKafka, streaming OSON payloads through Transactional Event Queue topics with integration tests.                               |

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker Desktop or another OCI-compatible container runtime (required for Testcontainers-based integration tests)
- 
## Learn more

- [Learn more about JSON Relational Duality Views](https://andersswanson.dev/2025/07/14/7-reasons-to-try-out-json-relational-duality-views-with-samples/)
- [My JSON Blogs](https://andersswanson.dev/tag/json/)
- [JSON Relational Duality Views overview](https://docs.oracle.com/en/database/oracle/oracle-database/26/jsnvu/overview-json-relational-duality-views.html)
- [JSON Data Type in Oracle](https://docs.oracle.com/en/database/oracle/oracle-database/26/adjsn/json-data-type.html)
