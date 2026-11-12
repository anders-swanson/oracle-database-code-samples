#!/usr/bin/env bash
set -euo pipefail

ords --config /etc/ords/config config set --global feature.mcp true
ords --config /etc/ords/config config set --global mcp.security.jwt.profile.issuer http://localhost:8081/realms/ords-lab
ords --config /etc/ords/config config set --global mcp.security.jwt.profile.audience http://localhost:8888/mcp
ords --config /etc/ords/config config set --global mcp.security.jwt.profile.jwk.url http://keycloak:8080/realms/ords-lab/protocol/openid-connect/certs
ords --config /etc/ords/config config set --global mcp.security.jwt.profile.authorization.server.url http://localhost:8081/realms/ords-lab

ords --config /etc/ords/config config --db-pool mcp-lab set db.connectionType basic
ords --config /etc/ords/config config --db-pool mcp-lab set db.hostname ordsdb
ords --config /etc/ords/config config --db-pool mcp-lab set db.port 1521
ords --config /etc/ords/config config --db-pool mcp-lab set db.servicename freepdb1
ords --config /etc/ords/config config --db-pool mcp-lab set db.username MCP_READER
ords --config /etc/ords/config config --db-pool mcp-lab set db.description 'Local sales lab (read only)'
ords --config /etc/ords/config config --db-pool mcp-lab set mcp.scope urn:oracle:dbtools:ords:mcpserver:all
printf '%s\n%s\n' ReadOnly12345 ReadOnly12345 |
  ords --config /etc/ords/config config --db-pool mcp-lab secret db.password
