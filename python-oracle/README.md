# Python Oracle Database Samples

The following code samples use the open-source [python-oracledb driver](https://python-oracledb.readthedocs.io/en/latest/) with [Oracle Database Free](https://andersswanson.dev/2025/05/22/oracle-database-for-free/).

This module uses [poetry](https://python-poetry.org/) to manage dependencies and virtual environments. It is recommended to install poetry before running samples.

### Active the virtual environment with poetry

```bash
# Install dependencies
poetry install
# Print the command to active a virtual environment
poetry env activate
```

| Example program                                                     | Description                                                                                                                                                   | Command                                                               |
|---------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| [testcontainers](src/python_oracle/testcontainers_sample/README.md) | Spin up an Oracle Database Free container with [testcontainers](https://testcontainers.com/modules/oracle-free/?language=python) from within a Python script. | `python python_oracle/testcontainers_sample/testcontainers_sample.py` |

