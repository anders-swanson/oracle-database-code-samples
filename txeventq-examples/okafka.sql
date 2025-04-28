-- create a basic table to store messages in the order we produce them.
create table if not exists okafka_messages (
    id      number(10) generated always as identity primary key,
    message varchar2(1000)
);
