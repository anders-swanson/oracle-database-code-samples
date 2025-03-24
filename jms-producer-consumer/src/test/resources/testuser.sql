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
        -- FALSE means queues can only have one consumer for each message. This is the default.
        -- TRUE means queues created in the table can have multiple consumers for each message.
            multiple_consumers => true
    );

    -- 6 queue shards for consumer parallelization
    dbms_aqadm.set_queue_parameter('testuser.mytopic', 'shard_num', 6);
    -- must be set to make sure that order is guaranteed at the jms consumer.
    dbms_aqadm.set_queue_parameter('testuser.mytopic', 'sticky_dequeue', 1);
    -- must be set to make sure that order of the events per correlation in place
    DBMS_AQADM.set_queue_parameter('testuser.mytopic', 'KEY_BASED_ENQUEUE', 1);

    -- Start the queue
    dbms_aqadm.start_queue(
            queue_name         => 'testuser.mytopic'
    );
end;
/

begin
    dbms_aqadm.add_subscriber(
            queue_name => 'testuser.mytopic',
            subscriber => sys.aq$_agent('example_subscriber', null, null)
    );
end;
/
