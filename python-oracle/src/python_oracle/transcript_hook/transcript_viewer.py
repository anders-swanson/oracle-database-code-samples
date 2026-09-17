from __future__ import annotations

import argparse
import html
import json
import sys
import webbrowser
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Sequence
from urllib.parse import parse_qs, urlencode, urlparse

import oracledb

from src.python_oracle.transcript_hook.config import load_config
from src.python_oracle.transcript_hook.oracle_transcript_hook import TABLE_NAME, connect


@dataclass(frozen=True)
class TranscriptSummary:
    id: int
    source: str
    session_id: str
    updated_at: str
    working_directory: str
    model: str | None
    hook_event_name: str
    character_count: int


@dataclass(frozen=True)
class TranscriptMessage:
    role: str
    text: str


def text_content(content: object) -> str | None:
    if isinstance(content, str):
        return content
    if isinstance(content, dict):
        for key in ("text", "content", "message"):
            value = content.get(key)
            if isinstance(value, str):
                return value
        return None
    if not isinstance(content, list):
        return None

    parts = [text_content(item) for item in content]
    text = "\n".join(part for part in parts if part)
    return text or None


def transcript_message(record: dict[str, object]) -> TranscriptMessage | None:
    payload = record.get("payload")
    if not isinstance(payload, dict):
        payload = record

    role = payload.get("role")
    if isinstance(role, str) and role in {"user", "assistant"}:
        text = text_content(payload.get("content"))
        if text:
            return TranscriptMessage(role, text)

    if record.get("type") == "event_msg" and payload.get("type") == "agent_message":
        text = text_content(payload.get("message"))
        if text:
            return TranscriptMessage("assistant", text)
    return None


def transcript_activity(record: dict[str, object]) -> str:
    payload = record.get("payload")
    payload_type = payload.get("type") if isinstance(payload, dict) else None
    record_type = record.get("type")
    labels = [label for label in (record_type, payload_type) if isinstance(label, str)]
    return " · ".join(labels) or "Unclassified record"


def render_transcript(transcript: str) -> str:
    messages: list[TranscriptMessage] = []
    activity: list[str] = []
    invalid_lines = 0

    for line in transcript.splitlines():
        if not line.strip():
            continue
        try:
            record = json.loads(line)
        except json.JSONDecodeError:
            invalid_lines += 1
            continue
        if not isinstance(record, dict):
            activity.append("Non-object JSON record")
            continue

        message = transcript_message(record)
        if message:
            if not messages or messages[-1] != message:
                messages.append(message)
        else:
            activity.append(transcript_activity(record))

    message_cards = "".join(
        f'<article class="message {html.escape(message.role)}">'
        f'<p class="role">{html.escape(message.role.title())}</p>'
        f'<div>{html.escape(message.text)}</div>'
        "</article>"
        for message in messages
    ) or "<p>No user or assistant messages were recognized.</p>"
    activity_items = "".join(f"<li>{html.escape(item)}</li>" for item in activity)
    invalid_note = (
        f"<p>{invalid_lines} line(s) were not valid JSON.</p>" if invalid_lines else ""
    )

    return f"""
<section class="conversation">{message_cards}</section>
<details class="activity">
  <summary>Activity ({len(activity)})</summary>
  {invalid_note}<ul>{activity_items}</ul>
</details>
<details class="raw">
  <summary>Raw JSONL transcript</summary>
  <pre>{html.escape(transcript)}</pre>
</details>"""


def fetch_transcripts(
    connection: oracledb.Connection,
    source: str | None,
    limit: int,
) -> list[TranscriptSummary]:
    where_clause = "where agent_source = :source" if source else ""
    query = f"""
        select id, agent_source, session_id, updated_at, working_directory,
               model, hook_event_name, transcript_characters
        from (
            select id, agent_source, session_id, updated_at, working_directory,
                   model, hook_event_name,
                   dbms_lob.getlength(transcript) as transcript_characters
            from {TABLE_NAME}
            {where_clause}
            order by updated_at desc
        )
        where rownum <= :limit
    """
    parameters = {"limit": limit}
    if source:
        parameters["source"] = source

    with connection.cursor() as cursor:
        cursor.execute(query, parameters)
        return [
            TranscriptSummary(
                id=row[0],
                source=row[1],
                session_id=row[2],
                updated_at=row[3].isoformat(sep=" ", timespec="seconds"),
                working_directory=row[4],
                model=row[5],
                hook_event_name=row[6],
                character_count=row[7],
            )
            for row in cursor
        ]


