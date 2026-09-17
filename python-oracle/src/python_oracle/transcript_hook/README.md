---
name: python-oracle/src/python_oracle/transcript_hook
description: Python agent hook that persists complete Codex session transcripts in Oracle AI Database.
tags:
  - AI
  - python
  - Codex
  - observability
---

# Persist Agent Transcripts in Oracle AI Database

This sample is a command hook that writes an agent's transcript to Oracle AI Database. It works directly with the [Codex `Stop` hook](https://learn.chatgpt.com/docs/hooks#stop) and can be adapted to other agents that provide a session identifier and transcript file path.

The hook upserts one row per agent source and session into `AGENT_TRANSCRIPTS`. Each row has a database-generated numeric primary key, while a unique constraint on `(agent_source, session_id)` provides the stable identity used by the upsert. Each completed turn replaces the stored transcript with the latest complete version while retaining the same primary key. The table is created automatically the first time the hook runs.

The raw transcript is stored instead of parsing individual messages because the Codex transcript format is not a stable hook interface. Transcripts can contain prompts, model output, file paths, and tool activity, so only enable this hook when storing that data is appropriate.

## Prerequisites

- Python 3.13+
- uv
- An Oracle AI Database user with permission to create a table and read and write its rows

To start a disposable local test database with the `appuser` schema, run:

```bash
export ORACLE_DB_PASSWORD='appuser_password'
docker run --name oracle-free-test --rm -d \
  -p 1521:1521 \
  -e ORACLE_RANDOM_PASSWORD=y \
  -e APP_USER=appuser \
  -e APP_USER_PASSWORD="$ORACLE_DB_PASSWORD" \
  gvenzl/oracle-free:latest
```

The hook connects as `appuser` to `localhost:1521/freepdb1`. Wait for the container
to finish initializing before connecting. Stop the disposable database with
`docker stop oracle-free-test`.

Install the Python dependencies from the `python-oracle/` directory:

```bash
uv sync
```

## Configure the Hook

Copy the example configuration to a location outside the repository:

```bash
mkdir -p ~/.config/codex
cp src/python_oracle/transcript_hook/example-config.yaml \
  ~/.config/codex/oracle-transcript-hook.yaml
```

The YAML file is a direct map of keyword arguments passed to `oracledb.connect()`:

```yaml
user: "${ORACLE_DB_USER}"
password: "${ORACLE_DB_PASSWORD}"
dsn: "${ORACLE_DB_DSN}"
```

| Field | Required | Description |
|---|---:|---|
| `user` | Yes | Database user name. |
| `password` | Yes | Database password. |
| `dsn` | Yes | Easy Connect string or TNS alias, such as `localhost:1521/freepdb1`. |
| `config_dir` | No | Directory containing Oracle Net files such as `tnsnames.ora`. |
| `wallet_location` | No | Wallet directory for an mTLS connection. |
| `wallet_password` | No | Wallet password for an mTLS connection. |

The loader returns the YAML map directly after applying Python's `os.path.expandvars()` to its string values. Both `$NAME` and `${NAME}` references are supported. Optional python-oracledb connection arguments such as `config_dir`, `wallet_location`, and `wallet_password` can be added when needed.

For a local Oracle AI Database Free instance:

```bash
export ORACLE_DB_USER=appuser
export ORACLE_DB_PASSWORD='your-password'
export ORACLE_DB_DSN='localhost:1521/freepdb1'
```

Environment expansion happens after the YAML is parsed, so values containing `:`, `#`, or other YAML punctuation are preserved exactly. An unset environment reference remains unchanged and causes the database connection to fail rather than silently becoming an empty credential.

Do not put the password directly in the YAML file or `hooks.json`. The hook inherits environment variables from the Codex process, so start Codex from a shell where these variables are set or provide them through your normal secret-management tooling.

## Configure Codex

Use [example-hook.json](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/python-oracle/src/python_oracle/transcript_hook/example-hook.json) as the template for the `Stop` entry in `~/.codex/hooks.json`. Replace `/path/to/oracle-database-code-samples` with the absolute path to this repository and `/Users/you` with your home directory.

The caller can optionally add `--source another-agent` to the hook command to select a different transcript producer namespace. When omitted, the source defaults to `codex`; it does not belong in the database connection YAML.

Restart Codex, open Hooks in Settings, review the command, and trust it. The next completed turn will create or update the session row.

The `Stop` event is used intentionally. Codex supplies `session_id` and `transcript_path` to command hooks, and a `Stop` hook has enough time to establish a database connection. A `SessionEnd` hook is limited to three seconds and is therefore not a reliable place to open a new database connection.

## Query Stored Transcripts

```sql
select id,
       agent_source,
       session_id,
       updated_at,
       working_directory,
       model,
       dbms_lob.getlength(transcript) as transcript_characters
from agent_transcripts
order by updated_at desc;
```

To retrieve the raw transcript for a session:

```sql
select transcript
from agent_transcripts
where agent_source = :agent_source
  and session_id = :session_id;
```

The generated `id` is the primary key. `(agent_source, session_id)` is unique so that two agent products can use the same session identifier without overwriting each other.

The table name is fixed as `AGENT_TRANSCRIPTS` in the sample code instead of accepting an interpolated SQL identifier from configuration.

If you ran an earlier version of this sample that created `AGENT_TRANSCRIPTS` with `session_id` as its primary key, migrate or recreate that development table before enabling this version. `CREATE TABLE IF NOT EXISTS` deliberately does not alter an existing table.

## Browse Transcripts

The included local viewer is intended for agent-run debugging: select a completed session to read recognized user and assistant messages as a conversation. Tool and lifecycle records are summarized under **Activity**, and the complete raw JSONL remains available under **Raw JSONL transcript**. Unrecognized future Codex record types remain available in the raw view.

```bash
uv run python -m src.python_oracle.transcript_hook.transcript_viewer \
  --config ~/.config/codex/oracle-transcript-hook.yaml \
  --source codex
```

`--source` is optional; leave it out to browse every agent source. The viewer shows the 100 most recently updated sessions by default; use `--limit` to change that number. It opens a browser on `http://127.0.0.1:8765/`; add `--no-browser` when running it remotely.

Use the trash-can button at the right of a row to permanently delete that transcript. The browser asks for confirmation, and a later `Stop` event for the same agent source and session can create it again.

## Test the Hook Manually

Create a small JSONL file and pipe a Codex-shaped event to the module:

```bash
printf '%s\n' '{"type":"message","role":"user","content":"Hello"}' > /tmp/sample-transcript.jsonl
printf '%s\n' '{"session_id":"sample-session","transcript_path":"/tmp/sample-transcript.jsonl","cwd":"/tmp","hook_event_name":"Stop","model":"sample-model"}' \
  | uv run python -m src.python_oracle.transcript_hook.oracle_transcript_hook \
      --config ~/.config/codex/oracle-transcript-hook.yaml
```

The command prints `{}` when the transcript has been committed successfully. Errors are written to standard error and return a nonzero exit status so Codex reports the hook failure.

## Run the Tests

The integration test loads a database connection YAML map with environment references, starts Oracle AI Database Free with Testcontainers, and verifies the table creation, insert, update, and CLOB readback:

```bash
uv run python -m unittest tests.test_transcript_hook -v
```
