# `OracleFree` Testcontainer

Use [`OracleFree`](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/testcontainers/src/main/java/com/example/containers/OracleFree.java) to run the official Oracle AI Database Free image in a Java integration test.

## Quick start

Put your SQL setup in `src/test/resources`. Then pass the classpath resource to `withInitScript`:

```java
static OracleFree oracle = new OracleFree()
        .withInitScript("students.sql");

@BeforeAll
static void startDatabase() {
    oracle.start();
}
```

That is enough to start Oracle AI Database Free, create an application user, and run `students.sql` in the application user's schema.

### Defaults

| Setting | Default |
| --- | --- |
| Image | `container-registry.oracle.com/database/free:latest-lite` |
| Service | `FREEPDB1` |
| Application user | `TEST` |
| Application password | `TestPassword1` |
| Listener port | `1521` |

Use the container's connection details when configuring a data source:

```java
dataSource.setURL(oracle.getJdbcUrl());
dataSource.setUser(oracle.getUsername());
dataSource.setPassword(oracle.getPassword());
```

See [`OracleFreeTest`](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/testcontainers/src/test/java/com/example/containers/OracleFreeTest.java) for a complete test that initializes a table and verifies an inserted row.

## Use your own application user

Set the username and password before adding the initialization script:

```java
static OracleFree oracle = new OracleFree()
        .withUsername("MY_APP")
        .withPassword("MyAppPassword1")
        .withInitScript("students.sql");
```

`OracleFree` creates `MY_APP` in `FREEPDB1`, grants `DB_DEVELOPER_ROLE`, and gives it space for application objects. Usernames are converted to uppercase and must be valid unquoted Oracle AI Database identifiers.

## How initialization scripts work

`withInitScript` and `withInitScripts` use the values returned by:

- `getJdbcUrl()`
- `getUsername()`
- `getPassword()`

With the default configuration, scripts connect to `FREEPDB1` as `TEST`. With the custom configuration above, they connect as `MY_APP`. Tables, views, and other unqualified objects therefore belong to that application user's schema.

Scripts must be on the test classpath:

```text
src/test/resources/students.sql
```

Write the scripts as JDBC-compatible SQL. Testcontainers executes statements through JDBC, not through SQL*Plus or SQLcl, so SQL*Plus commands are not supported.

## Connect as an administrator

The official image provides `SYS`, `SYSTEM`, and `PDBADMIN`. It does not provide an `APP_USER` environment variable, which is why `OracleFree` creates the application user itself.

Connect to `FREEPDB1` as `SYSTEM` or `PDBADMIN` when a test needs administrative access:

```java
static OracleFree oracle = new OracleFree()
        .withUsername("SYSTEM")
        .withPassword("AdminPassword1");
```

In this configuration, initialization scripts also run as the selected administrator.

To keep separate application and administrative passwords:

```java
static OracleFree oracle = new OracleFree()
        .withUsername("MY_APP")
        .withPassword("MyAppPassword1")
        .withAdminPassword("AdminPassword1");
```

Call `usingSid()` only when a test needs to connect to the `FREE` container database as `SYSTEM` instead of to the `FREEPDB1` service. `SYS` connections are not supported because they require `SYSDBA` privileges.

## Configuration options

| Method | Purpose |
| --- | --- |
| `withUsername(...)` | Select or create the JDBC user |
| `withPassword(...)` | Set the selected user's password |
| `withAdminPassword(...)` | Set the image's administrative password separately |
| `withCharacterSet(...)` | Set `ORACLE_CHARACTERSET` |
| `withArchiveLog(...)` | Set `ENABLE_ARCHIVELOG` |
| `withForceLogging(...)` | Set `ENABLE_FORCE_LOGGING` |
| `usingSid()` | Connect to `FREE` as `SYSTEM` |

The default `latest-lite` image is smaller and starts quickly. Use `latest` or a specific compatible tag when a test needs the full image:

```java
static OracleFree oracle = new OracleFree(
        "container-registry.oracle.com/database/free:latest");
```

## Run the smoke test

From the repository root:

```shell
mvn -pl testcontainers -Dtest=OracleFreeTest test
```

The smoke test starts the container, runs `students.sql` as the application user, and queries an inserted row.
