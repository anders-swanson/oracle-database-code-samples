#!/bin/bash

# Initialization script for DBMS_CLOUD and wallet with client certificates.
set -e

mkdir -p /opt/oracle/dbc
cd /opt/oracle/dbc
curl -O "$CERTS_FILE"
tar -xvf ./dbc_certs.tar

cd /opt/oracle/product/26ai/dbhomeFree/data/wallet
orapki wallet create -wallet . -pwd "$WALLET_PASSWORD" -auto_login
for i in /opt/oracle/dbc/*cer
do
    orapki wallet add -wallet . -trusted_cert -cert "$i" -pwd "$WALLET_PASSWORD"
done

"$ORACLE_HOME/perl/bin/perl" "$ORACLE_HOME/rdbms/admin/catcon.pl" \
    -u "SYS/$ORACLE_PASSWORD" \
    -force_pdb_mode 'READ WRITE' \
    -b dbms_cloud_install \
    -d "$ORACLE_HOME/rdbms/admin/" \
    -l /tmp \
    dbms_cloud_install.sql

sqlplus sys / as sysdba @/tmp/dbms_cloud_aces.sql