whenever sqlerror exit failure rollback
whenever oserror exit failure rollback

-- The prebuilt official image runs startup SQL as SYSDBA on every start.
alter session set container=freepdb1;
declare
    user_count number;
begin
    select count(*) into user_count from dba_users where username = 'TESTUSER';
    if user_count = 0 then
        execute immediate 'create user TESTUSER identified by "Welcome123#"
            default tablespace USERS temporary tablespace TEMP quota unlimited on USERS';
    end if;
end;
/
grant connect, resource to TESTUSER;

-- Grant OKafka access as SYS.
@/lab/okafka.sql

-- Install the same schema used by the Testcontainers suite.
connect TESTUSER/"Welcome123#"@//localhost:1521/FREEPDB1
whenever sqlerror exit failure rollback
@/lab/db/schema.sql
exit
