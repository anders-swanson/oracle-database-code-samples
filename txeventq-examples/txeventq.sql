-- Delete a Transactional Event Queue.
begin
    dbms_aqadm.stop_queue(
            queue_name => 'sql_test'
    );
    dbms_aqadm.drop_transactional_event_queue(
            queue_name => 'sql_test'
    );
end;
/

-- Create a Transactional Event Queue
begin
    -- See https://docs.oracle.com/en/database/oracle/oracle-database/23/arpls/DBMS_AQADM.html#GUID-93B0FF90-5045-4437-A9C4-B7541BEBE573
    -- For documentation on creating Transactional Event Queues.
    dbms_aqadm.create_transactional_event_queue(
            queue_name         => 'sql_test',
        -- Payload can be RAW, JSON, DBMS_AQADM.JMS_TYPE, or an object type.
        -- Default is DBMS_AQADM.JMS_TYPE.
            queue_payload_type => 'JSON'
    );
    -- Start the queue
    dbms_aqadm.start_queue(
            queue_name => 'sql_test'
    );
end;
/

-- Procedure to produce a JSON event to the event_stream queue.
create or replace procedure produce_json_event (
    event in json
) as
    enqueue_options dbms_aq.enqueue_options_t;
    message_properties dbms_aq.message_properties_t;
    msg_id raw(16);
begin
    enqueue_options := dbms_aq.enqueue_options_t();
    message_properties := dbms_aq.message_properties_t();
    dbms_aq.enqueue(
            queue_name => 'sql_test',
            enqueue_options => enqueue_options,
            message_properties => message_properties,
            payload => event,
            msgid => msg_id
    );
    commit;
end;
/

-- Procedure to consume a JSON event from the event_stream queue.
create or replace function consume_json_event return json is
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
            queue_name => 'sql_test',
            dequeue_options => dequeue_options,
            message_properties => message_properties,
            payload => event,
            msgid => msg_id
    );
    return event;
end;
/

-- Produce a JSON message.
begin
    produce_json_event(json('{"content": "my first message"}'));
end;
/

-- Consume a JSON message and send the 'content' field to server output.
declare
    message json;
    message_buffer varchar2(500);
begin
    message := consume_json_event();
    select json_value(message, '$.content') into message_buffer;
    dbms_output.put_line('message: ' || message_buffer);
end;
/
