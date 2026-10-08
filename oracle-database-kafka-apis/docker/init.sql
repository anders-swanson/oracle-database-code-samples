whenever sqlerror exit failure rollback
whenever oserror exit failure rollback

-- The official Oracle AI Database Free image runs this script as SYSDBA on every start.
alter session set container=freepdb1;
declare
    tablespace_count number;
    user_count number;
begin
    -- Create the sample tablespace if it is missing.
    select count(*) into tablespace_count from dba_tablespaces where tablespace_name = 'USERS';
    if tablespace_count = 0 then
        execute immediate 'create tablespace USERS
            datafile ''/opt/oracle/oradata/FREE/FREEPDB1/users01.dbf''
            size 100M autoextend on next 10M maxsize 1G';
    end if;

    select count(*) into user_count from dba_users where username = 'TESTUSER';
    if user_count = 0 then
        execute immediate 'create user TESTUSER identified by "Welcome123#"
            default tablespace USERS temporary tablespace TEMP quota unlimited on USERS';
    end if;
end;
/
grant connect, resource to TESTUSER;

@/opt/oracle/scripts/okafka.sql
exit
