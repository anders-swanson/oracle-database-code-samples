#!/usr/bin/env bash
# Copyright (c) 2026, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/.

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${script_dir}"

log() {
  printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"
}

if ! command -v redis-cli >/dev/null 2>&1; then
  log "redis-cli is required for this test. Install a TLS-enabled redis-cli and retry."
  exit 1
fi

export ORACLE_PWD="${ORACLE_PWD:-Welcome12345}"

log "Checking coffee inventory replication through True Cache SQL."
lot_ids="$("${script_dir}/test-true-cache.sh")"
read -r ethiopian_lot_id colombian_lot_id <<<"${lot_ids}"

redis_cli() {
  REDISCLI_AUTH="${ORACLE_PWD}" redis-cli --tls --insecure --raw \
    -h 127.0.0.1 -p 6379 --user TRUE_CACHE_DEMO "$@"
}

redis_key_1="true_cache_demo.coffee_inventory:lot_id=${ethiopian_lot_id}"
redis_key_2="true_cache_demo.coffee_inventory:lot_id=${colombian_lot_id}"

log "Using redis-cli TLS/AUTH as TRUE_CACHE_DEMO to read both stock counts."
log "redis-cli HGET ${redis_key_1} bags_in_stock"
bags_1="$(redis_cli HGET "${redis_key_1}" bags_in_stock)"
log "redis-cli HGET ${redis_key_2} bags_in_stock"
bags_2="$(redis_cli HGET "${redis_key_2}" bags_in_stock)"

printf '%-24s %-8s %-14s\n' 'COFFEE' 'LOT ID' 'BAGS IN STOCK'
printf '%-24s %-8s %-14s\n' 'Ethiopian Yirgacheffe' "${ethiopian_lot_id}" "${bags_1}"
printf '%-24s %-8s %-14s\n' 'Colombian Huila' "${colombian_lot_id}" "${bags_2}"

if [[ "${bags_1}" != "22" || "${bags_2}" != "18" ]]; then
  log "Redis API returned unexpected stock counts for the coffee lots."
  exit 1
fi

log "Success: redis-cli authenticated and read both coffee lots through True Cache."
