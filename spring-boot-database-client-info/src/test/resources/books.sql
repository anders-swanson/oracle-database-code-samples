create table books (
    id               number generated always as identity primary key,
    title            varchar2(255) not null,
    author           varchar2(255) not null,
    isbn             varchar2(20),
    published_date   date
);
