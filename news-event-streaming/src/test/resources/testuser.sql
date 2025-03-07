alter session set container = freepdb1;

-- Create app user. You may wish to modify tablespace as needed.
create user if not exists testuser identified by testpwd;
grant create session to testuser;
grant connect, resource, unlimited tablespace to testuser;

-- Grants for the Kafka Java Client for Oracle Database Transactional Event Queues
grant aq_user_role to testuser;
grant execute on dbms_aq to  testuser;
grant execute on dbms_aqadm to testuser;
grant select on gv_$session to testuser;
grant select on v_$session to testuser;
grant select on gv_$instance to testuser;
grant select on gv_$listener_network to testuser;
grant select on sys.dba_rsrc_plan_directives to testuser;
grant select on gv_$pdbs to testuser;
grant select on user_queue_partition_assignment_table to testuser;
exec dbms_aqadm.grant_priv_for_rm_plan('testuser');
commit;

