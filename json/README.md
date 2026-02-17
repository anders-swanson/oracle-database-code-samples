# JSON in Oracle AI Database

Samples in this directory demonstrate how Oracle AI Database treats JSON as a first-class data model, including JSON Relational Duality Views and binary JSON payload handling from Java applications.

## Modules

| Sample | Description |
| --- | --- |
| [jpa-duality-views](./jpa-duality-views/README.md) | Generate JSON Relational Duality Views from Spring Data JPA entities, persist JSONB payloads, and exercise full CRUD flows backed by Oracle Database Free and Testcontainers. |

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker Desktop or another OCI-compatible container runtime (required for Testcontainers-based integration tests)
- 
## Learn more

- [My JSON Blogs](https://andersswanson.dev/tag/json/)
- [JSON Relational Duality Views overview](https://docs.oracle.com/en/database/oracle/oracle-database/26/jsnvu/overview-json-relational-duality-views.html)
- [JSON Data Type in Oracle](https://docs.oracle.com/en/database/oracle/oracle-database/26/adjsn/json-data-type.html)