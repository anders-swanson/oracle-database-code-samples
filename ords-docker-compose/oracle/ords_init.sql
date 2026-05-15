-- Set as appropriate for your database.
alter session set container = freepdb1;

@?/rdbms/admin/utlrp.sql

create user testuser identified by testpwd quota unlimited on users;
grant connect, resource to testuser;
