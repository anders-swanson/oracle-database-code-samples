from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any

import oracledb

from src.python_oracle.testcontainers_sample.oracle_database_container import OracleDatabaseContainer


ORACLE_FREE_IMAGE = "gvenzl/oracle-free:23.26.1-full-faststart"

DOCUMENTS = [
    {
        "title": "Oracle Text for JSON Search",
        "summary": "A hands-on lab for building ranked Oracle Text search over JSON knowledge entries.",
        "body": "The guide walks through loading API notes as JSON, creating a search index, and testing keyword plus NEAR queries for phrase-style retrieval.",
        "category": "GUIDE",
        "author": "Ava",
    },
    {
        "title": "Storefront Catalog Migration Notes",
        "summary": "Merchandising teams moved Oracle product blurbs into JSON documents for seasonal relevance tuning.",
        "body": "Analysts compared attribute-heavy catalog entries, synonym lists, and faceted descriptions before publishing the new storefront data set.",
        "category": "REFERENCE",
        "author": "Ben",
    },
    {
        "title": "Operations Bulletin for Incident Review",
        "summary": "Weekly notes for the Oracle support desk, covering query latency, alert thresholds, and search cluster maintenance.",
        "body": "The bulletin captures rollout timing, pager ownership, and runbook changes for overnight indexing checks.",
        "category": "OPERATIONS",
        "author": "Cara",
    },
    {
        "title": "Field Guide to Queue Backpressure",
        "summary": "Architecture notes on consumers, dead-letter queues, and throughput limits during traffic spikes.",
        "body": "This piece focuses on event delivery behavior, retry budgets, and telemetry rather than document retrieval or text indexing.",
        "category": "ARCHITECTURE",
        "author": "Drew",
    },
]

CREATE_TABLE = """
create table if not exists json_text_documents (
    id number generated always as identity primary key,
    search_document json not null
)"""

CREATE_SEARCH_INDEX = """
create search index if not exists json_text_documents_search_idx
on json_text_documents (search_document)
for json
parameters ('sync (on commit) search_on text include ($.title, $.summary, $.body)')
"""

DELETE_DOCUMENTS = "delete from json_text_documents"

INSERT_DOCUMENT = """
insert into json_text_documents (search_document)
values (:search_document)
"""

KEYWORD_SEARCH = """
select id,
       search_document,
       score(1) as score
from json_text_documents
where json_textcontains(search_document, '$', :query, 1)
order by score(1) desc, id
"""

PROXIMITY_SEARCH = """
select id,
       search_document,
       score(2) as score
from json_text_documents
where json_textcontains(search_document, '$', :query, 2)
order by score(2) desc, id
"""

FILTERED_SEARCH = """
select id,
       search_document,
       score(3) as score
from json_text_documents
where json_textcontains(search_document, '$', :query, 3)
  and json_value(search_document, '$.category' returning varchar2(30)) = :category
  and json_value(search_document, '$.author' returning varchar2(30)) = :author
order by score(3) desc, id
"""


@dataclass(frozen=True)
class SearchHit:
    document_id: int
    document: dict[str, Any]
    score: int


def main() -> None:
    with OracleDatabaseContainer(image=ORACLE_FREE_IMAGE) as db, db.get_connection() as conn:
        run_sample(conn)


def run_sample(conn: oracledb.Connection) -> None:
    reset_schema(conn)
    insert_documents(conn, DOCUMENTS)

    print(f"Loaded {len(DOCUMENTS)} JSON documents into JSON_TEXT_DOCUMENTS and committed them.")
    print("The Oracle Text JSON search index can now rank matching documents.")
    print()

    keyword_hits = search(conn, KEYWORD_SEARCH, {"query": "oracle"})
    proximity_hits = search(conn, PROXIMITY_SEARCH, {"query": "NEAR((json, search), 3)"})
    filtered_hits = search(
        conn,
        FILTERED_SEARCH,
        {"query": "oracle", "category": "GUIDE", "author": "Ava"},
    )

    validate_expected_results(keyword_hits, proximity_hits, filtered_hits)

    print_results(
        "Keyword search",
        "oracle",
        "Find documents whose indexed JSON text contains the token 'oracle'.",
        keyword_hits,
    )
    print_results(
        "Proximity search",
        "NEAR((json, search), 3)",
        "Find documents where 'json' and 'search' appear within 3 tokens of each other.",
        proximity_hits,
    )
    print_results(
        "Mixed search",
        "oracle with category = GUIDE and author = Ava",
        "Find documents whose indexed JSON text contains 'oracle', then keep only JSON documents where category is GUIDE and author is Ava.",
        filtered_hits,
    )


