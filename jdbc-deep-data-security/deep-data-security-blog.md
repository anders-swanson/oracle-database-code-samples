# A Small JDBC Proof For Oracle AI Database Deep Data Security

The simplest useful security sample is one where the application does less, not more.

This module starts Oracle AI Database Free with Testcontainers, creates two local Deep Sec end users, and runs the same JDBC query as each user. The interesting part is what the Java code does not do: it does not add user-specific predicates, mask SSNs in memory, or special-case managers.

The policy lives in data grants.

## The Runnable Proof

Run it from the repository root:

```bash
mvn -pl jdbc-deep-data-security test
```

The setup script creates the HR table, local end users, data roles, and data grants in [deep-data-security-demo.sql](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/jdbc-deep-data-security/src/test/resources/sql/deep-data-security-demo.sql). The test then connects over JDBC as `"manderson"` and `"ebaker"` in [DeepDataSecurityTest.java](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/jdbc-deep-data-security/src/test/java/com/example/security/DeepDataSecurityTest.java).

Oracle's guide describes Deep Data Security as database-enforced authorization for row, column, and cell access. The local end-user quick start is the smallest development path because it avoids IAM, TLS, and token setup while still using Deep Sec end users, data roles, and data grants.

![Deep Data Security JDBC flow](deep-data-security-flow.svg)

## The Policy Shape

The employee grant is direct:

```sql
create data grant hr.employees_own_record
    as select, update (phone)
    on hr.employees
    where email = ORA_END_USER_CONTEXT.username
    to employee_role;
```

Marvin and Emma both get `employee_role`, so each can see their own row.

The manager grant is different:

```sql
create data grant hr.manager_direct_reports
    as select (all columns except ssn)
    on hr.employees
    where manager = ORA_END_USER_CONTEXT.username
    to manager_role;
```

Marvin also gets `manager_role`. He can see Emma and Taylor as direct reports, but their SSN cells come back as `NULL`. The Java test asserts that behavior from the result set, which is a better proof than a README table.

## Why This Is Better Than Java Filtering

The JDBC query is intentionally plain:

```sql
select employee_id, first_name, last_name, email, manager, ssn, salary, phone
from hr.employees
order by employee_id
```

The same SQL returns different data because Oracle AI Database evaluates the current Deep Sec end user context. That matters for applications, reporting tools, and agentic systems where generated SQL can bypass application-side guardrails.

The same employee grant also carries a tiny write rule. The test uses `ORA_CHECK_DATA_PRIVILEGE` to prove Marvin's own phone cell is updateable and Emma's is not, even though Marvin can see Emma's row through the manager grant.

The setup also enables mandatory data-grant enforcement with `SET USE DATA GRANTS ONLY ON hr.employees ENABLED`, which keeps the protected table on the Deep Sec policy path for Deep Sec users.

## The Boundary

This is not an IAM-token application sample. Oracle AI Database Deep Data Security supports application-mediated security contexts, but that path needs identity-provider configuration, application identities, token propagation, and often TLS or wallet setup.

For a repo sample, the local end-user flow is the right first proof: it is small enough to run in Testcontainers and still shows real Deep Sec enforcement at the data layer.

Start with this test. Then move the same policy shape into an application-mediated setup when you need pooled connections and IAM-backed end-user identity propagation.
