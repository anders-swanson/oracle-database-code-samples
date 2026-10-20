#!/bin/bash
# Oracle's startup runner sources shell scripts. Isolate variables/options in a subshell.
(
set -eu
wallet_root=/opt/oracle/oradata/wallets
mkdir -p "$wallet_root"
# Only the public CA crosses containers; NGINX keeps its private key locally.
curl --fail --silent --show-error http://nginx/lab-ca.crt -o "$wallet_root/ca.crt"
current_root=$(sqlplus -s / as sysdba <<'SQL'
WHENEVER SQLERROR EXIT FAILURE
SET HEADING OFF FEEDBACK OFF PAGES 0
SELECT value FROM v$parameter WHERE name = 'wallet_root';
EXIT;
SQL
)
if [[ "$current_root" != *"$wallet_root"* ]]; then
    # WALLET_ROOT is static. Only the first start needs an instance restart.
    sqlplus -s / as sysdba <<SQL
WHENEVER SQLERROR EXIT FAILURE
ALTER SYSTEM SET wallet_root='$wallet_root' SCOPE=SPFILE;
SHUTDOWN IMMEDIATE;
STARTUP;
ALTER PLUGGABLE DATABASE ALL OPEN;
EXIT;
SQL
fi
pdb_guid=$(sqlplus -s / as sysdba <<'SQL'
WHENEVER SQLERROR EXIT FAILURE
SET HEADING OFF FEEDBACK OFF PAGES 0
SELECT RAWTOHEX(guid) FROM v$pdbs WHERE name = 'FREEPDB1';
EXIT;
SQL
)
pdb_guid=$(echo "$pdb_guid" | tr -d '[:space:]')
[[ "$pdb_guid" =~ ^[A-F0-9]{32}$ ]]
# A stable CA permits proxy certificate regeneration without reimporting each leaf.
for component in disttrc tls; do
    wallet_dir="$wallet_root/$pdb_guid/$component"
    mkdir -p "$wallet_dir"
    if [[ ! -f "$wallet_dir/cwallet.sso" ]] || ! cmp -s "$wallet_root/ca.crt" "$wallet_dir/ca.crt"; then
        # These wallets belong only to the lab's telemetry exporter.
        rm -f "$wallet_dir/cwallet.sso" "$wallet_dir/ewallet.p12"
        "$ORACLE_HOME/bin/orapki" wallet create -wallet "$wallet_dir" -pwd 'ObservabilityWallet123#' -auto_login
        "$ORACLE_HOME/bin/orapki" wallet add -wallet "$wallet_dir" -pwd 'ObservabilityWallet123#' \
            -trusted_cert -cert "$wallet_root/ca.crt"
        cp "$wallet_root/ca.crt" "$wallet_dir/ca.crt"
    fi
done
sqlplus -s / as sysdba @/opt/oracle/scripts/observability.sql
)
