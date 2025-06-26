-- Set as appropriate for your database.
alter session set container = freepdb1;

-- Configure testuser with the necessary privileges to use Transactional Event Queues.
grant aq_user_role to testuser;
grant execute on dbms_aq to testuser;
grant execute on dbms_aqadm to testuser;
grant execute ON dbms_aqin TO testuser;
grant execute ON dbms_aqjms TO testuser;

create table testuser.tickets (
    id      number(10) generated always as identity primary key,
    title   varchar2(100),
    status  varchar2(50),
    created timestamp default systimestamp
);

-- Create a Transactional Event Queue
begin
    dbms_aqadm.create_transactional_event_queue(
            queue_name         => 'testuser.ticket_event',
            queue_payload_type => DBMS_AQADM.JMS_TYPE
    );

    -- Start the queue
    dbms_aqadm.start_queue(
            queue_name         => 'testuser.ticket_event'
    );
end;
/


create or replace trigger testuser.tickets_insert_trigger
    after insert on testuser.tickets
    for each row
declare
    enqueue_options    dbms_aq.enqueue_options_t;
    message_properties dbms_aq.message_properties_t;
    message_handle     raw(16);
    jms_msg            sys.aq$_jms_text_message;
    ticket_json        varchar2(400);
begin
    ticket_json := json_object(
            'id'   value :new.id,
            'title' value :new.title,
            'status' value :new.status,
            'created' value :new.created
            returning varchar2(400)
                   );

    -- Create a JMS message for the event
    jms_msg := sys.aq$_jms_text_message.construct;
    jms_msg.set_text(ticket_json);

    -- Send the message to the ticket_event queue
    dbms_aq.enqueue(
            queue_name          => 'testuser.ticket_event',
            enqueue_options     => enqueue_options,
            message_properties  => message_properties,
            payload             => jms_msg,
            msgid               => message_handle
    );
    -- commit isn't required in a trigger, due to the enclosing txn
end;
/

