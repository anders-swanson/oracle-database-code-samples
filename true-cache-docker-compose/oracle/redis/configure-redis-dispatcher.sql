-- Copyright (c) 2026, Oracle and/or its affiliates.
-- Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/.

-- Run on True Cache to expose its read-only Redis protocol endpoint.
whenever oserror exit failure rollback
whenever sqlerror exit failure rollback
set feedback on
set linesize 180

connect / as sysdba

alter session set container=FREEPDB1;

declare
  service_active number;
begin
  select count(*)
    into service_active
    from v$active_services
   where upper(name) = 'FREEPDB1_TC';

  if service_active = 0 then
    dbms_service.start_service('FREEPDB1_TC');
  end if;
end;
/

alter session set container=CDB$ROOT;

alter system set shared_servers=1 scope=both;

declare
  dispatcher_index number;
begin
  select min(conf_indx)
    into dispatcher_index
    from v$dispatcher_config
   where upper(network) like '%PROTOCOL=TCPS%'
     and upper(network) like '%PORT=6379%';

  if dispatcher_index is null then
    select nvl(max(conf_indx), -1) + 1
      into dispatcher_index
      from v$dispatcher_config;
  end if;

  execute immediate
    'alter system set dispatchers=''(INDEX=' || dispatcher_index ||
    ')(ADDRESS=(PROTOCOL=tcps)(HOST=true-cache)(PORT=6379))' ||
    '(SERVICE=FREEPDB1)(PRE=REDIS)'' scope=both';
end;
/

select conf_indx, network, service, dispatchers
from v$dispatcher_config
where upper(network) like '%PROTOCOL=TCPS%'
  and upper(network) like '%PORT=6379%'
  and upper(trim(service)) = 'FREEPDB1';

exit success
