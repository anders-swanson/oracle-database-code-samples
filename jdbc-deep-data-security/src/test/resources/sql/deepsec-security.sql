whenever sqlerror exit sql.sqlcode

-- Deep Data Security policy handoff for Oracle AI Database 26ai environments.
--
-- The default Testcontainers workflow uses sql/02-compat-security.sql so it can
-- run locally without IAM, TLS wallets, SYS grants, or cloud identity setup.
-- This file shows the database-native objects that express the same policy
-- shape in a Deep Data Security-enabled environment.
--
-- Replace support_app with the schema that owns SUPPORT_CASES before using
-- this as a deployment script.

-- Custom context attributes must be declared before data grants reference
-- them. OracleEndUserContextApplier sends these values in the "support_case"
-- context payload.
create or replace end user context support_app.support_case using json schema '{
    "type": "object",
    "properties": {
        "tenant_id": {
            "type": "string",
            "default": ""
        },
        "region": {
            "type": "string",
            "default": ""
        },
        "role": {
            "type": "string",
            "default": ""
        },
        "elevated": {
            "type": "string",
            "default": "false"
        }
    }
}'
/

-- This sample uses locally managed data roles because the Java handoff shows
-- withDataRoles(...). In production, derive the Java role selection from
-- verified IAM claims or use externally mapped roles instead of trusting request
-- parameters.
create data role support_agent_role
/

create data role support_manager_role
/

-- Keep elevation disabled by default. The application enables this role only
-- inside a narrow operation scope.
create data role support_service_role disabled
/

-- Agents can read only their own tenant cases, and the sensitive columns are
-- intentionally excluded from the data grant. Deep Data Security returns NULL
-- for unauthorized columns; add a projection layer if the UI needs explicit
-- mask strings like the local compatibility harness uses.
create or replace data grant agent_assigned_cases
    as select (all columns except customer_email, ssn, internal_notes),
    update (status)
    on support_app.support_cases
    where tenant_id = ora_end_user_context.support_app.support_case.tenant_id
      and assigned_agent = ora_end_user_context.username
    to support_agent_role
/

-- Managers can read regional cases for their tenant and update case status.
-- The example assumes the application passes TENANT_ID and REGION as end-user
-- security attributes.
create or replace data grant manager_regional_cases
    as select,
    update (status)
    on support_app.support_cases
    where tenant_id = ora_end_user_context.support_app.support_case.tenant_id
      and region = ora_end_user_context.support_app.support_case.region
    to support_manager_role
/

-- The service role demonstrates controlled privilege elevation. Application
-- code should include this role only inside the smallest possible try/finally
-- scope when building the end-user security context.
create or replace data grant service_routing_cases
    as select
    on support_app.support_cases
    where status = 'ROUTING'
    to support_service_role
/

-- Application identity setup depends on the IAM provider. The exact mapped-to
-- value is environment-specific. Grant locally managed data roles to the
-- application identity before Java code can enable them with withDataRoles(...).
-- The shape is:
--
-- create or replace application identity support_case_app
--     mapped to 'AZURE_CLIENT_ID=<iam-client-id>'
-- /
--
-- grant data role support_agent_role to support_case_app
-- /
--
-- grant data role support_manager_role to support_case_app
-- /
--
-- grant data role support_service_role to support_case_app
-- /
--
-- If you prefer IAM-mapped roles, create the agent and manager data roles with
-- MAPPED TO 'AZURE_ROLE=...' or MAPPED TO 'IAM_OAUTH_GROUP=...' instead, and
-- remove them from the application-enabled role set.

-- Mandatory access control: data grants become the authorization source for
-- protected application access instead of broad object privileges.
set use data grants only on support_app.support_cases enabled
/

-- Inspection examples for troubleshooting policy decisions.
select ora_end_user_context.username as end_user_name
from dual
/

select ora_end_user_context.support_app.support_case as support_case_context
from dual
/

select case_id,
       ora_check_data_privilege(sc, 'SELECT') as can_view,
       ora_check_data_privilege(sc, 'SELECT', ssn) as can_read_ssn
from support_app.support_cases sc
order by case_id
/
