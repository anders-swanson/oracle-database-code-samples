import getpass
import os

from langchain_community.vectorstores import OracleVS
from langchain_community.vectorstores.utils import DistanceStrategy
from langchain_openai import OpenAIEmbeddings

from src.python_oracle.testcontainers_sample.oracle_database_container import OracleDatabaseContainer


def main():
    dimensions = 1536

    # Load OpenAI API Key from environment
    if not os.getenv("OPENAI_API_KEY"):
        os.environ["OPENAI_API_KEY"] = getpass.getpass("Enter your OpenAI API key: ")

        # Create an OpenAI embedding model
    embeddings = OpenAIEmbeddings(
        model="text-embedding-3-small",
        dimensions=dimensions,
        api_key=os.getenv("OPENAI_API_KEY")
    )

    with OracleDatabaseContainer() as oracledb:
        conn = oracledb.get_connection()
        vector_store = OracleVS(conn,
                 embeddings,
                 table_name="sample_vectors",
                 distance_strategy=DistanceStrategy.COSINE)

        print("#### Embedding Data into Oracle AI Database ####")
        # Save some text into the vector db as embeddings
        vector_store.add_texts([
            "Reset the user’s password, clear MFA lockouts, and unlock the account after verifying identity.",
            "Reinstall the application, clear local cache/temp files, and validate the PDF upload workflow end-to-end.",
            "Submit an access request for the finance dashboard, confirm the required role, and route it to the user’s manager for approval.",
            "Upgrade/reinstall the VPN client, disable Wi‑Fi power-saving for the adapter, and collect logs to confirm the disconnect cause.",
            "Review the workload needs, then recommend object vs. block storage and share the internal storage standards/decision guide.",
        ])

        # Verify the vectors are persisted in the database
        cursor = oracledb.get_connection().cursor()
        print("#### Display Embedded Data ####:")
        for row in cursor.execute("SELECT id, text, metadata, embedding FROM sample_vectors"):
            if row is None:
                print("No result from query!")
            print(f"id (binary): {row[0]}, text: {row[1]}, metadata: {row[2]}, embedding: vector[{len(row[3])}]")


        print("#### Similarity Search ####")
        query = "My VPN keeps disconnecting every few minutes when I’m on Wi‑Fi, but it stays connected on Ethernet. Can you fix it?"
        print(f"Search query: '{query}'")
        documents = vector_store.similarity_search(query, k=1)

        print("#### Top Query Match ####")
        for doc in documents:
            print(doc.page_content)

if __name__ == "__main__":
    main()


