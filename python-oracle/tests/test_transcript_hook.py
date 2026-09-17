import io
import json
import tempfile
import unittest
from contextlib import suppress
from pathlib import Path
from unittest.mock import patch

from src.python_oracle.testcontainers_sample.oracle_database_container import (
    APP_USER,
    APP_USER_PASSWORD,
    OracleDatabaseContainer,
)
from src.python_oracle.transcript_hook.config import load_config
from src.python_oracle.transcript_hook.oracle_transcript_hook import run
from src.python_oracle.transcript_hook.transcript_viewer import (
    delete_transcript,
    fetch_transcript,
    fetch_transcripts,
    render_transcript,
)


class TranscriptHookIntegrationTest(unittest.TestCase):
    oracle_container: OracleDatabaseContainer

    @classmethod
    def setUpClass(cls) -> None:
        cls.oracle_container = OracleDatabaseContainer()
        cls.oracle_container.start()

    @classmethod
    def tearDownClass(cls) -> None:
        with suppress(Exception):
            cls.oracle_container.stop()

    def test_persists_and_updates_session_transcript(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            config_path = Path(directory) / "transcript-hook.yaml"
            config_path.write_text(
                """user: "${TEST_DB_USER}"
password: "${TEST_DB_PASSWORD}"
dsn: "${TEST_DB_DSN}"
""",
                encoding="utf-8",
            )
            transcript_path = Path(directory) / "transcript.jsonl"
            transcript_path.write_text('{"message":"first"}\n', encoding="utf-8")
            event_payload = {
                "session_id": "integration-session",
                "transcript_path": str(transcript_path),
                "cwd": "/workspace",
                "hook_event_name": "Stop",
                "model": "sample-model",
            }
            dsn = (
                f"localhost:{self.oracle_container.get_exposed_port(1521)}/freepdb1"
            )
            environment = {
                "TEST_DB_USER": APP_USER,
                "TEST_DB_PASSWORD": APP_USER_PASSWORD,
                "TEST_DB_DSN": dsn,
            }
            with patch.dict("os.environ", environment):
                config = load_config(config_path)
                output = io.StringIO()
                run(
                    config_path,
                    stdin=io.StringIO(json.dumps(event_payload)),
                    stdout=output,
                )

                source = "codex"
                self.assertEqual(APP_USER, config["user"])
                self.assertEqual("{}\n", output.getvalue())

                with self.oracle_container.get_connection() as connection:
                    with connection.cursor() as cursor:
                        cursor.execute(
                            """select id
                               from agent_transcripts
                               where agent_source = :1 and session_id = :2""",
                            [source, event_payload["session_id"]],
                        )
                        transcript_id = cursor.fetchone()[0]

                    transcript_path.write_text(
                        """{"type":"response_item","payload":{"type":"message","role":"user","content":[{"type":"input_text","text":"How is the hook doing?"}]}}
{"type":"response_item","payload":{"type":"message","role":"assistant","content":[{"type":"output_text","text":"It is storing transcripts."}]}}
{"type":"response_item","payload":{"type":"function_call","name":"run_tests"}}
""",
                        encoding="utf-8",
                    )
                    run(
                        config_path,
                        stdin=io.StringIO(json.dumps(event_payload)),
                        stdout=io.StringIO(),
                    )

                    with connection.cursor() as cursor:
                        cursor.execute(
                            """select id, transcript
                               from agent_transcripts
                               where agent_source = :1 and session_id = :2""",
                            [source, event_payload["session_id"]],
                        )
                        stored_id, stored_transcript = cursor.fetchone()
                        if hasattr(stored_transcript, "read"):
                            stored_transcript = stored_transcript.read()
                        cursor.execute(
                            """select count(*)
                               from agent_transcripts
                               where agent_source = :1 and session_id = :2""",
                            [source, event_payload["session_id"]],
                        )
                        row_count = cursor.fetchone()[0]

                    run(
                        config_path,
                        "another-agent",
                        stdin=io.StringIO(json.dumps(event_payload)),
                        stdout=io.StringIO(),
                    )
                    with connection.cursor() as cursor:
                        cursor.execute(
                            """select count(*), count(distinct id)
                               from agent_transcripts
                               where session_id = :1""",
                            [event_payload["session_id"]],
                        )
                        source_count, distinct_id_count = cursor.fetchone()

                    transcripts = fetch_transcripts(connection, source, limit=10)
                    viewer_transcript = fetch_transcript(connection, transcript_id)

                self.assertEqual(transcript_id, stored_id)
                self.assertIn('"It is storing transcripts."', stored_transcript)
                self.assertEqual(1, row_count)
                self.assertEqual(2, source_count)
                self.assertEqual(2, distinct_id_count)
                self.assertEqual([event_payload["session_id"]], [item.session_id for item in transcripts])
                self.assertIn('"It is storing transcripts."', viewer_transcript)
                rendered_transcript = render_transcript(viewer_transcript)
                self.assertIn('class="message user"', rendered_transcript)
                self.assertIn('How is the hook doing?', rendered_transcript)
                self.assertIn('class="message assistant"', rendered_transcript)
                self.assertIn('It is storing transcripts.', rendered_transcript)
                self.assertIn('function_call', rendered_transcript)

                with self.oracle_container.get_connection() as connection:
                    self.assertTrue(delete_transcript(connection, transcript_id))
                    self.assertFalse(delete_transcript(connection, transcript_id))

if __name__ == "__main__":
    unittest.main()
