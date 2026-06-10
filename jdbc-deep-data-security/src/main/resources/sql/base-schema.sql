begin
    execute immediate 'drop view support_case_access_v';
exception
    when others then
        if sqlcode != -942 then
            raise;
        end if;
end;
/

begin
    execute immediate 'drop package support_security';
exception
    when others then
        if sqlcode != -4043 then
            raise;
        end if;
end;
/

begin
    execute immediate 'drop table support_case_audit purge';
exception
    when others then
        if sqlcode != -942 then
            raise;
        end if;
end;
/

begin
    execute immediate 'drop table support_cases purge';
exception
    when others then
        if sqlcode != -942 then
            raise;
        end if;
end;
/

create table support_cases (
    case_id number primary key,
    tenant_id varchar2(30) not null,
    region varchar2(30) not null,
    assigned_agent varchar2(128) not null,
    severity varchar2(20) not null,
    status varchar2(30) not null,
    subject varchar2(200) not null,
    customer_email varchar2(128) not null,
    ssn varchar2(11) not null,
    internal_notes varchar2(500) not null
)
/

create table support_case_audit (
    audit_id number generated always as identity primary key,
    actor_name varchar2(128) not null,
    actor_role varchar2(30) not null,
    security_mode varchar2(30) not null,
    operation varchar2(30) not null,
    case_id number,
    rows_affected number not null,
    elevated varchar2(5) not null,
    created_at timestamp default systimestamp not null
)
/
