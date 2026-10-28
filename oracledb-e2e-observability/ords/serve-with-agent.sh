#!/bin/bash
set -eu

if [[ "${1:-}" == "serve" ]]; then
    set -- --java-options "-javaagent:/opt/oracle/ords/opentelemetry-javaagent.jar" "$@"
fi
exec /opt/oracle/ords/bin/ords-original "$@"
