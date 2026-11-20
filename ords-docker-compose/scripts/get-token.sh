#!/usr/bin/env bash
set -euo pipefail

# Include Homebrew tools when launched by the macOS desktop app.
export PATH="/opt/homebrew/bin:/usr/local/bin:${PATH:-/usr/bin:/bin}"

headers=false
if [[ "${1:-}" == "--headers" ]]; then
  headers=true
  shift
fi

# Local demo credentials from the selected profile's realm import.
case "${1:-}" in
  "")
    realm=ords-lab
    token_args=(--data-urlencode grant_type=client_credentials
      --data-urlencode client_id=ords-lab --data-urlencode client_secret=local-demo-secret)
    ;;
  sales-us|sales-ca|sales-none)
    realm=ords-deep-sec
    token_args=(--data-urlencode grant_type=password
      --data-urlencode client_id=ords-lab-users --data-urlencode "username=$1"
      --data-urlencode password=LocalSales12345)
    ;;
  *)
    echo "Usage: $0 [--headers] [sales-us|sales-ca|sales-none]" >&2
    exit 2
    ;;
esac

curl --fail --silent --show-error \
  "http://localhost:8081/realms/$realm/protocol/openid-connect/token" \
  "${token_args[@]}" --data-urlencode scope=urn:oracle:dbtools:ords:mcpserver:all |
  jq --exit-status --compact-output --raw-output --argjson headers "$headers" '
    .access_token // error("No access token returned")
    | if $headers then {Authorization: ("Bearer " + .)} else . end
  '
