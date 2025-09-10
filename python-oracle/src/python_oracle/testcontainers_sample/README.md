# Testcontainers with Oracle Database

This module shows you how to run an Oracle Database Free container image using [Testcontainers (python)](https://github.com/testcontainers/testcontainers-python).

The [OracleDatabaseContainer](oracle_database_container.py) class implements a Testcontainers database container for Oracle Database Free, using the `gvenzl/oracle-free:23.9-slim-faststart` image by default.

The [sample program](testcontainers_sample.py) uses spins up an Oracle Database container, runs a query, and exits.

The container exists ephemerally for the runtime of the program, and is cleaned up after.

You can use these scripts to run Oracle Database Free containers in your programs and tests.

### Sample output

If you run the [sample program](testcontainers_sample.py), you should see output similar to the following, indicating a container was started, Testcontainers waited for it to be ready, and the output of the `select * from V$VERSION` SQL query:

```
Pulling image gvenzl/oracle-free:23.9-slim-faststart
Container started: 164861e88ad6
Waiting to be ready...
Waiting to be ready...
Waiting to be ready...
Waiting to be ready...
Waiting to be ready...
Waiting to be ready...
Waiting to be ready...
('Oracle Database 23ai Free Release 23.0.0.0.0 - Develop, Learn, and Run for Free', 'Oracle Database 23ai Free Release 23.0.0.0.0 - Develop, Learn, and Run for Free\nVersion 23.9.0.25.07', 'Oracle Database 23ai Free Release 23.0.0.0.0 - Develop, Learn, and Run for Free', 0)
```
