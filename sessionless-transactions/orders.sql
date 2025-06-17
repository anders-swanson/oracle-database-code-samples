create table order_processing (
    id        number(10) generated always as identity primary key,
    status    varchar2(20) check (status in ('created', 'inventory_reserved', 'completed', 'failed')),
    gtrid     raw(16),
    timestamp timestamp default systimestamp
);