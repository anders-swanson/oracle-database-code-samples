# Typescript Oracle Database Samples

The following code samples use the open-source [node-oracledb driver](https://node-oracledb.readthedocs.io/en/latest/user_guide/introduction.html) with [Oracle Database Free](https://andersswanson.dev/2025/05/22/oracle-database-for-free/).

This module uses Typescript and Vitest to build and test examples. It is recommended to use the latest version of node and run `npm i` before running any of the examples.


| Example program                                                    | Description                                                                                                                                                                                                          |
|--------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [testcontainers](src/testcontainers/oracle_database_container.ts)  | Spin up an Oracle Database Free container with [testcontainers](https://testcontainers.com/modules/oracle-free/?language=python) from within a Typescript project or test. Run with `npm run testcontainers-example` |
| [TxEventQ PL/SQL Producer/Consumer](./src/txeventq/SQLProducer.ts) | PL/SQL Producer/Consumer using Oracle Database Transactional Event Queues. Run sample with `npm run sql-producer-consumer`                                                                                           |

