-- Set as appropriate for your database.
alter session set container = freepdb1;

create user testuser identified by testpwd quota unlimited on users;
grant connect, resource to testuser;

-- In your config server schema, a table named PROPERTIES,
-- with at least application, profile, label, key, and value columns must be present.
-- These columns should be varchar2 of any length.
create table testuser.PROPERTIES (
    id          number generated always as identity primary key,
    application varchar2(255),
    profile     varchar2(255),
    label       varchar2(255),
    prop_key    varchar2(255),
    value       varchar2(255)
);
