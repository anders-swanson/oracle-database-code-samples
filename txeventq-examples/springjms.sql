-- Delete a Transactional Event Queue
begin
    dbms_aqadm.stop_queue(
            queue_name => 'jms_test'
    );
    dbms_aqadm.drop_transactional_event_queue(
            queue_name => 'jms_test'
    );
end;
/

-- Create a Transactional Event Queue
begin
    dbms_aqadm.create_transactional_event_queue(
            queue_name         => 'jms_test',
            queue_payload_type => DBMS_AQADM.JMS_TYPE
    );

    -- Start the queue
    dbms_aqadm.start_queue(
            queue_name         => 'jms_test'
    );
end;
/
