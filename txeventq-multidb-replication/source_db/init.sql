-- Set as appropriate for your database.
alter session set container = freepdb1;
-- queue-to-queue replication uses background processes from the job queue
alter system set job_queue_processes=10;

create user sourceuser identified by testpwd;
grant create session to sourceuser;
grant unlimited tablespace to sourceuser;
grant connect, resource to sourceuser;

-- Configure sourceuser with the necessary privileges to use Transactional Event Queues for JMS.
grant create database link to sourceuser;
grant aq_administrator_role to sourceuser;
grant execute on dbms_aq to sourceuser;
grant execute on dbms_aqadm to sourceuser;
grant execute on dbms_aqin to sourceuser;
grant execute on dbms_aqjms to sourceuser;

begin
    -- create and start the source queue
    dbms_aqadm.create_transactional_event_queue(
        queue_name => 'sourceuser.source',
        multiple_consumers => true
    );
    dbms_aqadm.start_queue(
        queue_name => 'sourceuser.source'
    );
end;
/

-- create a link to database "destdb"
create public database link destdb
    connect to destuser identified by testpwd
    using '(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=destdb)(PORT=1521)) (CONNECT_DATA=(SERVICE_NAME=freepdb1)))';

begin
    -- schedule propagation from the sourceuser.source topic to the destuser.dest@destdb topic over a database link
    dbms_aqadm.schedule_propagation(
            queue_name => 'sourceuser.source',
            destination => 'destdb',
            destination_queue => 'destuser.dest',
            start_time => sysdate, -- immediate start
            duration => null, -- propagate until stopped
            latency  => 0 -- no latency between propagation
    );
end;
/
