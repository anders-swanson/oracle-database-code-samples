---
name: ords-docker-compose
description: Docker Compose setup for running ORDS with Oracle AI Database Free locally.
tags:
  - Database
  - APEX
  - docker
  - MongoDB
  - ORDS
  - MCP
  - oraclefree
blog_post: "https://andersswanson.dev/2025/12/12/oracle-rest-data-services-ords-with-docker-compose/"
---

# Oracle REST Data Services (ORDS) + Oracle AI Database with Docker Compose

Simple Docker Compose setup to run Oracle REST Data Services (ORDS) connected to an Oracle AI Database Free container!

ORDS enables a variety of HTTP APIs for the database, including the wire-compatible MongoDB API, APEX, SQL Worksheet, and more.

## Containers

- **ords**: ORDS container for exposing REST APIs. The ORDS HTTP dashboard is available on `http://localhost:8888`
- **ordsdb**: Oracle AI Database container. The database container externally accessible at `testuser/testpwd@localhost:1555/freepdb1`

The setup includes pre-configured volumes for ORDS configuration and database initialization scripts.

## Prerequisites

- Docker-compatible container runtime and Docker Compose 2.20 or newer installed on your system.
- Internet access if you want the stack to download APEX automatically.

## Directory Structure

- `docker-compose.yml`: Main composition file defining services.
- `oracle/`: Database initialization scripts (e.g., [`ords_init.sql`](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/ords-docker-compose/oracle/ords_init.sql)).
- `apex/`: Optional local APEX distribution directory. Leave it empty to let the stack download APEX when `APEX_ENABLED=true`.
- `ords_config/`: Placeholder ORDS configuration directory. You do not need to modify this directory, ORDS will populate its configuration data here on startup.
- `keycloak/`: Realm import used by the optional `mcp` profile.
- `scripts/`: APEX download, MCP configuration, and local token helpers.

## Usage

1. Clone the repo and navigate to this directory:
   ```
   cd ords-docker-compose
   ```

2. Start the services:
   ```
   docker-compose up -d
   ```

3. Start with APEX enabled:
   ```
   APEX_ENABLED=true docker-compose up -d
   ```

   When APEX is enabled, the stack uses files from `apex/` if that directory contains an extracted APEX distribution. If local files are not present, it downloads the latest APEX ZIP from Oracle and stores the extracted files in the `apex_files` Docker volume.

4. Start with checksum verification for the downloaded APEX ZIP:
   ```
   APEX_DOWNLOAD_SHA256=<sha256> APEX_ENABLED=true docker-compose up -d
   ```

   `APEX_DOWNLOAD_SHA256` is optional because the default `apex-latest.zip` URL moves when Oracle publishes a new APEX release. Set it when you want a deterministic download.

5. Stop the services:
   ```
   docker-compose down
   ```

## Accessing Services

- **ORDS HTTP Endpoint**: `http://localhost:8888/`. Use `testuser/testpwd` as the login information for services like SQL Developer.
- **APEX**: `http://localhost:8888/ords/apex` when started with `APEX_ENABLED=true`.
- **MongoDB API**: Port 27017 (e.g., connect using MongoDB clients to `localhost:27017`).
- **Oracle AI Database**: Connect via SQL*Plus or tools like SQLcl to `localhost:1555/freepdb1` with username `testuser` and password `testpwd`.

## Configuration Details

- **Features Enabled**:
  - REST-Enabled SQL and Database APIs
  - SQL Developer web view
  - Wire compatible MongoDB API
  - APEX when `APEX_ENABLED=true`
- **Security**: Uses HTTP for local demo mode. If you're using ORDS in production, ensure you use proper certificates.

## ORDS MCP with Keycloak as an identity provider (IdP)

The optional `mcp` profile configures ORDS for MCP with Keycloak as an identity provider. Keycloak is configured with a checked-in realm import, enables JWT authentication in ORDS, and creates a read-only MCP pool with sample orders. Keycloak listens on `http://localhost:8081`, and ORDS MCP is available at `http://localhost:8888/mcp`.

