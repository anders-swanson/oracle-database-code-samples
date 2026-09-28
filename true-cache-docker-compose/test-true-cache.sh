#!/usr/bin/env bash
# Copyright (c) 2026, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/.

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${script_dir}"

export ORACLE_PWD="${ORACLE_PWD:-Welcome12345}"

log() {
  printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*" >&2
}

log "Checking Compose services and their current health."
docker compose ps >&2

log "Adding two coffee lots to inventory on the primary."
primary_output=$(docker compose exec -T oracle-free bash -s <<'PRIMARY'
set -euo pipefail

sqlplus -L -s /nolog <<SQL
whenever oserror exit failure rollback
whenever sqlerror exit failure rollback
set echo off
set feedback off
set heading off
set linesize 150
set pagesize 0
set trimout on
set serveroutput on
connect true_cache_demo/"${ORACLE_PWD}"@//localhost:1521/FREEPDB1

variable ethiopian_lot_id number
variable colombian_lot_id number

insert into coffee_inventory (coffee_name, bags_in_stock)
values ('Ethiopian Yirgacheffe', 25)
returning lot_id into :ethiopian_lot_id;

insert into coffee_inventory (coffee_name, bags_in_stock)
values ('Colombian Huila', 18)
returning lot_id into :colombian_lot_id;

commit;

select 'Primary lot ' || lot_id || ': ' || coffee_name || ' - ' || bags_in_stock || ' bags'
from coffee_inventory
where lot_id in (:ethiopian_lot_id, :colombian_lot_id)
order by lot_id;

begin
  dbms_output.put_line('LOT_IDS=' || :ethiopian_lot_id || ' ' || :colombian_lot_id);
end;
/

exit success
SQL
PRIMARY
) || {
  printf '%s\n' "${primary_output}" >&2
  exit 1
}
printf '%s\n' "${primary_output}" >&2
lot_ids=$(printf '%s\n' "${primary_output}" | sed -n 's/^LOT_IDS=\([0-9][0-9]* [0-9][0-9]*\)$/\1/p')
if [[ ! "${lot_ids}" =~ ^[0-9]+\ [0-9]+$ ]]; then
  log "Could not read the two numeric lot ids from the primary database output."
  exit 1
fi
read -r ethiopian_lot_id colombian_lot_id <<<"${lot_ids}"
log "Coffee lot ids: ${ethiopian_lot_id} and ${colombian_lot_id}."

await_cache_rows() {
  local phase="$1" expected_bags="$2" attempt=1 max_attempts=30
  while (( attempt <= max_attempts )); do
    log "Reading ${phase} inventory from True Cache (attempt ${attempt}/${max_attempts})."
    if docker compose exec -T \
      -e "ETHIOPIAN_LOT_ID=${ethiopian_lot_id}" \
      -e "COLOMBIAN_LOT_ID=${colombian_lot_id}" \
      -e "EXPECTED_BAGS=${expected_bags}" \
      true-cache bash -s <<'CACHE' >&2
set -euo pipefail

sqlplus -L -s /nolog <<SQL
whenever oserror exit failure rollback
whenever sqlerror exit failure rollback
set echo off
set feedback off
set heading off
set linesize 150
set pagesize 0
set trimout on
set serveroutput on
connect true_cache_demo/"${ORACLE_PWD}"@//localhost:1521/FREEPDB1_TC

declare
  visible_count number;
begin
  select count(*) into visible_count
  from coffee_inventory
  where (lot_id = ${ETHIOPIAN_LOT_ID} and coffee_name = 'Ethiopian Yirgacheffe'
         and bags_in_stock = ${EXPECTED_BAGS})
     or (lot_id = ${COLOMBIAN_LOT_ID} and coffee_name = 'Colombian Huila'
         and bags_in_stock = 18);

  dbms_output.put_line('Expected inventory lots visible on True Cache: ' || visible_count);
  if visible_count != 2 then
    raise_application_error(-20001, 'Waiting for inventory changes on True Cache.');
  end if;
end;
/

select 'True Cache lot ' || lot_id || ': ' || coffee_name || ' - ' || bags_in_stock || ' bags'
from coffee_inventory
where lot_id in (${ETHIOPIAN_LOT_ID}, ${COLOMBIAN_LOT_ID})
order by lot_id;

exit success
SQL
CACHE
    then
      log "Success: ${phase} inventory is visible through True Cache."
      return 0
    fi

    if (( attempt == max_attempts )); then
      log "Timed out waiting for ${phase} inventory on True Cache."
      log "Review the True Cache startup and true-cache-configure logs with: docker compose logs true-cache true-cache-configure"
      return 1
    fi

    log "True Cache has not applied the ${phase} inventory yet; retrying in 2 seconds."
    sleep 2
    ((attempt += 1))
  done
}

await_cache_rows "new" 25

log "Selling three bags of Ethiopian Yirgacheffe on the primary."
docker compose exec -T -e "ETHIOPIAN_LOT_ID=${ethiopian_lot_id}" oracle-free bash -s <<'UPDATE' >&2
set -euo pipefail

sqlplus -L -s /nolog <<SQL
whenever oserror exit failure rollback
whenever sqlerror exit failure rollback
set echo off
set feedback off
set heading off
set linesize 150
set pagesize 0
set trimout on
connect true_cache_demo/"${ORACLE_PWD}"@//localhost:1521/FREEPDB1

begin
  update coffee_inventory
     set bags_in_stock = bags_in_stock - 3
   where lot_id = ${ETHIOPIAN_LOT_ID} and bags_in_stock = 25;
  if sql%rowcount != 1 then
    raise_application_error(-20002, 'Expected to update one coffee lot.');
  end if;
  commit;
end;
/

select 'Primary lot ' || lot_id || ': ' || coffee_name || ' - ' || bags_in_stock || ' bags'
from coffee_inventory
where lot_id = ${ETHIOPIAN_LOT_ID};

exit success
SQL
UPDATE

await_cache_rows "updated" 22
log "Coffee lots ${ethiopian_lot_id} and ${colombian_lot_id} remain in TRUE_CACHE_DEMO.COFFEE_INVENTORY."

printf '%s %s\n' "${ethiopian_lot_id}" "${colombian_lot_id}"
