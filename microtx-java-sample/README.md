---
name: microtx-java-sample
description: Spring Boot service scaffold for Oracle Transaction Manager for Microservices.
tags:
  - Java
  - MicroTX
  - SpringBoot
---

# MicroTx Java Sample

This module is the starting point for a basic Java service that will use Oracle Transaction Manager for Microservices. The current scaffold provides a runnable Spring Boot HTTP service; MicroTx transaction behavior will be added in a later step.

## Important Files

- [MicroTxApplication](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/microtx-java-sample/src/main/java/com/example/microtx/MicroTxApplication.java): Spring Boot entry point.
- [ServiceController](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/microtx-java-sample/src/main/java/com/example/microtx/ServiceController.java): Baseline HTTP endpoint used to verify that the service is running.
- [MicroTxApplicationTest](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/microtx-java-sample/src/test/java/com/example/microtx/MicroTxApplicationTest.java): Starts the service and verifies its HTTP response.
- [microtx-docker-compose.yaml](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/microtx-java-sample/microtx-docker-compose.yaml): Runs the MicroTx coordinator, workflow server, console, and Oracle AI Database Free on one network.

## Run MicroTx and Oracle AI Database Free

The Compose environment pulls the MicroTx Free images from Oracle Container Registry. Sign in and accept the MicroTx repository terms before starting it:

```bash
docker login container-registry.oracle.com
```

The included `tcs-config.yaml` and `workflow-server-config.properties` files connect MicroTx to Oracle AI Database Free over the shared Compose network. The [configuration notes](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/microtx-java-sample/config/CONFIGURATION.md) describe the database and credential settings.

Start the environment:

```bash
docker compose -f microtx-docker-compose.yaml up -d
docker compose -f microtx-docker-compose.yaml ps
```

The `workflow-key-init` service generates the workflow-server encryption key the first time the environment starts. The key is retained in the `workflow-encryption-key` volume and reused across restarts.

The services are available at:

- MicroTx coordinator health: `http://localhost:9000/health`
- MicroTx workflow server health: `http://localhost:9010/workflow-server/health`
- MicroTx console: `http://localhost:8080/consoleui/`
- Oracle AI Database Free: `localhost:1521/FREEPDB1`

Stop the environment without deleting its database and workflow storage volumes:

```bash
docker compose -f microtx-docker-compose.yaml down
```

Add `--volumes` only when you intentionally want to delete the persisted development data and workflow-server encryption key. Deleting the key prevents the workflow server from decrypting secrets that were encrypted with it.

## Run the Service

From the repository root:

```bash
mvn -pl microtx-java-sample spring-boot:run
```

Then access the console UI:

```bash
http://localhost:8080/consoleui
```

You should see the microtx dashboard:

![console UI](./consoleui.png)

## Test

```bash
mvn -pl microtx-java-sample test
```