### Start the lab

Run from `ords-docker-compose/`:

```bash
docker compose --profile mcp up -d --wait
```

The profile starts Keycloak in development mode and imports the [realm configuration](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/ords-docker-compose/keycloak/ords-lab-realm.json).

The database startup script, [`ords_init.sql`](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/ords-docker-compose/oracle/ords_init.sql), creates the sales schema, seeds the data, and creates the `MCP_READER` user. The `mcp-config` service waits for Keycloak and the initialized schema, then configures ORDS.

The sales schema has four related tables in `TESTUSER` and four read-only views with the same names in `MCP_READER`:

| Table / view | Sample rows | Contents |
| --- | --- | --- |
| `CUSTOMERS` | 6 | Names, emails, cities, and country codes |
| `PRODUCTS` | 8 | Product categories, current unit prices, and stock quantities |
| `ORDERS` | 12 | Customer, order date, and status |
| `ORDER_ITEMS` | 24 | Order, product, quantity, and purchase unit price |

Orders reference customers, and order items reference orders and products through foreign keys.

`MCP_READER` has `CREATE SESSION` and `SELECT` on the four base tables.

### Get a token

For the terminal examples, export a token as shown below. The Codex and Claude Code configurations below acquire tokens automatically.

```bash
export ORDS_MCP_TOKEN=$(./scripts/get-token.sh)
```

The helper uses the imported `ords-lab` client's credentials to obtain a short-lived JWT. These are fixed local demo credentials: client ID `ords-lab`, client secret `local-demo-secret`. Obtain a fresh token when it expires.

The imported configuration provides:

| Setting | Value |
| --- | --- |
| Realm | `ords-lab` |
| Issuer | `http://localhost:8081/realms/ords-lab` |
| Audience | `http://localhost:8888/mcp` |
| Scope | `urn:oracle:dbtools:ords:mcpserver:all` |
| Grant | `client_credentials` |

ORDS fetches public signing keys from `http://keycloak:8080/realms/ords-lab/protocol/openid-connect/certs` over the Compose network, the issuer is the public localhost URL. Keycloak's [realm import](https://www.keycloak.org/server/importExport) and [hostname configuration](https://www.keycloak.org/server/hostname) documentation describe these mechanisms.

### Connect Codex desktop or CLI

Add or update `ords` in `~/.codex/config.toml`, replacing the script path with the absolute path to your checkout:

```toml
[mcp_servers.ords]
url = "http://localhost:8888/mcp"
http_headers_helper = "/bin/bash /absolute/path/to/oracle-database-java-samples/ords-docker-compose/scripts/get-token.sh --headers"
```

The same [token script](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/ords-docker-compose/scripts/get-token.sh) returns a raw token by default or authorization-header JSON with `--headers`. Codex runs it to authenticate and refreshes the headers after an expired token is rejected, so no token export is needed for desktop or CLI use.