def reset_schema(conn: oracledb.Connection) -> None:
    with conn.cursor() as cursor:
        cursor.execute(CREATE_TABLE)
        cursor.execute(CREATE_SEARCH_INDEX)
        cursor.execute(DELETE_DOCUMENTS)
    conn.commit()


def insert_documents(conn: oracledb.Connection, documents: list[dict[str, Any]]) -> None:
    with conn.cursor() as cursor:
        cursor.setinputsizes(search_document=oracledb.DB_TYPE_JSON)
        cursor.executemany(
            INSERT_DOCUMENT,
            [{"search_document": document} for document in documents],
        )
    conn.commit()


def search(conn: oracledb.Connection, sql: str, parameters: dict[str, Any]) -> list[SearchHit]:
    hits: list[SearchHit] = []
    with conn.cursor() as cursor:
        for row in cursor.execute(sql, parameters):
            hits.append(
                SearchHit(
                    document_id=int(row[0]),
                    document=decode_json(row[1]),
                    score=int(row[2]),
                )
            )
    return hits


def decode_json(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    if isinstance(value, str):
        return json.loads(value)
    if isinstance(value, bytes):
        return json.loads(value.decode("utf-8"))
    if hasattr(value, "read"):
        return decode_json(value.read())
    raise TypeError(f"Unsupported JSON value returned by python-oracledb: {type(value).__name__}")


def validate_expected_results(
    keyword_hits: list[SearchHit],
    proximity_hits: list[SearchHit],
    filtered_hits: list[SearchHit],
) -> None:
    require(len(DOCUMENTS) == 4, f"Expected 4 JSON documents but found {len(DOCUMENTS)}")
    require(len(keyword_hits) == 3, f"Expected 3 keyword hits but found {len(keyword_hits)}")
    assert_top_title(keyword_hits, "Oracle Text for JSON Search", "keyword search")
    require(len(proximity_hits) == 1, f"Expected 1 proximity hit but found {len(proximity_hits)}")
    assert_top_title(proximity_hits, "Oracle Text for JSON Search", "proximity search")
    require(len(filtered_hits) == 1, f"Expected 1 filtered hit but found {len(filtered_hits)}")
    assert_top_title(filtered_hits, "Oracle Text for JSON Search", "filtered search")


def assert_top_title(hits: list[SearchHit], expected_title: str, query_name: str) -> None:
    actual_title = hits[0].document["title"]
    require(actual_title == expected_title, (
        f"Expected first {query_name} hit to be {expected_title} but was {actual_title}"
    ))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def print_results(heading: str, expression: str, explanation: str, hits: list[SearchHit]) -> None:
    print(f'{heading} using json_textcontains(..., "{expression}")')
    print(explanation)
    print("Oracle Text SCORE is a relevance ranking for this query only.")
    print("A higher score means a stronger match in this result set. It is not a percentage.")

    if not hits:
        print("No documents matched this query.")
        print()
        return

    print(f"{len(hits)} document(s) matched. Results are ordered by descending SCORE.")
    for index, hit in enumerate(hits, start=1):
        print(
            f"{index}. {hit.document['title']} | "
            f"category={hit.document['category']} | "
            f"author={hit.document['author']} | "
            f"score={hit.score} | "
            f"{describe_score(index)}"
        )
    print()


def describe_score(index: int) -> str:
    if index == 1:
        return "top-ranked match for this query"
    return "less relevant than the top result for this query"


if __name__ == "__main__":
    main()
