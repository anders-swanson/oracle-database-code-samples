#!/usr/bin/env bash
set -euo pipefail

config=/etc/ords/config
scope=urn:oracle:dbtools:ords:mcpserver:all
realm=ords-lab
if [[ "${DEEP_SEC_ENABLED:-false}" == true ]]; then
  realm=ords-deep-sec
fi

issuer="http://localhost:8081/realms/$realm"
ords --config "$config" config set --global feature.mcp true
ords --config "$config" config set --global mcp.security.jwt.profile.issuer "$issuer"
ords --config "$config" config set --global mcp.security.jwt.profile.audience http://localhost:8888/mcp
ords --config "$config" config set --global mcp.security.jwt.profile.jwk.url "http://keycloak:8080/realms/$realm/protocol/openid-connect/certs"
ords --config "$config" config set --global mcp.security.jwt.profile.authorization.server.url "$issuer"

configure_pool() {
  local name=$1 username=$2 password=$3 description=$4
  local pool=(ords --config "$config" config --db-pool "$name")

  "${pool[@]}" set db.connectionType basic
  "${pool[@]}" set db.hostname ordsdb
  "${pool[@]}" set db.port 1521
  "${pool[@]}" set db.servicename freepdb1
  "${pool[@]}" set db.username "$username"
  "${pool[@]}" set db.description "$description"
  printf '%s\n%s\n' "$password" "$password" | "${pool[@]}" secret db.password
}

if [[ "${DEEP_SEC_ENABLED:-false}" == true ]]; then
  sql -L -s 'sys/Welcome12345@ordsdb:1521/freepdb1 as sysdba' @/deep-sec/grants.sql
  ords --config "$config" config set --global mcp.security.jwt.profile.role.claim.name /roles

  configure_pool sales-us SALES_US_READER LocalUSReader12345 'US customers (Deep Data Security)'
  ords --config "$config" config --db-pool sales-us set mcp.role SALES_US
  configure_pool sales-ca SALES_CA_READER LocalCAReader12345 'Canadian customers (Deep Data Security)'
  ords --config "$config" config --db-pool sales-ca set mcp.role SALES_CA
else
  configure_pool mcp-lab MCP_READER ReadOnly12345 'Local sales lab (read only)'
  ords --config "$config" config --db-pool mcp-lab set mcp.scope "$scope"
fi
