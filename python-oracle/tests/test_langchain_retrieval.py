import unittest
from contextlib import suppress

from langchain_core.messages import AIMessage, HumanMessage
from langchain_oracledb.chat_message_histories import OracleChatMessageHistory
from langchain_oracledb.vectorstores import DistanceStrategy, OracleVS

from src.python_oracle.langchain_retrieval.runbook_retrieval import (
    AllMiniLMEmbeddings,
    HISTORY_TABLE,
    ORACLE_FREE_FULL_IMAGE,
    SESSION_ID,
    VECTOR_TABLE,
    semantic_search,
    run_sample,
)
from src.python_oracle.testcontainers_sample.oracle_database_container import OracleDatabaseContainer


EXPECTED_RUNBOOK = "Stabilize VPN over Wi-Fi"


class LangChainRetrievalCompositionTest(unittest.TestCase):
    oracle_container: OracleDatabaseContainer

    @classmethod
    def setUpClass(cls) -> None:
        cls.oracle_container = OracleDatabaseContainer(image=ORACLE_FREE_FULL_IMAGE)
        cls.oracle_container.start()

    @classmethod
    def tearDownClass(cls) -> None:
        with suppress(Exception):
            cls.oracle_container.stop()

    def test_composes_langchain_oracle_components(self) -> None:
        with self.oracle_container.get_connection() as conn:
            result = run_sample(conn)

            self.assertEqual(4, result.source_count)
            self.assertGreater(result.chunk_count, result.source_count)

            first_answer = result.first_answer
            self.assertFalse(first_answer.cache_hit)
            self.assertEqual(EXPECTED_RUNBOOK, first_answer.semantic_hits[0].title)
            self.assertEqual(EXPECTED_RUNBOOK, first_answer.keyword_hits[0].title)
            self.assertEqual(EXPECTED_RUNBOOK, first_answer.fused_hit.title)
            self.assertIn(f"Use runbook: {EXPECTED_RUNBOOK}", first_answer.answer)

            cached_answer = result.cached_answer
            self.assertTrue(cached_answer.cache_hit)
            self.assertEqual(first_answer.answer, cached_answer.answer)
            self.assertEqual(2, cached_answer.history_count)

            history = OracleChatMessageHistory(
                SESSION_ID,
                client=conn,
                table_name=HISTORY_TABLE,
            )
            self.assertEqual(2, len(history.messages))
            self.assertIsInstance(history.messages[0], HumanMessage)
            self.assertIsInstance(history.messages[1], AIMessage)

            vector_store = OracleVS(
                conn,
                AllMiniLMEmbeddings(),
                table_name=VECTOR_TABLE,
                distance_strategy=DistanceStrategy.COSINE,
                mutate_on_duplicate=True,
            )
            storage_hits = semantic_search(
                vector_store,
                first_answer.question,
                product_filter="storage",
            )
            self.assertTrue(storage_hits)
            self.assertNotEqual(EXPECTED_RUNBOOK, storage_hits[0].title)
            self.assertTrue(all(hit.product == "storage" for hit in storage_hits))


if __name__ == "__main__":
    unittest.main()
