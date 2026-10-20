#!/bin/sh
set -eu
# Stop with Ctrl+C. curl returns nonzero on an unsuccessful request.
endpoint=${APP_URL:-http://localhost:8081}
while true; do
    curl --fail --silent --show-error -H 'X-Client-Id: workload' "$endpoint/api/readings"
    printf '\n'
    sleep 1
done
