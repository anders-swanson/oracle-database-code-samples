-- Set as appropriate for your database.
alter session set container = freepdb1;

create user testuser identified by testpwd;
grant create session to testuser;
grant unlimited tablespace to testuser;
grant connect, resource to testuser;


create table testuser.books (
    id               number generated always as identity primary key,
    title            varchar2(255) not null,
    author           varchar2(255) not null,
    isbn             varchar2(20),
    published_date   date
);
