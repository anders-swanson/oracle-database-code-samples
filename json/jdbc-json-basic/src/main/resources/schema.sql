create table if not exists json_products (
    id number generated always as identity primary key,
    attributes json
);
