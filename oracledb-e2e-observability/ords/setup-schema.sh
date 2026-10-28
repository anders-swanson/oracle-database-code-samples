#!/bin/bash
set -eu

# The image runs this hook after installing ORDS metadata and before serving requests.
sql -s "sys/${ORACLE_PWD}@${CONN_STRING} as sysdba" \
    @/opt/oracle/lab-schema.sql "LAB_APP/${ORACLE_PWD}@${CONN_STRING}"
