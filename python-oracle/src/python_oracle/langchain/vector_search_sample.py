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
        dimensions=dimensions
    )

    with OracleDatabaseContainer() as oracledb:
        conn = oracledb.get_connection()
        vector_store = OracleVS(conn,
                 embeddings,
                 table_name="sample_vectors",
                 distance_strategy=DistanceStrategy.COSINE)


if __name__ == "__main__":
    main()


