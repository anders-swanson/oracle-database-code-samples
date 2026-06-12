whenever sqlerror exit sql.sqlcode

-- Compatibility policy path for local Oracle AI Database Free tests.
--
-- The package keeps policy context in session package state so the sample can
-- run with an ordinary application user. If the user has CREATE ANY CONTEXT,
-- the Java installer also creates SUPPORT_SECURITY_CTX; then SET_ACTOR mirrors
-- the same values into DBMS_SESSION.SET_CONTEXT for inspection with SYS_CONTEXT.

create or replace package support_security authid definer as
    procedure set_actor(
        p_actor_name in varchar2,
        p_tenant_id in varchar2,
        p_region in varchar2,
        p_actor_role in varchar2,
        p_elevated in varchar2
    );

    procedure clear_actor;

    function actor_name return varchar2;
    function actor_role return varchar2;
    function elevated return varchar2;

    function can_view_case(
        p_tenant_id in varchar2,
        p_region in varchar2,
        p_assigned_agent in varchar2,
        p_status in varchar2
    ) return number;

    function can_view_sensitive(
        p_tenant_id in varchar2,
        p_region in varchar2,
        p_assigned_agent in varchar2
    ) return number;

    function can_update_case(
        p_tenant_id in varchar2,
        p_region in varchar2,
        p_assigned_agent in varchar2
    ) return number;

    function policy_reason(
        p_tenant_id in varchar2,
        p_region in varchar2,
        p_assigned_agent in varchar2,
        p_status in varchar2
    ) return varchar2;
end support_security;
/

create or replace package body support_security as
    g_actor_name varchar2(128);
    g_tenant_id varchar2(30);
    g_region varchar2(30);
    g_actor_role varchar2(30);
    g_elevated varchar2(5);

    procedure mirror_context(p_name in varchar2, p_value in varchar2) is
    begin
        dbms_session.set_context('SUPPORT_SECURITY_CTX', p_name, p_value);
    exception
        when others then
            null;
    end;

    procedure clear_mirror_context(p_name in varchar2) is
    begin
        dbms_session.clear_context('SUPPORT_SECURITY_CTX', null, p_name);
    exception
        when others then
            null;
    end;

    procedure set_actor(
        p_actor_name in varchar2,
        p_tenant_id in varchar2,
        p_region in varchar2,
        p_actor_role in varchar2,
        p_elevated in varchar2
    ) is
    begin
        g_actor_name := p_actor_name;
        g_tenant_id := p_tenant_id;
        g_region := p_region;
        g_actor_role := p_actor_role;
        g_elevated := p_elevated;

        dbms_session.set_identifier(p_actor_name);
        mirror_context('ACTOR_NAME', p_actor_name);
        mirror_context('TENANT_ID', p_tenant_id);
        mirror_context('REGION', p_region);
        mirror_context('ACTOR_ROLE', p_actor_role);
        mirror_context('ELEVATED', p_elevated);
    end;

    procedure clear_actor is
    begin
        g_actor_name := null;
        g_tenant_id := null;
        g_region := null;
        g_actor_role := null;
        g_elevated := 'false';
        dbms_session.clear_identifier;
        clear_mirror_context('ACTOR_NAME');
        clear_mirror_context('TENANT_ID');
        clear_mirror_context('REGION');
        clear_mirror_context('ACTOR_ROLE');
        clear_mirror_context('ELEVATED');
    end;

    function actor_name return varchar2 is
    begin
        return g_actor_name;
    end;

    function actor_role return varchar2 is
    begin
        return g_actor_role;
    end;

    function elevated return varchar2 is
    begin
        return nvl(g_elevated, 'false');
    end;

    function can_view_case(
        p_tenant_id in varchar2,
        p_region in varchar2,
        p_assigned_agent in varchar2,
        p_status in varchar2
    ) return number is
    begin
        if g_actor_role = 'SERVICE' and g_elevated = 'true' and p_status = 'ROUTING' then
            return 1;
        elsif g_actor_role = 'MANAGER' and p_tenant_id = g_tenant_id and p_region = g_region then
            return 1;
        elsif g_actor_role = 'AGENT' and p_tenant_id = g_tenant_id and p_assigned_agent = g_actor_name then
            return 1;
        end if;

        return 0;
    end;

    function can_view_sensitive(
        p_tenant_id in varchar2,
        p_region in varchar2,
        p_assigned_agent in varchar2
    ) return number is
    begin
        if g_actor_role = 'MANAGER' and p_tenant_id = g_tenant_id and p_region = g_region then
            return 1;
        elsif g_actor_role = 'SERVICE' and g_elevated = 'true' then
            return 1;
        end if;

        return 0;
    end;

    function can_update_case(
        p_tenant_id in varchar2,
        p_region in varchar2,
        p_assigned_agent in varchar2
    ) return number is
    begin
        if g_actor_role = 'MANAGER' and p_tenant_id = g_tenant_id and p_region = g_region then
            return 1;
        elsif g_actor_role = 'AGENT' and p_tenant_id = g_tenant_id and p_assigned_agent = g_actor_name then
            return 1;
        end if;

        return 0;
    end;

    function policy_reason(
        p_tenant_id in varchar2,
        p_region in varchar2,
        p_assigned_agent in varchar2,
        p_status in varchar2
    ) return varchar2 is
    begin
        if g_actor_role = 'SERVICE' and g_elevated = 'true' and p_status = 'ROUTING' then
            return 'service elevation for routed critical case';
        elsif g_actor_role = 'MANAGER' and p_tenant_id = g_tenant_id and p_region = g_region then
            return 'manager regional access';
        elsif g_actor_role = 'AGENT' and p_tenant_id = g_tenant_id and p_assigned_agent = g_actor_name then
            return 'assigned agent access';
        end if;

        return 'not authorized';
    end;
end support_security;
/

create or replace context support_security_ctx using support_security
/

create or replace view support_case_access_v as
select case_id,
       tenant_id,
       region,
       assigned_agent,
       severity,
       status,
       subject,
       case
           when support_security.can_view_sensitive(tenant_id, region, assigned_agent) = 1
               then customer_email
           else 'masked-' || substr(customer_email, instr(customer_email, '@'))
       end as customer_email,
       case
           when support_security.can_view_sensitive(tenant_id, region, assigned_agent) = 1
               then ssn
           else '***-**-' || substr(ssn, -4)
       end as ssn,
       case
           when support_security.can_view_sensitive(tenant_id, region, assigned_agent) = 1
               then internal_notes
           else '[redacted by policy]'
       end as internal_notes,
       support_security.policy_reason(tenant_id, region, assigned_agent, status) as policy_reason
from support_cases
where support_security.can_view_case(tenant_id, region, assigned_agent, status) = 1
/