def fetch_transcript(connection: oracledb.Connection, transcript_id: int) -> str:
    with connection.cursor() as cursor:
        cursor.execute(
            f"select transcript from {TABLE_NAME} where id = :id",
            {"id": transcript_id},
        )
        row = cursor.fetchone()
    if row is None:
        raise ValueError(f"Transcript {transcript_id} no longer exists")

    transcript = row[0]
    return transcript.read() if hasattr(transcript, "read") else transcript


def delete_transcript(connection: oracledb.Connection, transcript_id: int) -> bool:
    with connection.cursor() as cursor:
        cursor.execute(
            f"delete from {TABLE_NAME} where id = :id",
            {"id": transcript_id},
        )
        deleted = cursor.rowcount == 1
    connection.commit()
    return deleted


def page(title: str, content: str) -> str:
    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>{html.escape(title)}</title>
  <style>
    body {{ background: #f7f7f8; color: #1f2937; font: 15px system-ui, sans-serif; margin: 2rem auto; max-width: 1200px; padding: 0 1rem; }}
    h1 {{ margin-bottom: .25rem; }}
    .filter {{ display: flex; gap: .75rem; margin: 1.5rem 0; }}
    input, button {{ font: inherit; padding: .45rem .6rem; }}
    input {{ flex: 1; }}
    table {{ background: white; border-collapse: collapse; width: 100%; }}
    th, td {{ border-bottom: 1px solid #e5e7eb; padding: .65rem; text-align: left; vertical-align: top; }}
    th {{ background: #eef2ff; }}
    pre {{ background: #111827; color: #e5e7eb; overflow: auto; padding: 1rem; white-space: pre-wrap; word-break: break-word; }}
    a {{ color: #4338ca; }}
    .conversation {{ display: grid; gap: 1rem; margin: 1.5rem 0; }}
    .message {{ border-radius: .75rem; max-width: 85%; padding: .9rem 1rem; white-space: pre-wrap; word-break: break-word; }}
    .message.user {{ background: #dbeafe; justify-self: end; }}
    .message.assistant {{ background: white; border: 1px solid #e5e7eb; justify-self: start; }}
    .role {{ color: #4b5563; font-size: .8rem; font-weight: 700; margin: 0 0 .45rem; text-transform: uppercase; }}
    details {{ background: white; border: 1px solid #e5e7eb; border-radius: .5rem; margin: 1rem 0; padding: .8rem 1rem; }}
    summary {{ cursor: pointer; font-weight: 600; }}
    .activity ul {{ color: #4b5563; margin-bottom: 0; }}
    .delete {{ display: inline; }}
    .trash {{ background: transparent; border: 0; cursor: pointer; padding: .25rem; }}
  </style>
</head>
<body>{content}</body>
</html>"""


class TranscriptViewerServer(ThreadingHTTPServer):
    def __init__(
        self,
        address: tuple[str, int],
        database_config: dict,
        source: str | None,
        limit: int,
    ) -> None:
        super().__init__(address, TranscriptViewerRequestHandler)
        self.database_config = database_config
        self.source = source
        self.limit = limit


class TranscriptViewerRequestHandler(BaseHTTPRequestHandler):
    server: TranscriptViewerServer

    def do_GET(self) -> None:
        parsed_url = urlparse(self.path)
        query = parse_qs(parsed_url.query)
        source = query.get("source", [self.server.source or ""])[0] or None
        try:
            if parsed_url.path == "/":
                body = self.render_index(source)
            elif parsed_url.path == "/transcript":
                body = self.render_transcript(query, source)
            else:
                self.send_error(404, "Not found")
                return
        except (TypeError, ValueError, oracledb.Error) as error:
            self.send_error(500, f"Could not load transcript: {error}")
            return

        encoded_body = body.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded_body)))
        self.end_headers()
        self.wfile.write(encoded_body)

    def do_POST(self) -> None:
        if urlparse(self.path).path != "/transcript/delete":
            self.send_error(404, "Not found")
            return

        try:
            content_length = int(self.headers["Content-Length"])
            form = parse_qs(self.rfile.read(content_length).decode("utf-8"))
            transcript_id = int(form["id"][0])
            source = form.get("source", [self.server.source or ""])[0]
            with connect(self.server.database_config) as connection:
                if not delete_transcript(connection, transcript_id):
                    self.send_error(404, "Transcript not found")
                    return
        except (KeyError, TypeError, ValueError, UnicodeDecodeError, oracledb.Error) as error:
            self.send_error(400, f"Could not delete transcript: {error}")
            return

        self.send_response(303)
        self.send_header("Location", f"/?{urlencode({'source': source})}")
        self.end_headers()

    def render_index(self, source: str | None) -> str:
        with connect(self.server.database_config) as connection:
            transcripts = fetch_transcripts(connection, source, self.server.limit)

        rows = "".join(
            "<tr>"
            f"<td><a href=\"/transcript?{urlencode({'id': summary.id, 'source': source or ''})}\">{html.escape(summary.session_id)}</a></td>"
            f"<td>{html.escape(summary.source)}</td>"
            f"<td>{html.escape(summary.updated_at)}</td>"
            f"<td>{html.escape(summary.model or '')}</td>"
            f"<td>{summary.character_count}</td>"
            "<td>"
            f"<form class=\"delete\" method=\"post\" action=\"/transcript/delete\" onsubmit=\"return confirm('Delete this transcript permanently?')\">"
            f"<input type=\"hidden\" name=\"id\" value=\"{summary.id}\">"
            f"<input type=\"hidden\" name=\"source\" value=\"{html.escape(source or '', quote=True)}\">"
            "<button class=\"trash\" type=\"submit\" title=\"Delete transcript\" aria-label=\"Delete transcript\">🗑</button>"
            "</form>"
            "</td>"
            "</tr>"
            for summary in transcripts
        ) or "<tr><td colspan=\"6\">No transcripts found.</td></tr>"
        source_value = html.escape(source or "", quote=True)
        return page(
            "Oracle AI Database Transcript Viewer",
            f"""
<h1>Transcript Viewer</h1>
<p>Agent-run debugging for Oracle AI Database transcripts.</p>
<form class="filter" method="get">
  <input name="source" value="{source_value}" placeholder="Agent source (optional)">
  <button type="submit">Filter</button>
</form>
<table>
  <thead><tr><th>Session</th><th>Source</th><th>Updated</th><th>Model</th><th>Characters</th><th>Delete</th></tr></thead>
  <tbody>{rows}</tbody>
</table>""",
        )

    def render_transcript(self, query: dict[str, list[str]], source: str | None) -> str:
        transcript_id = int(query["id"][0])
        with connect(self.server.database_config) as connection:
            transcript = fetch_transcript(connection, transcript_id)

        return page(
            "Transcript",
            f"""
<p><a href="/?{urlencode({'source': source or ''})}">Back to sessions</a></p>
<h1>Transcript {transcript_id}</h1>
{render_transcript(transcript)}""",
        )

    def log_message(self, _format: str, *_arguments: object) -> None:
        return


def positive_integer(value: str) -> int:
    integer = int(value)
    if integer <= 0:
        raise argparse.ArgumentTypeError("must be greater than zero")
    return integer


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Browse agent transcripts stored in Oracle AI Database.",
    )
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--source", help="Only show transcripts from this source.")
    parser.add_argument("--limit", type=positive_integer, default=100)
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--no-browser", action="store_true")
    arguments = parser.parse_args(argv)

    try:
        server = TranscriptViewerServer(
            ("127.0.0.1", arguments.port),
            load_config(arguments.config),
            arguments.source,
            arguments.limit,
        )
    except (OSError, TypeError, ValueError, oracledb.Error) as error:
        print(f"Could not start transcript viewer: {error}", file=sys.stderr)
        return 1

    url = f"http://{server.server_address[0]}:{server.server_address[1]}/"
    print(f"Transcript viewer: {url}")
    if not arguments.no_browser:
        webbrowser.open(url)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
