# Python Oracle AI Database Samples

The following code samples use the open-source [python-oracledb driver](https://python-oracledb.readthedocs.io/en/latest/) with [Oracle AI Database Free](https://andersswanson.dev/2025/05/22/oracle-database-for-free/).

This module uses [uv](https://docs.astral.sh/uv/) with `pyproject.toml` and `uv.lock` to manage dependencies and virtual environments.

## Setup

Run commands from the `python-oracle` directory.

```bash
uv sync
```

Run samples directly with `uv run`; uv creates and uses `.venv` automatically.

## Testing

Run the ORDS Testcontainers integration test with:

```bash
uv run python -m unittest tests.test_ords_container -v
```

## Samples

| Example program                                                                                                                                                                      | Description                                                                                                                                     | Command                                                                                                      |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| [Agent transcript hook](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/python-oracle/src/python_oracle/transcript_hook/README.md)                                           | Persist complete Codex session transcripts in Oracle AI Database from a Python lifecycle hook.                                                   | See the sample README for Codex hook configuration.                                                          |
| [SQLcl MCP agent](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/python-oracle/src/python_oracle/mcp_agent/README.md)                                                        | Natural-language SQL agent using LangChain, SQLcl MCP, and Oracle AI Database.                                                                  | `uv run python src/python_oracle/mcp_agent/sqlcl_mcp_agent.py --connection python_mcp`                               |
| [LangGraph persistence](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/python-oracle/src/python_oracle/langgraph_persistence/README.md)                                        | Durable LangGraph travel approval workflow using Oracle AI Database checkpoints, store, Testcontainers, and OCI chat.                            | `uv run python src/python_oracle/langgraph_persistence/travel_approval_graph.py`                                     |
| [LangChain Retrieval](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/python-oracle/src/python_oracle/langchain_retrieval/README.md)                                            | Compose `langchain-oracledb` loaders, chunking, vector search, Oracle Text retrieval, chat history, and semantic cache.                         | `uv run python src/python_oracle/langchain_retrieval/runbook_retrieval.py`                                           |
| [LangChain vector search](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/python-oracle/src/python_oracle/langchain/vector_search_sample.py)                                    | Use LangChain and Oracle AI Database as a vector store for similarity search.                                                                   | `OPENAI_API_KEY=<your-openai-api-key> uv run python src/python_oracle/langchain/vector_search_sample.py`             |
| [Native Oracle AI Database vector search](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/python-oracle/src/python_oracle/database/vector_search_native.py)                     | Native vector search with Oracle AI Database using Python and SQL.                                                                              | `OPENAI_API_KEY=<your-openai-api-key> uv run python src/python_oracle/database/vector_search_native.py`              |
| [JSON Oracle Text search](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/python-oracle/src/python_oracle/full_text_search/README.md)                                           | Store JSON documents in Oracle AI Database and query them with Oracle Text ranking, proximity search, and structured JSON filters.              | `uv run python src/python_oracle/full_text_search/json_text_search.py`                                               |
| [Testcontainers](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/python-oracle/src/python_oracle/testcontainers_sample/README.md)                                               | Spin up an Oracle AI Database Free container with [Testcontainers for Python](https://testcontainers.com/modules/oracle-free/?language=python). | `uv run python src/python_oracle/testcontainers_sample/testcontainers_sample.py`                                     |
