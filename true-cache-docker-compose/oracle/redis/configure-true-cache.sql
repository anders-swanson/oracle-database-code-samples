-- Copyright (c) 2026, Oracle and/or its affiliates.
-- Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/.

-- Run on the writable primary after True Cache service registration.
-- Argument 1 is the base64-encoded ORACLE_PWD passed by the Compose helper.
whenever oserror exit failure rollback
whenever sqlerror exit failure rollback
set echo off
set verify off
set feedback on

connect / as sysdba
alter session set container=FREEPDB1;

-- A fresh Compose database needs one user and a small coffee inventory table.
declare
  demo_password varchar2(1024) := utl_raw.cast_to_varchar2(
    utl_encode.base64_decode(utl_raw.cast_to_raw('&1')));
begin
  execute immediate 'create user true_cache_demo identified by "' ||
    replace(demo_password, '"', '""') || '"';
end;
/

grant create session, unlimited tablespace to true_cache_demo;

create table true_cache_demo.coffee_inventory (
  lot_id number generated always as identity primary key,
  coffee_name varchar2(100) not null,
  bags_in_stock number(6) not null check (bags_in_stock >= 0),
  roasted_on date default trunc(sysdate) not null
);

exit success
