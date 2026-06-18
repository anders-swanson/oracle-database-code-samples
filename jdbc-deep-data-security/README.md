---
name: jdbc-deep-data-security
description: Plain JDBC sample that validates Oracle AI Database Deep Data Security data grants with Testcontainers.
tags:
  - Java
  - JDBC
  - Security
  - Testcontainers
blog_post: ""
---

# JDBC Deep Data Security

This locally runnable Deep Data Security example for Oracle AI Database. It follows the local end-user quick start from the Deep Data Security guide, driving the validation from a JUnit test.

The flow is simple:

1. Testcontainers starts Oracle AI Database Free.
2. A [setup script](./src/test/resources/init.sql) creates an `hr.employees` table, a custom `hr.hcm_context`, two local Deep Sec end users, two data roles, data grants, and mandatory data-grant enforcement on the table.
3. The test connects with JDBC as `"manderson"` and `"ebaker"`.
4. Both users run the same `select * from hr.employees` query.
5. JUnit asserts that data grants filter rows, return unauthorized SSN cells as `NULL`, expose the expected phone-update authorization through `ORA_CHECK_DATA_PRIVILEGE`, and use end-user context attributes for the active organization scope.

![Deep Data Security JDBC flow](deep-data-security-flow.svg)

## How Policy Enforcement Works

Deep Data Security is enforced by Oracle AI Database, not by application-side filtering. The test doesn't need to add policy-level filters like `where email = ?` or `where manager = ?` - it connects as a Deep Data Security end user and lets data grant policies determine the visible rows and cells.

| End user      | Data roles                      | Expected access                                                                                                                   |
|---------------|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `"manderson"` | `employee_role`, `manager_role` | Marvin sees his own row with SSN, plus Emma and Taylor as direct reports in the default organization with SSN returned as `NULL`. |
| `"ebaker"`    | `employee_role`                 | Emma sees only her own row in the default organization, including her own SSN.                                                    |

The custom end-user context defines session attributes that can participate in data grant predicates:

```sql
create end user context hr.hcm_context using json schema '{
    "type": "object",
    "properties": {
        "org_id": {
            "type": "integer",
            "default": 10
        },
        "scope": {
            "type": "string",
            "default": "WORKFORCE"
        }
    }
}';
```

The employee grant lets employees read their own row and grants update authorization only for their own phone number. This is an example of both row and column level RBAC driven by database policies:

```sql
create data grant hr.employees_own_record
    as select, update (phone)
    on hr.employees
    where email = ORA_END_USER_CONTEXT.username
      and org_id = ORA_END_USER_CONTEXT.hr.hcm_context.org_id
    to employee_role;
```

The test validates with `ORA_CHECK_DATA_PRIVILEGE`: Marvin's own phone cell is updateable, but Emma and Taylor's phone cells are not updateable through the manager row visibility grant. It also updates Marvin's `hr.hcm_context.org_id` through `SYS.END_USER_CONTEXT` for the current JDBC session and verifies that the same table query moves to his direct report in organization `20`.

The setup also runs `SET USE DATA GRANTS ONLY ON hr.employees ENABLED` so Deep Data Security users _must_ use data grants for the protected table.

## Code Map

- [pom.xml](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/jdbc-deep-data-security/pom.xml) declares the plain JDBC and Testcontainers dependencies.
- [init.sql](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/jdbc-deep-data-security/src/test/resources/init.sql) creates the HR table, local end users, custom end-user context, data roles, and data grants.
- [DeepDataSecurityTest.java](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/jdbc-deep-data-security/src/test/java/com/example/security/DeepDataSecurityTest.java) runs the JDBC proof against Oracle AI Database Free.
- [deep-data-security-flow.svg](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/jdbc-deep-data-security/deep-data-security-flow.svg) diagrams the end-to-end test flow.

## Run The Test

From the repository root:

```bash
mvn -pl jdbc-deep-data-security test
```

The test uses an Oracle AI Database Free container image. A Docker-compatible environment must be running before Maven starts the test.

