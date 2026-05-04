# Python Oracle AI Database Samples

The following code samples use the open-source [python-oracledb driver](https://python-oracledb.readthedocs.io/en/latest/) with [Oracle AI Database Free](https://andersswanson.dev/2025/05/22/oracle-database-for-free/).

This module uses [Poetry](https://python-poetry.org/) with the `pyproject.toml` format to manage dependencies and virtual environments.

## Setup

Run commands from the `python-oracle` directory.

```bash
poetry install
poetry env activate
```

`poetry env activate` prints the command to activate the virtual environment. You can also run samples directly with `poetry run`.

## Samples

| Example program                                                                                                                                                                      | Description                                                                                                                                     | Command                                                                                                      |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| [SQLcl MCP agent](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/python-oracle/src/python_oracle/mcp_agent/README.md)                                      | Natural-language SQL agent using LangChain, SQLcl MCP, and Oracle AI Database.                                                                  | `poetry run python src/python_oracle/mcp_agent/sqlcl_mcp_agent.py --connection python_mcp`                   |
| [LangChain vector search](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/python-oracle/src/python_oracle/langchain/vector_search_sample.py)                | Use LangChain and Oracle AI Database as a vector store for similarity search.                                                                   | `OPENAI_API_KEY=<your-openai-api-key> poetry run python src/python_oracle/langchain/vector_search_sample.py` |
| [Native Oracle AI Database vector search](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/python-oracle/src/python_oracle/database/vector_search_native.py) | Native vector search with Oracle AI Database using Python and SQL.                                                                              | `OPENAI_API_KEY=<your-openai-api-key> poetry run python src/python_oracle/database/vector_search_native.py`  |
| [Testcontainers](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/python-oracle/src/python_oracle/testcontainers_sample/README.md)                           | Spin up an Oracle AI Database Free container with [Testcontainers for Python](https://testcontainers.com/modules/oracle-free/?language=python). | `poetry run python src/python_oracle/testcontainers_sample/testcontainers_sample.py`                         |
