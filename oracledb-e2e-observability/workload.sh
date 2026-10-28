#!/bin/sh
set -eu
# Stop with Ctrl+C. curl returns nonzero on an unsuccessful request.
endpoint=${ORDS_URL:-${APP_URL:-http://localhost:8081}/api/readings}
while true; do
    curl --fail --silent --show-error -H 'X-Client-Id: workload' "$endpoint"
    printf '\n'
    sleep 1
done
