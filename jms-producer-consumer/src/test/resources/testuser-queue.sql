-- When using Oracle Database Free as a local container, this value may be too low for subscriber jobs.
-- It is not usually necessary to change this value outside of Oracle Database Free container instances.
alter system set job_queue_processes=10;
-- Set as appropriate for your database. "freepdb1" is the default PDB in Oracle Database Free
alter session set container = freepdb1;

-- Configure testuser with the necessary privileges to use Transactional Event Queues.
grant execute on dbms_aq to testuser;
grant execute on dbms_aqadm to testuser;
grant execute on dbms_aqin to testuser;
grant execute on dbms_aqjms to testuser;

-- Create a Transactional Event Queue
begin
    -- See https://docs.oracle.com/en/database/oracle/oracle-database/23/arpls/DBMS_AQADM.html#GUID-93B0FF90-5045-4437-A9C4-B7541BEBE573
    -- For documentation on creating Transactional Event Queues.
    dbms_aqadm.create_transactional_event_queue(
            queue_name         => 'testuser.mytopic',
        -- Payload can be RAW, JSON, DBMS_AQADM.JMS_TYPE, or an object type.
        -- Default is DBMS_AQADM.JMS_TYPE.
            queue_payload_type => DBMS_AQADM.JMS_TYPE,
            multiple_consumers => false
    );

    -- Start the queue
    dbms_aqadm.start_queue(
            queue_name         => 'testuser.mytopic'
    );
end;
/
