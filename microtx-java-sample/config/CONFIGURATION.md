# MicroTx Configuration

The Compose environment mounts the included `tcs-config.yaml` and `workflow-server-config.properties` files into the MicroTx containers.

The coordinator uses Oracle AI Database as its transaction store:

```yaml
tmmConfiguration:
  storage:
    type: db
    db:
      connectionString: microtxdb:1521/FREEPDB1
```

The Compose environment provides the coordinator database credentials through `STORAGE_DB_CREDENTIAL`.

The workflow server connects to the same database service:

```properties
spring.datasource.url=jdbc:oracle:thin:@microtxdb:1521/FREEPDB1
spring.datasource.username=microtx
spring.datasource.password=Welcome12345
```

Compose environment variables override the database credentials in both files when `ORACLE_APP_USER` or `ORACLE_APP_PASSWORD` is set. A wallet is not required for this local Oracle AI Database Free service.
