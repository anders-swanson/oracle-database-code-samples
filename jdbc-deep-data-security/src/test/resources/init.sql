whenever sqlerror exit sql.sqlcode

alter session set container = freepdb1;

create user hr no authentication
    default tablespace users
    quota unlimited on users;

create table hr.employees (
    employee_id number primary key,
    first_name varchar2(50) not null,
    last_name varchar2(50) not null,
    email varchar2(128) not null,
    manager varchar2(128),
    org_id number not null,
    ssn varchar2(20),
    salary number(10, 2),
    phone varchar2(20)
);

insert into hr.employees values (100, 'Victoria', 'Williams', 'vwilliams', null, 10, '219-09-9999', 13000, '555-0100');
insert into hr.employees values (200, 'Marvin', 'Anderson', 'manderson', 'vwilliams', 10, '457-55-5462', 12030, '555-0200');
insert into hr.employees values (300, 'Chris', 'Evans', 'cevans', 'vwilliams', 10, '321-12-4567', 6900, '555-0300');
insert into hr.employees values (400, 'Emma', 'Baker', 'ebaker', 'manderson', 10, '733-02-9821', 8200, '555-0400');
insert into hr.employees values (500, 'Taylor', 'Mills', 'tmills', 'manderson', 10, '558-76-1243', 9000, '555-0500');
insert into hr.employees values (600, 'Noor', 'Patel', 'npatel', 'manderson', 20, '239-77-4012', 8800, '555-0600');
commit;

-- local end users have no schema or database objects
-- and must be granted access to data
create end user "manderson" identified by testpwd;
create end user "ebaker" identified by testpwd;

create data role employee_role;
create data role manager_role;

-- create session is required to connect
create role deepsec_session_role;
grant create session to deepsec_session_role;
grant deepsec_session_role to employee_role;
grant deepsec_session_role to manager_role;

grant data role employee_role to "manderson";
grant data role manager_role to "manderson";
grant data role employee_role to "ebaker";

-- # DATA GRANTS

-- the data grant allows access to an employee's own record
create data grant hr.employees_own_record
    as select, update (phone)
    on hr.employees
    -- row level RBAC
    where email = ORA_END_USER_CONTEXT.username
      and org_id = ORA_END_USER_CONTEXT.hr.hcm_context.org_id
    to employee_role;

-- a manager is allowed access to their direct reports
create data grant hr.manager_direct_reports
    as select (all columns except ssn) -- column level RBAC
    on hr.employees
    where manager = ORA_END_USER_CONTEXT.username
      and org_id = ORA_END_USER_CONTEXT.hr.hcm_context.org_id
    to manager_role;

-- require data grants for hr.employees
set use data grants only on hr.employees enabled;

-- # SESSION CONTEXT

-- custom deep data security end-user context using a JSON schema
-- essentially, for each session, there is a HR-owned context object with these attributes
-- org_id: if not initialized by the session, set to 10
-- scope: if not initialized by the session, set to WORKFORCE
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

-- allow end users to read the instantiated context attributes
create data grant hr.hcm_context_read
    as select
       on sys.end_user_context
       where owner = 'HR' and name = 'HCM_CONTEXT'
           to employee_role, manager_role;

-- managers can change the active organization for this session
create data grant hr.hcm_context_manager_update
    as update
           on sys.end_user_context
       where owner = 'HR' and name = 'HCM_CONTEXT'
           to manager_role;
