# CRUD with JSON Relational Duality Views

This sample performs create, read, update, and delete (CRUD) operations against a JSON Relational Duality View (JDV) using the Oracle JDBC driver and Oracle's binary JSON (OSON) format. A lightweight console application serializes Java POJOs into OSON, writes them to a JDV, and demonstrates how to query, mutate, and delete the backed JSON document while keeping the relational tables synchronized.

## What you will learn
- Serialize Java records into Oracle OSON using a reusable `OSONMapper`.
- Insert JSON payloads into a JDV and retrieve generated keys via JDBC `RETURNING`.
- Read JDV rows with `JsonParser`, map them back to typed objects, and apply `json_transform` updates.
- Delete documents through the JDV and cascade relational changes automatically.

## Prerequisites
- Java 21+
- Maven 3.9+
- Access to an Oracle Database Free instance (local container or remote)

## Provision schema for the JDV

The test resources include `src/test/resources/init.sql`, which creates the `products` and `orders` tables as well as the `orders_dv` duality view. Execute the script in your database user before running the application:

```sql
@src/test/resources/init.sql
```

## Run the console app

From the repository root, package the module and run the application with your JDBC connection details:

```bash
mvn compile exec:java -Dexec.args="jdbc:oracle:thin:@localhost:1521/freepdb1 testuser testpwd"
```

The program will:
- Insert a new `Order` document into `orders_dv` and capture the generated `_id`.
- Query the JDV to deserialize the JSON document back into the `Order` class.
- Update the order quantity with `json_transform` and persist the change.
- Delete the same order and confirm the record is removed.

## Run the tests

To run the Testcontainers-backed integration test:

```bash
docker pull gvenzl/oracle-free:23.26.1-slim-faststart
mvn test
```

`JDVCrudTest` launches Oracle Database Free inside a container, executes `init.sql`, and invokes `Application.main(...)`. The test performs the same CRUD workflow end-to-end—insert, read, update, and delete—against the `orders_dv` duality view to verify the sample is working.

## Related resources
- [JSON Relational Duality Views overview](https://docs.oracle.com/en/database/oracle/oracle-database/26/jsnvu/overview-json-relational-duality-views.html)
