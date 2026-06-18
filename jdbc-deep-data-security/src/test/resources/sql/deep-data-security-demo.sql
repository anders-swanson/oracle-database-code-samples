whenever sqlerror exit sql.sqlcode

alter session set container = freepdb1;

begin
    execute immediate 'drop end user "manderson"';
exception
    when others then null;
end;
/

begin
    execute immediate 'drop end user "ebaker"';
exception
    when others then null;
end;
/

begin
    execute immediate 'drop user hr cascade';
exception
    when others then null;
end;
/

begin
    execute immediate 'drop data role employee_role';
exception
    when others then null;
end;
/

begin
    execute immediate 'drop data role manager_role';
exception
    when others then null;
end;
/

begin
    execute immediate 'drop role deepsec_session_role';
exception
    when others then null;
end;
/

create user hr no authentication
    default tablespace users
    quota unlimited on users;

create table hr.employees (
    employee_id number primary key,
    first_name varchar2(50) not null,
    last_name varchar2(50) not null,
    email varchar2(128) not null,
    manager varchar2(128),
    ssn varchar2(20),
    salary number(10, 2),
    phone varchar2(20)
);

insert into hr.employees values (100, 'Victoria', 'Williams', 'vwilliams', null, '219-09-9999', 13000, '555-0100');
insert into hr.employees values (200, 'Marvin', 'Anderson', 'manderson', 'vwilliams', '457-55-5462', 12030, '555-0200');
insert into hr.employees values (300, 'Chris', 'Evans', 'cevans', 'vwilliams', '321-12-4567', 6900, '555-0300');
insert into hr.employees values (400, 'Emma', 'Baker', 'ebaker', 'manderson', '733-02-9821', 8200, '555-0400');
insert into hr.employees values (500, 'Taylor', 'Mills', 'tmills', 'manderson', '558-76-1243', 9000, '555-0500');
commit;

create end user "manderson" identified by testpwd;
create end user "ebaker" identified by testpwd;

create data role employee_role;
create data role manager_role;

create role deepsec_session_role;
grant create session to deepsec_session_role;
grant deepsec_session_role to employee_role;
grant deepsec_session_role to manager_role;

grant data role employee_role to "manderson";
grant data role manager_role to "manderson";
grant data role employee_role to "ebaker";

create data grant hr.employees_own_record
    as select, update (phone)
    on hr.employees
    where email = ORA_END_USER_CONTEXT.username
    to employee_role;

create data grant hr.manager_direct_reports
    as select (all columns except ssn)
    on hr.employees
    where manager = ORA_END_USER_CONTEXT.username
    to manager_role;

set use data grants only on hr.employees enabled;
