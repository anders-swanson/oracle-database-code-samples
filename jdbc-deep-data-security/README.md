---
name: jdbc-deep-data-security
description: Plain JDBC sample showing support-case access guardrails with Oracle AI Database Deep Data Security and a local compatibility path.
tags:
  - Java
  - JDBC
  - security
  - Testcontainers
blog_post: ""
---

# JDBC Deep Data Security

This module teaches how a Java application can keep authorization policy close to the data in Oracle AI Database.

The sample models a support desk that stores cases for multiple tenants and regions. The Java app connects as one database user, applies an end-user context for Alice, Bob, manager Maria, and a routing service, then runs the same SQL under each actor.

The default workflow is a local compatibility harness. It runs in Oracle AI Database Free with Testcontainers and proves the access behavior that an Oracle AI Database Deep Data Security deployment should enforce. The module also includes the JDBC `EndUserSecurityContext` code path and a Deep Data Security SQL handoff script for Oracle AI Database 26ai environments.

## Why this sample is useful

Oracle AI Database Deep Data Security is easiest to learn when the access decisions are visible:

- The application changes only the end-user context.
- The SQL stays the same.
- The database-side policy decides which rows, columns, and writes are allowed.
- The test verifies both allowed and denied paths.

That makes this module useful as a teaching sample even without a full identity-provider setup. The compatibility harness gives new users a deterministic loop; the Deep Data Security files show where the same policy shape moves in a real deployment.

## How Deep Data Security thinks

The mental model is:

1. A trusted application opens a database connection.
2. Before executing SQL, the application attaches an end-user security context with the current user name, active data roles, and runtime attributes such as tenant and region.
3. Oracle AI Database evaluates data grants against that context.
4. The same `SELECT` or `UPDATE` can see different rows, columns, or cells depending on the current end user.
5. Temporary privilege elevation is scoped to the smallest operation and cleared before the connection is reused.

Compatibility mode mirrors that contract with ordinary SQL objects so the module can run locally. It is a behavioral harness, not a replacement for Oracle AI Database Deep Data Security.

## Mode matrix

| Mode      | Purpose                        | What runs locally                                                                                        | What it proves                                                                                                                                        |
|-----------|--------------------------------|----------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `compat`  | Default deterministic workflow | Recreates tables, installs `support_security`, loads CSV data, runs the sample, and validates the report | The intended row filtering, masking, guarded writes, elevation, and sample audit evidence                                                             |
| `auto`    | Probe and explain              | Checks for `ORA_END_USER_CONTEXT`; falls back only when the feature is unavailable                       | Local databases without Deep Data Security still get the compatibility proof; unexpected probe errors fail closed                                     |
| `deepsec` | Deployment handoff check       | Fails fast unless Deep Data Security objects are present, then stops with handoff guidance               | The module found a Deep Data Security-capable environment, but identity, token, TLS, and policy administration setup are still external prerequisites |

## What the sample proves

- Alice sees only cases assigned to `alice@example.com`, with sensitive values masked.
- Bob sees only the ACME East case assigned to `bob@example.com`.
- Alice can update one assigned case, but Alice and Bob cannot update unauthorized cases.
- Maria sees ACME West regional cases and can update one regional case.
- The routing service sees no cases until a tightly scoped elevation is applied.
- Every read and write path records actor, role, mode, operation, case ID, row count, and elevation state in an audit table.

| Actor           | Context                                 | Visible cases          | Sensitive fields                              | Write behavior                                                        |
|-----------------|-----------------------------------------|------------------------|-----------------------------------------------|-----------------------------------------------------------------------|
| Alice           | `AGENT`, tenant `ACME`, region `WEST`   | `1001`, `1002`         | Masked email, SSN suffix only, redacted notes | Can update assigned case `1001`; cannot update unassigned case `1005` |
| Bob             | `AGENT`, tenant `ACME`, region `EAST`   | `1003`                 | Masked email, SSN suffix only, redacted notes | Cannot update Alice's case `1001`                                     |
| Maria           | `MANAGER`, tenant `ACME`, region `WEST` | `1001`, `1002`, `1005` | Unmasked regional case details                | Can update regional case `1005`                                       |
| Routing service | `SERVICE`, no elevation                 | none                   | none                                          | No case access                                                        |
| Routing service | `SERVICE`, elevated                     | `1004`                 | Unmasked routed case details                  | Elevation is visible in the sample audit trail                        |

![Support case access guardrails](./support-case-access-guardrails.svg)

The diagram shows the stable application contract: one JDBC user, changing end-user context, and policy decisions made close to the data. In compatibility mode, the package and secured view are teaching scaffolding. In a Deep Data Security deployment, data roles and data grants become the policy source.

The implementation keeps two policy paths explicit:

![Deep Data Security policy paths](./deepsec-policy-paths.svg)

The second diagram is the most important boundary in the sample: compatibility mode proves behavior locally; the Deep Data Security path shows the deployment handoff.

## Feature map

| Feature | Where to look | What it demonstrates |
| --- | --- | --- |
| End-user security context | [OracleEndUserContextApplier.java](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/jdbc-deep-data-security/src/main/java/com/example/security/OracleEndUserContextApplier.java) | The JDBC shape for `OracleConnection.setEndUserSecurityContext(...)` and `clearEndUserSecurityContext()`. |
| Data roles and data grants | [deepsec-security.sql](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/jdbc-deep-data-security/src/main/resources/sql/deepsec-security.sql) | Deep Data Security policy concepts for agents, managers, and scoped service elevation. |
| Local compatibility policy | [compat-security.sql](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/jdbc-deep-data-security/src/main/resources/sql/compat-security.sql) | A runnable policy layer with session context, `DBMS_SESSION`, a secured view, row filtering, and masking. |
| Guarded JDBC access | [SupportCaseRepository.java](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/jdbc-deep-data-security/src/main/java/com/example/security/SupportCaseRepository.java) | One SELECT and one UPDATE path whose results change only because the actor context changes. |
| Deterministic proof | [DeepDataSecurityTest.java](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/jdbc-deep-data-security/src/test/java/com/example/security/DeepDataSecurityTest.java) | Testcontainers validation for filtered rows, masked values, denied writes, elevation, and audit attribution. |

