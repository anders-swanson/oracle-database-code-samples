whenever sqlerror exit failure rollback

-- The image creates TESTUSER before running init scripts. Grant OKafka access as SYS.
@/lab/okafka.sql

-- Install the same schema used by the Testcontainers suite.
connect TESTUSER/"Welcome123#"@//localhost:1521/FREEPDB1
whenever sqlerror exit failure rollback
@/lab/db/schema.sql
exit
