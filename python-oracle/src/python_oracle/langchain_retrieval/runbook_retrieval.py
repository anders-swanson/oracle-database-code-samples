from __future__ import annotations

import re
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import oracledb
from langchain_core.documents import Document
from langchain_core.embeddings import Embeddings
from langchain_core.messages import AIMessage, HumanMessage
from langchain_core.outputs import Generation
from langchain_oracledb.cache import OracleSemanticCache
from langchain_oracledb.chat_message_histories import OracleChatMessageHistory
from langchain_oracledb.document_loaders import OracleDocLoader, OracleTextSplitter
from langchain_oracledb.retrievers import OracleTextSearchRetriever
from langchain_oracledb.vectorstores import DistanceStrategy, OracleVS
from sentence_transformers import SentenceTransformer

from src.python_oracle.testcontainers_sample.oracle_database_container import OracleDatabaseContainer


ORACLE_FREE_FULL_IMAGE = "gvenzl/oracle-free:23.26.3-full-faststart"
EMBEDDING_MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2"
SOURCE_TABLE = "LANGCHAIN_RUNBOOK_SOURCES"
VECTOR_TABLE = "langchain_runbook_chunks"
CACHE_TABLE = "langchain_runbook_cache"
HISTORY_TABLE = "langchain_runbook_messages"
SESSION_ID = "vpn-support-demo"
LLM_CACHE_KEY = "all-minilm-runbook-answer-v1"
SETUP_SQL_FILE = Path(__file__).with_name("setup_runbooks.sql")
SQLPLUS_SEPARATOR = re.compile(r"(?m)^\s*/\s*$")


@dataclass(frozen=True)
class RunbookHit:
    runbook_id: int
    title: str
    product: str
    source: str
    rank: int
    score: float
    excerpt: str


@dataclass(frozen=True)
class QuestionResult:
    question: str
    answer: str
    cache_hit: bool
    semantic_hits: list[RunbookHit]
    keyword_hits: list[RunbookHit]
    fused_hit: RunbookHit
    history_count: int


@dataclass(frozen=True)
class SampleResult:
    source_count: int
    chunk_count: int
    first_answer: QuestionResult
    cached_answer: QuestionResult


@dataclass
class RankAccumulator:
    hit: RunbookHit
    score: float = 0.0


