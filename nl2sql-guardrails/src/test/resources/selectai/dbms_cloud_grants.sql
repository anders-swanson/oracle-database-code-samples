whenever sqlerror exit failure rollback;

-- Set as appropriate for your database. "freepdb1" is the default PDB in Oracle AI Database Free
alter session set container = freepdb1;

-- add grants for DMBS_CLOUD family packages
create user selectai identified by Welcome12345 quota unlimited on users;
grant connect, resource to selectai;
grant execute on dbms_cloud to selectai;
grant execute on dbms_cloud_ai to selectai;