Remove any previous `bearer_token_env_var` or static `Authorization` header for `ords`, then restart Codex once to load the configuration. Quote the script path inside the command string if it contains spaces. See the [official Codex MCP documentation](https://learn.chatgpt.com/docs/extend/mcp?surface=desktop).

### Connect Claude Code

Merge this entry into `.mcp.json` in this lab directory, replacing the script path with the absolute path to your checkout:

```json
{
  "mcpServers": {
    "ords": {
      "type": "http",
      "url": "http://localhost:8888/mcp",
      "headersHelper": "/bin/bash /absolute/path/to/oracle-database-java-samples/ords-docker-compose/scripts/get-token.sh --headers"
    }
  }
}
```

Start `claude` from this directory, accept the directory trust and project MCP prompts, and use `/mcp` to check the connection. The same helper used by Codex supplies fresh authorization headers at connection time. After a tool call returns HTTP 401 or 403, Claude Code re-runs the helper, reconnects, and retries once. No token export is needed. Replace the previous static `headers` block with `headersHelper` if you used the earlier configuration.

See the [official Claude Code dynamic-header documentation](https://code.claude.com/docs/en/mcp#use-dynamic-headers-for-custom-authentication).

### Try a query

In either client, ask:

> Use the ords MCP server to discover the mcp-lab database, inspect its schema, and report the total order value by status.

The sample returns `CANCELLED = 99.93`, `DELIVERED = 709.82`, `PENDING = 821.78`, and `SHIPPED = 584.38`.
Other prompts that exercise the relationships:

- Which customers have the highest order value, excluding cancelled orders?
- What is the order value by product category and customer country?
- How does October order value compare with November, excluding cancelled orders?
- Which products appear in pending orders, and how much stock is available?

For example, the category and country question can use this SQL through `sql_run`:

```sql
select c.country, p.category, sum(i.quantity * i.unit_price) as total
from customers c
join orders o on o.customer_id = c.id
join order_items i on i.order_id = o.id
join products p on p.id = i.product_id
where o.status <> 'CANCELLED'
group by c.country, p.category
order by c.country, p.category;
```

Both Codex and Claude Code renew expired tokens through the helper above. Only the terminal examples need a manual token export: if a curl call returns HTTP 401, run `export ORDS_MCP_TOKEN=$(./scripts/get-token.sh)` again and retry. These examples do not use browser-based OAuth login. Run the clients locally on the same machine as Compose so `localhost:8888` reaches ORDS.

See Oracle's [MCP configuration guide](https://docs.oracle.com/en/database/oracle/oracle-rest-data-services/26.3/ordig/configuring-model-context-protocol-mcp.html) and [MCP developer guide](https://docs.oracle.com/en/database/oracle/oracle-rest-data-services/26.3/orddg/using-ords-model-context-protocol-mcp.html) for the authentication and tool interfaces.

## ORDS MCP with Deep Data Security

The optional Deep Data Security mode runs locally in Compose. Keycloak issues a JWT with a top-level `roles` claim, and ORDS checks it against each pool's `mcp.role` setting. Each pool connects with `PASSWORD` authentication as a fixed Deep Data Security end user. Oracle AI Database applies that end user's read-only data grant to filter rows and mask columns on the same `TESTUSER.CUSTOMERS` table. See the [Keycloak realm](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/ords-docker-compose/deep-sec/keycloak/ords-deep-sec-realm.json) and [data grants](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/ords-docker-compose/deep-sec/grants.sql).

| Demo login | Keycloak sales role | MCP pool | Database end user | Visible rows | `EMAIL` |
| --- | --- | --- | --- | --- | --- |
| `sales-us` | `SALES_US` | `sales-us` | `SALES_US_READER` | Four US customers | `NULL` |
| `sales-ca` | `SALES_CA` | `sales-ca` | `SALES_CA_READER` | Two Canadian customers | Visible |
| `sales-none` | None | None | — | — | — |

All three demo users have the `ORDS_MCP_SERVER` role to access the MCP endpoint. The sales roles determine which pools a caller can use. Callers in the same pool share its database identity.

### Start the lab

Stop any other instance of this lab before starting the profile, since they share host ports. From `ords-docker-compose/`, use ORDS 26.3 or later and an Oracle AI Database 26ai Free image with Deep Data Security support.

```bash
docker compose --env-file deep-sec/compose.env up -d --wait
```

The environment file enables the `mcp` profile and selects a dedicated Compose project, Keycloak realm, and ORDS configuration volume.

### Connect Codex desktop or CLI

Add these entries to `~/.codex/config.toml` and replace the helper paths with the absolute paths in your checkout. Restart Codex to load the configuration.

```toml
[mcp_servers.ords-sales-us]
url = "http://localhost:8888/mcp"
http_headers_helper = "/bin/bash \"/absolute/path/to/oracle-database-java-samples/ords-docker-compose/scripts/get-token.sh\" --headers sales-us"

[mcp_servers.ords-sales-ca]
url = "http://localhost:8888/mcp"
http_headers_helper = "/bin/bash \"/absolute/path/to/oracle-database-java-samples/ords-docker-compose/scripts/get-token.sh\" --headers sales-ca"
```

### Connect Claude Code

Merge these entries into `.mcp.json` in the lab directory and replace the helper paths with the absolute paths in your checkout. Start Claude Code from this directory and use `/mcp` to check the connections.

```json
{
  "mcpServers": {
    "ords-sales-us": {
      "type": "http",
      "url": "http://localhost:8888/mcp",
      "headersHelper": "/bin/bash \"/absolute/path/to/oracle-database-java-samples/ords-docker-compose/scripts/get-token.sh\" --headers sales-us"
    },
    "ords-sales-ca": {
      "type": "http",
      "url": "http://localhost:8888/mcp",
      "headersHelper": "/bin/bash \"/absolute/path/to/oracle-database-java-samples/ords-docker-compose/scripts/get-token.sh\" --headers sales-ca"
    }
  }
}
```

### Verify row and column access

In either client, ask `ords-sales-us` and `ords-sales-ca` to call `database_list` and then run the same query with `sql_run` against their respective pools. The helper signs in to Keycloak as the demo user named after `--headers` and supplies the bearer token for that entry.

```sql
select id, name, country, email
from testuser.customers
order by id;
```

A request to the other pool returns HTTP 403. To check access with a valid token that has no sales role, use `sales-none` as the helper's user argument. For that user, `tools/list` returns an empty list. For terminal requests, run `./scripts/get-token.sh sales-us` to print a raw bearer token.

To stop the lab, use the same environment file.

```bash
docker compose --env-file deep-sec/compose.env down
```

### Token passthrough and private hostnames

A single pool can use `APP_TOKEN_WITH_END_USER_TOKEN` to forward each caller's Keycloak token to Oracle AI Database as the end-user security context. ORDS first obtains a separate database-access token from the trusted HTTPS endpoint configured in `db.access.token.endpoint`. The database can then map the caller's Keycloak realm roles to data roles using `CREATE DATA ROLE … MAPPED TO 'ROLE=…'`. [ORDS application token authentication](https://docs.oracle.com/en/database/oracle/oracle-rest-data-services/26.3/orddg/ords-mcp-deep-security.html#GUID-29D9FC88-1FAD-4684-A89A-EF6B7790A7D9) · see also [Keycloak realm role mapping](https://docs.oracle.com/en/database/oracle/oracle-database/26/ddscg/configure-realm-roles-and-role-claim.html) and [ORDS configuration reference](https://docs.oracle.com/en/database/oracle/oracle-rest-data-services/26.3/ordig/about-REST-configuration-files.html#:~:text=db.access.token.endpoint)

## Customization

- Add database initialization scripts to `oracle/` (e.g., modify `ords_init.sql` or add your own scripts).
- Environment variables can be adjusted in `docker-compose.yml` (e.g., change passwords or ports).
- To use a specific APEX ZIP, set `APEX_DOWNLOAD_URL`.
- To change the APEX public user password used during installation, set `APEX_PWD`.
- To use local APEX files, extract the APEX distribution into `apex/` so that `apex/apxsilentins.sql` exists, then start with `APEX_ENABLED=true`.
- The downloader patches the copied APEX installer in the `apex_files` volume so the ORDS container uses strong local-demo passwords during the silent APEX install. Your local `apex/` files are not modified.

## Notes

- The database has no persistent data volume in this sample; recreating `ordsdb` discards its data. Back up any work before switching images.
- ORDS uses `container-registry.oracle.com/database/ords:latest`.
- Volumes persist configuration and downloaded APEX files; remove them with `docker-compose down -v` if needed.
- If you first started the database without APEX and later enable APEX, use `docker-compose down -v` before restarting so the Oracle AI Database container initializes cleanly with APEX.
- For more details on ORDS, visit the [official documentation](https://docs.oracle.com/en/database/oracle/oracle-rest-data-services/).

If you encounter issues, check container logs:
```
docker logs ords
docker logs ordsdb
```
