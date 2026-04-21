# LangChain Vector Search Sample

This folder contains a LangChain-based vector-search sample for Oracle AI Database. It uses `langchain-oracledb` with `OpenAIEmbeddings` to persist sample texts into Oracle AI Database and then runs a similarity search with `OracleVS`.

The sample file is [`vector_search_sample.py`](./vector_search_sample.py).

## Prerequisites

- Python 3.13+
- Poetry
- Docker compatible environment
- `OPENAI_API_KEY` set in your environment, or available for interactive prompt entry

Install dependencies from the `python-oracle/` directory:

```bash
poetry install
```

## Run the sample

From the `python-oracle/` directory:

```bash
poetry run python src/python_oracle/langchain/vector_search_sample.py
```

The script starts Oracle AI Database Free with the local Testcontainers helper, creates an `OracleVS` vector store in `sample_vectors`, embeds sample texts, and prints the top similarity-search match for the query.
