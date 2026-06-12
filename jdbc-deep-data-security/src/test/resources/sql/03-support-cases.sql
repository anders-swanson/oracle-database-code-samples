whenever sqlerror exit sql.sqlcode

insert into support_cases (
    case_id,
    tenant_id,
    region,
    assigned_agent,
    severity,
    status,
    subject,
    customer_email,
    ssn,
    internal_notes
) values (
    1001,
    'ACME',
    'WEST',
    'alice@example.com',
    'HIGH',
    'OPEN',
    'Checkout terminals cannot reach inventory router',
    'ops-lead@acme.example',
    '123-45-6789',
    'Customer contract includes privileged routing notes'
)
/

insert into support_cases (
    case_id,
    tenant_id,
    region,
    assigned_agent,
    severity,
    status,
    subject,
    customer_email,
    ssn,
    internal_notes
) values (
    1002,
    'ACME',
    'WEST',
    'alice@example.com',
    'MEDIUM',
    'OPEN',
    'Mobile scanner intermittently loses Wi-Fi',
    'warehouse@acme.example',
    '111-22-3333',
    'RMA discussion includes direct customer phone'
)
/

insert into support_cases (
    case_id,
    tenant_id,
    region,
    assigned_agent,
    severity,
    status,
    subject,
    customer_email,
    ssn,
    internal_notes
) values (
    1003,
    'ACME',
    'EAST',
    'bob@example.com',
    'HIGH',
    'OPEN',
    'Payment gateway rejects settlement batch',
    'payments@acme.example',
    '222-33-4444',
    'Bank token rotation steps are restricted'
)
/

insert into support_cases (
    case_id,
    tenant_id,
    region,
    assigned_agent,
    severity,
    status,
    subject,
    customer_email,
    ssn,
    internal_notes
) values (
    1004,
    'BRIGHT',
    'WEST',
    'cara@example.com',
    'CRITICAL',
    'ROUTING',
    'Priority outage escalated to service routing',
    'noc@bright.example',
    '333-44-5555',
    'Cross-tenant escalation details require service elevation'
)
/

insert into support_cases (
    case_id,
    tenant_id,
    region,
    assigned_agent,
    severity,
    status,
    subject,
    customer_email,
    ssn,
    internal_notes
) values (
    1005,
    'ACME',
    'WEST',
    'dana@example.com',
    'CRITICAL',
    'OPEN',
    'Regional order service queue blocked',
    'orders@acme.example',
    '444-55-6666',
    'SRE rollback credential reference is restricted'
)
/

commit
/
