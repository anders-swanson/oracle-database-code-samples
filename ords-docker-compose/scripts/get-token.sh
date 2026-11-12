#!/usr/bin/env bash
set -euo pipefail

# Include Homebrew tools when launched by the macOS desktop app.
export PATH="/opt/homebrew/bin:/usr/local/bin:${PATH:-/usr/bin:/bin}"

headers=false
if [[ "${1:-}" == "--headers" ]]; then
  headers=true
fi

# Local demo credentials, matching keycloak/ords-lab-realm.json.
curl --fail --silent --show-error \
  http://localhost:8081/realms/ords-lab/protocol/openid-connect/token \
  --data-urlencode grant_type=client_credentials \
  --data-urlencode client_id=ords-lab \
  --data-urlencode client_secret=local-demo-secret \
  --data-urlencode scope=urn:oracle:dbtools:ords:mcpserver:all |
  jq --exit-status --compact-output --raw-output --argjson headers "$headers" '
    .access_token // error("No access token returned")
    | if $headers then {Authorization: ("Bearer " + .)} else . end
  '
