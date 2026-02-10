import getpass
import os
import array
from typing import Any

import oracledb
from openai import OpenAI

from src.python_oracle.testcontainers_sample.oracle_database_container import OracleDatabaseContainer

DIMENSIONS = 1536

CREATE_TABLE = f"""
create table if not exists sample_vectors (
    id        number generated always as identity primary key,
    content   clob,
    embedding vector({DIMENSIONS}, FLOAT64) 
        annotations(Distance 'COSINE', IndexType 'IVF')
)"""

CREATE_INDEX = """
create vector index if not exists idx_sample_vectors on sample_vectors (embedding)
    organization neighbor partitions
    distance COSINE
    with target accuracy 95
    parameters (
        type IVF, 
        neighbor partitions 10
    )"""

INSERT_TEXT_EMBEDDING = """
insert into sample_vectors 
    (content, embedding) values (:1, :2)"""


SIMILARITY_SEARCH_QUERY = """
select id, content, embedding, (1 - vector_distance(embedding, :1, COSINE)) as score
from sample_vectors
order by score desc
fetch first 1 rows only"""

def main():
    # Load OpenAI API Key from environment
    if not os.getenv("OPENAI_API_KEY"):
        os.environ["OPENAI_API_KEY"] = getpass.getpass("Enter your OpenAI API key: ")
    openai = OpenAI()

    with OracleDatabaseContainer() as oracledb:
        conn = oracledb.get_connection()
        cursor = conn.cursor()

        print("Creating table if not exists sample_vectors")
        cursor.execute(CREATE_TABLE)
        print("Creating index if not exists idx_sample_vectors")
        cursor.execute(CREATE_INDEX)
        conn.commit()

        print("Loading data into sample_vectors table")
        insert_text_embeddings(conn, openai, texts=[
            "Reset the user’s password, clear MFA lockouts, and unlock the account after verifying identity.",
            "Reinstall the application, clear local cache/temp files, and validate the PDF upload workflow end-to-end.",
            "Submit an access request for the finance dashboard, confirm the required role, and route it to the user’s manager for approval.",
            "Upgrade/reinstall the VPN client, disable Wi‑Fi power-saving for the adapter, and collect logs to confirm the disconnect cause.",
            "Review the workload needs, then recommend object vs. block storage and share the internal storage standards/decision guide.",
        ])

        print("\n#### Display Embedded Data ####:")
        for row in cursor.execute('SELECT id, content, embedding FROM sample_vectors'):
            if row is None:
                print("No result from query!")
            print(f"id: {row[0]}, content: {row[1]}, embedding: vector[{len(row[2])}]")

        print("#### Similarity Search ####")
        query = "My VPN keeps disconnecting every few minutes when I’m on Wi‑Fi, but it stays connected on Ethernet. Can you fix it?"
        print(f"Search query: '{query}'")
        result = similarity_search(conn, openai, query)
        if not result:
            print("No result from similarity search query!")
        else:
            print("#### Top Query Match ####")
            print(f"id: {result[0]}, content: {result[1]}, embedding: vector[{len(result[2])}]")


def embed_text(openai: OpenAI, text: str) -> tuple[str, array.array[float]]:
    embedding = openai.embeddings.create(
        input=text,
        model="text-embedding-3-small"
    ).data[0].embedding
    # python array's "d" typecode corresponds to 64-bit floating point,
    # which is what we use in our sample_vectors table.
    vector = array.array("d", embedding)

    return text, vector

def insert_text_embeddings(conn: oracledb.Connection, openai: OpenAI, texts: list[str]):
    # load embeddings: you may also do this asynchronously
    # as it's a series of network calls.
    data = [embed_text(openai, x) for x in texts]

    # save embeddings as batch
    with conn.cursor() as cursor:
        cursor.executemany(
            INSERT_TEXT_EMBEDDING,
            data
        )
        conn.commit()

def similarity_search(conn: oracledb.Connection, openai: OpenAI, query: str) -> Any:
    _, vector = embed_text(openai, query)
    with conn.cursor() as cursor:
        for row in cursor.execute(
                statement=SIMILARITY_SEARCH_QUERY,
                parameters=(vector,) # expects a tuple!
        ):
            return row

    return None

if __name__ == "__main__":
    main()