class AllMiniLMEmbeddings(Embeddings):
    """LangChain embeddings backed by sentence-transformers all-MiniLM."""

    def __init__(self, model_name: str = EMBEDDING_MODEL_NAME) -> None:
        self.model_name = model_name
        self.model = _load_sentence_transformer(model_name)

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return self._encode(texts)

    def embed_query(self, text: str) -> list[float]:
        return self._encode([text])[0]

    def _encode(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        vectors = self.model.encode(
            texts,
            convert_to_numpy=True,
            normalize_embeddings=True,
            show_progress_bar=False,
        )
        return [[float(value) for value in vector] for vector in vectors]


@lru_cache(maxsize=4)
def _load_sentence_transformer(model_name: str) -> SentenceTransformer:
    return SentenceTransformer(model_name)


def main() -> None:
    print("### Starting Oracle AI Database Free (container) ###")
    with OracleDatabaseContainer(image=ORACLE_FREE_FULL_IMAGE) as db, db.get_connection() as conn:
        print("### Oracle AI Database Free running ###\n")
        result = run_sample(conn)
        print_sample_result(result)


def run_sample(conn: oracledb.Connection) -> SampleResult:
    embeddings = AllMiniLMEmbeddings()
    vector_store = create_vector_store(conn, embeddings)
    run_sql_script(conn, SETUP_SQL_FILE)
    source_documents = load_source_documents(conn)
    chunk_ids = add_documents_to_vector_store(conn, vector_store, source_documents)

    question = (
        "My VPN disconnects every few minutes on Wi-Fi, "
        "but it stays connected on Ethernet. What should I try?"
    )
    first_answer = answer_question(
        conn,
        vector_store,
        question,
        embeddings=embeddings,
        product_filter="network",
    )
    cached_answer = answer_question(
        conn,
        vector_store,
        question,
        embeddings=embeddings,
        product_filter="network",
    )

    return SampleResult(
        source_count=len(source_documents),
        chunk_count=len(chunk_ids),
        first_answer=first_answer,
        cached_answer=cached_answer,
    )


def run_sql_script(conn: oracledb.Connection, script_file: Path) -> None:
    with conn.cursor() as cursor:
        for statement in SQLPLUS_SEPARATOR.split(script_file.read_text(encoding="utf-8")):
            if statement := statement.strip():
                cursor.execute(statement)
    conn.commit()


def load_source_documents(conn: oracledb.Connection) -> list[Document]:
    loader = OracleDocLoader(
        conn=conn,
        params={
            "owner": conn.username,
            "tablename": SOURCE_TABLE,
            "colname": "BODY",
            "mdata_cols": ["RUNBOOK_ID", "TITLE", "PRODUCT"],
        },
    )
    return [_normalize_document(document) for document in loader.load()]


def build_vector_store(
    conn: oracledb.Connection,
    source_documents: list[Document],
    embeddings: Embeddings | None = None,
) -> tuple[OracleVS, list[str]]:
    vector_store = create_vector_store(conn, embeddings)
    chunk_ids = add_documents_to_vector_store(conn, vector_store, source_documents)
    return vector_store, chunk_ids


def create_vector_store(
    conn: oracledb.Connection,
    embeddings: Embeddings | None = None,
) -> OracleVS:
    embedding_model = embeddings or AllMiniLMEmbeddings()
    return OracleVS(
        conn,
        embedding_model,
        table_name=VECTOR_TABLE,
        distance_strategy=DistanceStrategy.COSINE,
        mutate_on_duplicate=True,
    )


def add_documents_to_vector_store(
    conn: oracledb.Connection,
    vector_store: OracleVS,
    source_documents: list[Document],
) -> list[str]:
    splitter = OracleTextSplitter(
        conn=conn,
        params={"by": "words", "max": 30, "split": "sentence", "normalize": "all"},
    )
    ids = [str(document.metadata["runbook_id"]) for document in source_documents]
    return vector_store.add_documents(
        source_documents,
        text_splitter=splitter,
        ids=ids,
        add_chunk_metadata=True,
    )


def answer_question(
    conn: oracledb.Connection,
    vector_store: OracleVS,
    question: str,
    *,
    embeddings: Embeddings | None = None,
    product_filter: str | None = None,
) -> QuestionResult:
    embedding_model = embeddings or AllMiniLMEmbeddings()
    cache = OracleSemanticCache(
        conn,
        embedding_model,
        table_name=CACHE_TABLE,
        score_threshold=0.001,
    )
    cached_generations = cache.lookup(question, LLM_CACHE_KEY) or []
    cache_hit = bool(cached_generations)

    semantic_hits = semantic_search(vector_store, question, product_filter=product_filter)
    keyword_hits = keyword_search(vector_store, question)
    fused_hit = fuse_hits(semantic_hits, keyword_hits)

    answer = cached_generations[0].text if cached_generations else build_answer(question, fused_hit)
    if not cached_generations:
        cache.update(question, LLM_CACHE_KEY, [Generation(text=answer)])

    history = OracleChatMessageHistory(
        SESSION_ID,
        client=conn,
        table_name=HISTORY_TABLE,
    )
    if not cache_hit:
        history.add_messages([HumanMessage(content=question), AIMessage(content=answer)])

    return QuestionResult(
        question=question,
        answer=answer,
        cache_hit=cache_hit,
        semantic_hits=semantic_hits,
        keyword_hits=keyword_hits,
        fused_hit=fused_hit,
        history_count=len(history.messages),
    )


def semantic_search(
    vector_store: OracleVS,
    question: str,
    *,
    product_filter: str | None = None,
    k: int = 4,
) -> list[RunbookHit]:
    metadata_filter = {"product": {"$eq": product_filter}} if product_filter else None
    documents_with_scores = vector_store.similarity_search_with_score(
        question,
        k=k,
        filter=metadata_filter,
    )
    return [
        _document_hit(document, score, "semantic", rank)
        for rank, (document, score) in enumerate(documents_with_scores, start=1)
    ]


def keyword_search(vector_store: OracleVS, question: str, k: int = 4) -> list[RunbookHit]:
    retriever = OracleTextSearchRetriever(
        vector_store=vector_store,
        k=k,
        return_scores=True,
    )
    return [
        _document_hit(document, float(document.metadata.get("score", 0)), "keyword", rank)
        for rank, document in enumerate(retriever.invoke(question), start=1)
    ]


def fuse_hits(semantic_hits: list[RunbookHit], keyword_hits: list[RunbookHit]) -> RunbookHit:
    if not semantic_hits and not keyword_hits:
        raise RuntimeError("No runbook matched the question.")

    by_runbook: dict[int, RankAccumulator] = {}
    for hit in [*semantic_hits, *keyword_hits]:
        accumulator = by_runbook.setdefault(hit.runbook_id, RankAccumulator(hit))
        accumulator.score += 1.0 / (hit.rank + 1)

    return max(by_runbook.values(), key=lambda entry: entry.score).hit


def build_answer(question: str, runbook: RunbookHit) -> str:
    return (
        f"For: {question}\n"
        f"Use runbook: {runbook.title}.\n"
        f"Why: it matches the {runbook.product} product area and says to {runbook.excerpt}"
    )


def print_sample_result(result: SampleResult) -> None:
    first = result.first_answer
    cached = result.cached_answer
    print("#### Loaded runbooks into Oracle AI Database ####")
    print(f"Source runbooks: {result.source_count}")
    print(f"Vector chunks:   {result.chunk_count}\n")

    print("#### Retrieval ####")
    print(f"Question:      {first.question}")
    print(f"Semantic top:  {first.semantic_hits[0].title}")
    print(f"Keyword top:   {first.keyword_hits[0].title}")
    print(f"Fused top:     {first.fused_hit.title}\n")

    print("#### Response Persistence ####")
    print(first.answer)
    print(f"\nChat history messages: {first.history_count}")
    print(f"Second lookup used OracleSemanticCache: {cached.cache_hit}")


def _normalize_document(document: Document) -> Document:
    metadata = document.metadata
    return Document(
        page_content=document.page_content,
        metadata={
            "runbook_id": int(metadata["RUNBOOK_ID"]),
            "title": str(metadata["TITLE"]),
            "product": str(metadata["PRODUCT"]),
        },
    )


def _document_hit(document: Document, score: float, source: str, rank: int) -> RunbookHit:
    metadata = document.metadata
    return RunbookHit(
        runbook_id=int(metadata["runbook_id"]),
        title=str(metadata["title"]),
        product=str(metadata["product"]),
        source=source,
        rank=rank,
        score=float(score),
        excerpt=_excerpt(document.page_content),
    )


def _excerpt(text: str, limit: int = 130) -> str:
    collapsed = " ".join(text.split())
    if len(collapsed) <= limit:
        return collapsed
    return collapsed[: limit - 3].rstrip() + "..."


if __name__ == "__main__":
    main()
