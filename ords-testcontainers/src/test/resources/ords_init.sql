WHENEVER SQLERROR EXIT SQL.SQLCODE;

-- Run all test setup inside the pluggable database that ORDS connects to.
ALTER SESSION SET CONTAINER = freepdb1;

-- ORDS Database API user. This user owns the schema exposed through the
-- ORDS admin-style database APIs exercised by the integration test.
CREATE USER ordsuser IDENTIFIED BY ordsuserpwd QUOTA UNLIMITED ON users;
GRANT connect, pdb_dba TO ordsuser;

-- Oracle MongoDB API user. This user has the minimum privileges needed for
-- SODA-backed MongoDB API CRUD operations through ORDS.
CREATE USER mongouser IDENTIFIED BY mongouserpwd QUOTA UNLIMITED ON users;
GRANT create session, create table, soda_app TO mongouser;

EXIT;
