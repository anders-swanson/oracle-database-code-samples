---
name: python-oracle/src/python_oracle/database
description: Native Python vector search sample for Oracle AI Database using python-oracledb.
tags:
  - AI
  - Database
  - python
  - Vector Search
blog_post: "https://andersswanson.dev/2026/02/10/langchain-vs-diy-vector-search-with-oracle-ai-database/"
---

# Native Python Vector Search Sample

This folder contains a native Python vector-search sample for Oracle AI Database using `python-oracledb` and the OpenAI embeddings API. The program creates a `sample_vectors` table, builds a vector index, inserts embeddings, and runs a similarity search query with `vector_distance`.

The sample file is [`vector_search_native.py`](./vector_search_native.py).

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
poetry run python src/python_oracle/database/vector_search_native.py
```

The script uses the local Testcontainers helper to start Oracle AI Database Free, creates the vector table and index, inserts sample text embeddings, and prints the top similarity-search match.
