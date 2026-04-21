# Python Oracle AI Database Samples

The following code samples use the open-source [python-oracledb driver](https://python-oracledb.readthedocs.io/en/latest/) with [Oracle AI Database Free](https://andersswanson.dev/2025/05/22/oracle-database-for-free/).

This module uses [poetry](https://python-poetry.org/) with the `pyproject.toml` format to manage dependencies and virtual environments. It is recommended to install poetry before running samples.

### Active the virtual environment (venv) with poetry

```bash
# Install dependencies
poetry install
# Print the command to active a virtual environment
poetry env activate
```

You may run the samples from this directory once the venv is activated.

| Example program                                                                               | Description                                                                                                                                                   | Command                                                                                                   |
|-----------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| [langchain vector search](src/python_oracle/langchain/vector_search_sample.py)                | Example using the popular LangChain & Oracle AI Database as a vector store for similarity search.                                                             | `export OPENAI_API_KEY=<Your OpenAI API Key> python src/python_oracle/langchain/vector_search_sample.py ` |
| [native Oracle AI Database vector search](src/python_oracle/database/vector_search_native.py) | Native vector search example using Oracle AI Database as a vector store for similarity search. Just Python and SQL :)                                         | `export OPENAI_API_KEY=<Your OpenAI API Key> python src/python_oracle/database/vector_search_native.py`   |                                                                                                           |
| [testcontainers](src/python_oracle/testcontainers_sample/README.md)                           | Spin up an Oracle AI Database Free container with [testcontainers](https://testcontainers.com/modules/oracle-free/?language=python) from within a Python script. | `python python_oracle/testcontainers_sample/testcontainers_sample.py`                                     |
