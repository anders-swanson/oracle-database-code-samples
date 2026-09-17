from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence, TextIO

import oracledb

from src.python_oracle.transcript_hook.config import load_config


TABLE_NAME = "AGENT_TRANSCRIPTS"


@dataclass(frozen=True)
class HookEvent:
    session_id: str
    transcript_path: Path | None
    cwd: str
    hook_event_name: str
    model: str | None
    payload: dict[str, object]

    @classmethod
    def from_stream(cls, stream: TextIO) -> HookEvent:
        try:
            payload = json.load(stream)
        except json.JSONDecodeError as error:
            raise ValueError(f"Hook input is not valid JSON: {error.msg}") from error

        if not isinstance(payload, dict):
            raise ValueError("Hook input must be a JSON object")

        required = ("session_id", "cwd", "hook_event_name")
        missing = [
            name
            for name in required
            if not isinstance(payload.get(name), str) or not payload[name]
        ]
        if missing:
            raise ValueError(f"Hook input is missing string field(s): {', '.join(missing)}")

        transcript_path_value = payload.get("transcript_path")
        if transcript_path_value is None:
            transcript_path = None
        elif not isinstance(transcript_path_value, str) or not transcript_path_value:
            raise ValueError("Hook input has an invalid transcript_path")
        else:
            transcript_path = Path(transcript_path_value)
        if transcript_path is not None and not transcript_path.is_file():
            raise ValueError(f"Transcript file does not exist: {transcript_path}")

        model = payload.get("model")
        return cls(
            session_id=payload["session_id"],
            transcript_path=transcript_path,
            cwd=payload["cwd"],
            hook_event_name=payload["hook_event_name"],
            model=model if isinstance(model, str) else None,
            payload=payload,
        )


def connect(database_config: dict) -> oracledb.Connection:
    return oracledb.connect(**database_config)


def persist_transcript(
    connection: oracledb.Connection,
    source: str,
    event: HookEvent,
) -> None:
    if event.transcript_path is None:
        return

    transcript = event.transcript_path.read_text(encoding="utf-8")
    hook_payload = json.dumps(event.payload, separators=(",", ":"), ensure_ascii=False)
    transcript_lob = connection.createlob(oracledb.DB_TYPE_CLOB, transcript)
    create_table = f"""
        create table if not exists {TABLE_NAME} (
            id number generated always as identity primary key,
            agent_source varchar2(100) not null,
            session_id varchar2(255) not null,
            updated_at timestamp with time zone default systimestamp not null,
            working_directory varchar2(4000) not null,
            model varchar2(255),
            hook_event_name varchar2(64) not null,
            hook_payload clob check (hook_payload is json) not null,
            transcript clob not null,
            unique (agent_source, session_id)
        )
    """
    merge_transcript = f"""
        merge into {TABLE_NAME} target
        using (
            select :agent_source agent_source, :session_id session_id
            from dual
        ) incoming
           on (target.agent_source = incoming.agent_source
               and target.session_id = incoming.session_id)
        when matched then update set
            target.updated_at = systimestamp,
            target.working_directory = :working_directory,
            target.model = :model,
            target.hook_event_name = :hook_event_name,
            target.hook_payload = :hook_payload,
            target.transcript = :transcript
        when not matched then insert (
            agent_source, session_id, working_directory, model,
            hook_event_name, hook_payload, transcript
        ) values (
            :agent_source, :session_id, :working_directory, :model,
            :hook_event_name, :hook_payload, :transcript
        )
    """

    with connection.cursor() as cursor:
        cursor.execute(create_table)
        cursor.execute(
            merge_transcript,
            {
                "agent_source": source,
                "session_id": event.session_id,
                "working_directory": event.cwd,
                "model": event.model,
                "hook_event_name": event.hook_event_name,
                "hook_payload": hook_payload,
                "transcript": transcript_lob,
            },
        )
    connection.commit()


def run(
    config_path: Path,
    source: str = "codex",
    stdin: TextIO = sys.stdin,
    stdout: TextIO = sys.stdout,
) -> None:
    database_config = load_config(config_path)
    event = HookEvent.from_stream(stdin)
    with connect(database_config) as connection:
        persist_transcript(connection, source, event)

    # Codex Stop hooks expect a JSON object on stdout. An empty object lets the
    # turn finish normally without adding model-visible context.
    json.dump({}, stdout)
    stdout.write("\n")


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Persist an agent transcript in Oracle AI Database.",
    )
    parser.add_argument(
        "--config",
        required=True,
        type=Path,
        help="Path to the Oracle AI Database connection YAML file.",
    )
    parser.add_argument(
        "--source",
        default="codex",
        help="Transcript producer namespace (default: codex).",
    )
    arguments = parser.parse_args(argv)
    try:
        run(arguments.config, arguments.source)
    except (KeyError, OSError, TypeError, ValueError, oracledb.Error) as error:
        print(f"Could not persist transcript: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
