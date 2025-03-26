-- Create a table to store JSON events
create table weather_events
(
    id   number(10) generated always as identity primary key,
    data json
);