## Why Local End Users

Deep Data Security also supports application-mediated security contexts with IAM tokens, application identities, and driver-supplied `EndUserSecurityContext` payloads. That path is closer to a production web application, but it needs IAM, token, and TLS setup.

Local Deep Sec end users are the simplest for development and testing, while still exercising Deep Data Security objects: local end users, data roles, `ORA_END_USER_CONTEXT.username`, custom `ORA_END_USER_CONTEXT.hr.hcm_context` attributes, and data grants.

## IAM Token Access With EndUserSecurityContext

In an IAM-backed application, the app typically connects with an application or pool identity, then attaches the real end-user identity to the JDBC connection before running SQL. Oracle AI Database validates the tokens, activates the requested Deep Data Security data roles, and exposes the attached attributes through `ORA_END_USER_CONTEXT`.

```java
import oracle.jdbc.EndUserSecurityContext;
import oracle.jdbc.OracleConnection;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonObject;

import java.sql.Connection;

class IamDeepDataSecurityExample {
    void runAsEndUser(Connection pooledConnection, String databaseAccessToken, String endUserToken)
            throws Exception {
        OracleJsonObject hcmContext = new OracleJsonFactory().createObject();
        hcmContext.put("org_id", 10);
        hcmContext.put("scope", "WORKFORCE");

        EndUserSecurityContext securityContext = EndUserSecurityContext
                .createWithToken(databaseAccessToken, endUserToken)
                .withDataRoles("employee_role", "manager_role")
                .withAttributes("HR.HCM_CONTEXT", hcmContext);

        OracleConnection oracleConnection = pooledConnection.unwrap(OracleConnection.class);
        oracleConnection.setEndUserSecurityContext(securityContext);

        try {
            // Run normal application SQL here. Data grants still evaluate
            // ORA_END_USER_CONTEXT.username and ORA_END_USER_CONTEXT.hr.hcm_context.org_id.
        } finally {
            oracleConnection.clearEndUserSecurityContext();
        }
    }
}
```

This sample does not run that path locally because a real IAM integration needs token issuance, trust configuration, and TLS setup. The runnable Testcontainers test uses local end users so the data grants can be validated without external identity infrastructure. For the full IAM setup flow, see [Configure Oracle Deep Data Security for Direct Logon with End Users Using IAM](https://docs.oracle.com/en/database/oracle/oracle-database/26/ddscg/configure-oracle-deep-data-security-direct-logon-end-users-iam.html#).

## References

- [What Is Oracle Deep Data Security](https://docs.oracle.com/en/database/oracle/oracle-database/26/ddscg/what-is-oracle-deep-data-security.html)
- [Configure Oracle Deep Data Security for Direct Logon with Local End Users](https://docs.oracle.com/en/database/oracle/oracle-database/26/ddscg/configure-oracle-deep-data-security-direct-logon-local-end-users.html)
- [Configure Oracle Deep Data Security for Direct Logon with End Users Using IAM](https://docs.oracle.com/en/database/oracle/oracle-database/26/ddscg/configure-oracle-deep-data-security-direct-logon-end-users-iam.html#)
- [Data Access Control Configuration](https://docs.oracle.com/en/database/oracle/oracle-database/26/ddscg/data-access-control-configuration.html)
- [Configure End-User Contexts and Attributes](https://docs.oracle.com/en/database/oracle/oracle-database/26/ddscg/configure-end-user-contexts-and-attributes.html)
- [Read End-User Context Attributes](https://docs.oracle.com/en/database/oracle/oracle-database/26/ddscg/read-end-user-context-attributes.html)
- [Modify Custom End-User Context Attributes](https://docs.oracle.com/en/database/oracle/oracle-database/26/ddscg/modify-custom-end-user-context-attributes.html)
- [Validate Data Access Control](https://docs.oracle.com/en/database/oracle/oracle-database/26/ddscg/validate-data-access-control-l.html)
- [About Data Grants](https://docs.oracle.com/en/database/oracle/oracle-database/26/ddscg/data-grants.html)
