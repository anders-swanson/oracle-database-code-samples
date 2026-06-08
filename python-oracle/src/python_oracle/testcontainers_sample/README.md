---
name: python-oracle/src/python_oracle/testcontainers_sample
description: Python Testcontainers sample for starting Oracle AI Database Free programmatically.
tags:
  - Database
  - python
  - Testcontainers
  - oraclefree
blog_post: "https://andersswanson.dev/2025/09/11/test-python-applications-with-oracle-database-free-using-testcontainers/"
---

# Testcontainers with Oracle AI Database

This module shows you how to run an Oracle AI Database Free container image using [Testcontainers (python)](https://github.com/testcontainers/testcontainers-python).

The [OracleDatabaseContainer](oracle_database_container.py) class implements a Testcontainers database container for Oracle AI Database Free, using the `gvenzl/oracle-free:23.26.2-slim-faststart` image by default.

The [OrdsContainer](ords_container.py) class implements a reusable ORDS Testcontainers helper. It configures the ORDS image with an Oracle AI Database connection string and admin password, exposes HTTP, HTTPS, and MongoDB API ports, and can enable one or more schemas after ORDS starts.

The [sample program](testcontainers_sample.py) uses spins up an Oracle AI Database container, runs a query, and exits.

The container exists ephemerally for the runtime of the program, and is cleaned up after.

You can use these scripts to run Oracle AI Database Free containers in your programs and tests.

### Run the program

```bash
python python_oracle/testcontainers_sample/testcontainers_sample.py
```

If you run the [sample program](testcontainers_sample.py), you should see output similar to the following, indicating a container was started, Testcontainers waited for it to be ready, and the output of the `select * from V$VERSION` SQL query:

```
Pulling image gvenzl/oracle-free:23.26.2-slim-faststart
Container started: 164861e88ad6
Waiting to be ready...
Waiting to be ready...
Waiting to be ready...
Waiting to be ready...
Waiting to be ready...
Waiting to be ready...
Waiting to be ready...
('Oracle Database 23ai Free Release 23.26.2.0.0 - Develop, Learn, and Run for Free', 'Oracle Database 23ai Free Release 23.26.2.0.0 - Develop, Learn, and Run for Free\nVersion 23.26.2.0.0.0', 'Oracle Database 26ai Free Release 23.26.2.0.0 - Develop, Learn, and Run for Free', 0)
```
