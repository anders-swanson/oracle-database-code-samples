-- Set as appropriate for your database.
alter session set container = freepdb1;

-- Configure testuser with the necessary privileges to use Transactional Event Queues.
grant aq_user_role to testuser;
grant execute on dbms_aq to testuser;
grant execute on dbms_aqadm to testuser;
grant execute ON dbms_aqin TO testuser;
grant execute ON dbms_aqjms TO testuser;
grant execute on dbms_teqk to testuser;

-- Create a Transactional Event Queue
begin
    -- See https://docs.oracle.com/en/database/oracle/oracle-database/23/arpls/DBMS_AQADM.html#GUID-93B0FF90-5045-4437-A9C4-B7541BEBE573
    -- For documentation on creating Transactional Event Queues.
    dbms_aqadm.create_transactional_event_queue(
            queue_name         => 'testuser.testqueue',
            -- Payload can be RAW, JSON, DBMS_AQADM.JMS_TYPE, or an object type.
            -- Default is DBMS_AQADM.JMS_TYPE.
            queue_payload_type => DBMS_AQADM.JMS_TYPE,
            -- FALSE means queues can only have one consumer for each message. This is the default.
            -- TRUE means queues created in the table can have multiple consumers for each message.
            multiple_consumers => false
    );

    -- Start the queue
    dbms_aqadm.start_queue(
            queue_name         => 'testuser.testqueue'
    );
end;
/