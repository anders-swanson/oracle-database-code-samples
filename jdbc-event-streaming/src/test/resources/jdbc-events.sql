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
            queue_name         => 'testuser.event_stream',
        -- Payload can be RAW, JSON, DBMS_AQADM.JMS_TYPE, or an object type.
        -- Default is DBMS_AQADM.JMS_TYPE.
            queue_payload_type => 'JSON'
    );
    -- Start the queue
    dbms_aqadm.start_queue(
            queue_name => 'testuser.event_stream'
    );
end;
/

-- Procedure to produce a JSON event to the event_stream queue.
create or replace procedure testuser.produce_json_event (
    event in json
) as
    enqueue_options dbms_aq.enqueue_options_t;
    message_properties dbms_aq.message_properties_t;
    msg_id raw(16);
begin
    enqueue_options := dbms_aq.enqueue_options_t();
    message_properties := dbms_aq.message_properties_t();
    dbms_aq.enqueue(
            queue_name => 'testuser.event_stream',
            enqueue_options => enqueue_options,
            message_properties => message_properties,
            payload => event,
            msgid => msg_id
    );
    commit;
end;
/

-- Procedure to consume a JSON event from the event_stream queue.
create or replace function testuser.consume_json_event return json is
    dequeue_options dbms_aq.dequeue_options_t;
    message_properties dbms_aq.message_properties_t;
    msg_id raw(16);
    event json;
begin
    dequeue_options := dbms_aq.dequeue_options_t();
    message_properties := dbms_aq.message_properties_t();
    dequeue_options.navigation := dbms_aq.first_message;
    dequeue_options.wait := dbms_aq.no_wait;

    dbms_aq.dequeue(
            queue_name => 'testuser.event_stream',
            dequeue_options => dequeue_options,
            message_properties => message_properties,
            payload => event,
            msgid => msg_id
    );
    return event;
end;
/

-- Create a table to store JSON events
create table testuser.weather_events
(
    id   number(10) generated always as identity primary key,
    data json
);
/