## Prerequisites

- Java 21
- Maven
- Docker for the Testcontainers workflow
- Enough memory to start the `gvenzl/oracle-free:23.26.2-full-faststart` container image

The app path expects a database user that can create tables, views, and packages in its schema. If the user cannot create an application context, the local harness still runs; it keeps context in package state and skips the optional `DBMS_SESSION.SET_CONTEXT` mirror.

## Run the test

From the repository root:

```bash
mvn -pl jdbc-deep-data-security test
```

The test starts Oracle AI Database Free with Testcontainers, recreates the support-case schema, loads deterministic data, installs the compatibility policy layer, and verifies the full access workflow.

## Expected output

The test is the primary proof. The sample app also prints a report shaped like this:

```text
Security mode: COMPAT
CREATE CONTEXT available: false

Alice assigned cases:
  1001 | ACME | WEST | OPEN | ssn=***-**-6789 | assigned agent access
  1002 | ACME | WEST | OPEN | ssn=***-**-3333 | assigned agent access

Service before elevation:
  no rows visible

Service during elevation:
  1004 | BRIGHT | WEST | ROUTING | ssn=333-44-5555 | service elevation for routed critical case

Audit events captured: 10
```

## Run the sample app

Against an Oracle AI Database instance:

```bash
mvn compile exec:java -pl jdbc-deep-data-security -Dexec.args="jdbc:oracle:thin:@localhost:1521/freepdb1 testuser testpwd"
```

The default mode is `compat`, which is the local runnable path.

You can also pass an explicit mode:

```bash
mvn compile exec:java -pl jdbc-deep-data-security -Dexec.args="jdbc:oracle:thin:@localhost:1521/freepdb1 testuser testpwd --mode=auto"
```

`--mode=auto` probes for Deep Data Security and falls back to compatibility mode when the local database does not expose the required Deep Data Security objects. `--mode=deepsec` is a fail-fast probe for a Deep Data Security-enabled Oracle AI Database 26ai environment; this module keeps the fully automated workflow in compatibility mode so the sample remains locally testable.

## Deep Data Security handoff

The Deep Data Security-specific pieces are intentionally separate from the local compatibility proof:

- [OracleEndUserContextApplier.java](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/jdbc-deep-data-security/src/main/java/com/example/security/OracleEndUserContextApplier.java) shows how application code unwraps `OracleConnection`, creates an `EndUserSecurityContext`, sends end-user name, data roles, and attributes, then clears the context before the connection returns to a pool.
- [deepsec-security.sql](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/jdbc-deep-data-security/src/main/resources/sql/deepsec-security.sql) shows data roles and data grants for agents, managers, and scoped service elevation.

That separation keeps the sample teachable: local tests prove the authorization behavior without cloud identity setup, while the Deep Data Security files show the database-native policy objects to use in a deployment.

Before running the handoff script in a real environment, you still need:

- Oracle AI Database 26ai with Deep Data Security enabled
- a database schema that owns `support_cases`; the script uses `support_app` as an example owner that you must replace
- an end-user context definition for the attributes the Java code sends
- an application identity mapped to the IAM client ID
- IAM and token configuration for end-user and database-access tokens
- TLS/wallet configuration when required by the environment
- policy administration privileges such as creating data roles, end-user contexts, data grants, and mandatory data privilege settings

The handoff script uses locally managed data roles because the plain JDBC sample demonstrates `EndUserSecurityContext.withDataRoles(...)`. In a production application, derive those roles from verified identity claims or use externally mapped data roles, rather than accepting role names from request parameters.

## Compatibility-to-Deep-Data-Security mapping

| Local compatibility piece                | Deep Data Security deployment piece              | Why it exists in the sample                                                                  |
|------------------------------------------|--------------------------------------------------|----------------------------------------------------------------------------------------------|
| `CompatibilityContextApplier`            | `OracleEndUserContextApplier`                    | Shows that the application boundary is "apply actor context, run SQL, clear context"         |
| `support_security` package state         | end-user security context                        | Makes Alice, Bob, Maria, and the service actor visible to policy code                        |
| `support_case_access_v`                  | data grants on `support_cases`                   | Proves row filtering and sensitive-value behavior with a normal local database               |
| `can_update_case(...)` guarded predicate | `UPDATE (status)` data grants                    | Keeps write authorization close to the data instead of scattering checks through Java        |
| sample audit table                       | production audit/FGA/unified audit configuration | Gives deterministic teaching evidence, but is not a tamper-resistant production audit design |

## Important limits

Compatibility mode intentionally runs in one schema so it remains simple for Testcontainers. That means it is not a database security boundary: code with direct access to `support_cases` can bypass the secured view and guarded repository method. A production deployment should separate owner and runtime schemas, grant only the intended views/procedures, or rely on Oracle AI Database Deep Data Security mandatory data privileges.

The compatibility harness masks unauthorized sensitive values with readable placeholders. Deep Data Security column restrictions return unauthorized cells as `NULL` unless you add a projection layer that converts authorization checks into explicit display masks.
