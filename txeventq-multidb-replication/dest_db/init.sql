-- Set as appropriate for your database.
alter session set container = freepdb1;
-- queue-to-queue replication uses background processes from the job queue
alter system set job_queue_processes=10;

create user destuser identified by testpwd quota unlimited on users;
grant connect, resource to destuser;

-- Configure destuser with the necessary privileges to use Transactional Event Queues for JMS.
grant aq_administrator_role to destuser;
grant execute on dbms_aq to destuser;
grant execute on dbms_aqadm to destuser;
grant execute on dbms_aqin to destuser;
grant execute on dbms_aqjms to destuser;


begin
    -- create and start the source queue
    dbms_aqadm.create_transactional_event_queue(
        queue_name => 'destuser.dest',
        multiple_consumers => true
    );
    dbms_aqadm.start_queue(
        queue_name => 'destuser.dest'
    );
end;
/