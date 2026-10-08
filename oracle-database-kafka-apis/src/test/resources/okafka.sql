whenever sqlerror exit failure rollback
whenever oserror exit failure rollback

alter session set container=freepdb1;

-- This user is created by Testcontainers or the Compose startup script.
grant aq_user_role to TESTUSER;
grant execute on dbms_aq to  TESTUSER;
grant execute on dbms_aqadm to TESTUSER;
grant select on gv_$session to TESTUSER;
grant select on v_$session to TESTUSER;
grant select on gv_$instance to TESTUSER;
grant select on gv_$listener_network to TESTUSER;
grant select on SYS.DBA_RSRC_PLAN_DIRECTIVES to TESTUSER;
grant select on gv_$pdbs to TESTUSER;
grant select on user_queue_partition_assignment_table to TESTUSER;
exec dbms_aqadm.GRANT_PRIV_FOR_RM_PLAN('TESTUSER');
commit;

-- Shared metrics schema setup for Compose startup and Testcontainers.
declare
    table_count number;
begin
    select count(*) into table_count from all_tables
    where owner = 'TESTUSER' and table_name = 'OKAFKA_METRICS_READINGS';
    if table_count = 0 then
        execute immediate 'create table TESTUSER.OKAFKA_METRICS_READINGS (
            run_id varchar2(36) not null,
            reading varchar2(100) not null,
            produced_at timestamp default systimestamp not null,
            consumed_at timestamp,
            primary key (run_id, reading)
        )';
    end if;
end;
/
