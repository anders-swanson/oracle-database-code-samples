# Testing And Troubleshooting

## Testcontainers Setup

The reference integration tests use:

- Java 21
- Maven Failsafe for `*IT.java`
- `gvenzl/oracle-free:23.26.2-slim-faststart`
- Test user `testuser` / `Welcome123#`
- `src/test/resources/ojdbc.properties`
- `src/test/resources/okafka.sql`

The test setup starts Oracle Free, copies `okafka.sql` into the container, runs it as SYSDBA, then builds OKafka properties with the mapped Oracle port.

## Required Grants

The reference `okafka.sql` grants the test user AQ and dynamic view access required by OKafka:

```sql
alter session set container=freepdb1;

grant aq_user_role to TESTUSER;
grant execute on dbms_aq to TESTUSER;
grant execute on dbms_aqadm to TESTUSER;
grant select on gv_$session to TESTUSER;
grant select on v_$session to TESTUSER;
grant select on gv_$instance to TESTUSER;
grant select on gv_$listener_network to TESTUSER;
grant select on SYS.DBA_RSRC_PLAN_DIRECTIVES to TESTUSER;
grant select on gv_$pdbs to TESTUSER;
grant select on user_queue_partition_assignment_table to TESTUSER;
exec dbms_aqadm.GRANT_PRIV_FOR_RM_PLAN('TESTUSER');
commit;
```

Keep these in sample/test bootstrap unless the target database user has already been provisioned.

## Useful Validation Commands

From the reference module:

```shell
mvn integration-test -Dit.test=OKafkaExampleIT
mvn integration-test -Dit.test=TransactionalProduceIT
mvn integration-test -Dit.test=TransactionalConsumeIT
mvn integration-test
```

In a multi-module repo, adapt with `-pl <module>` and the repo's existing Maven flags.

## Common Checks

- `TopicExistsException`: handle as success for idempotent tests and startup.
- Authentication failure: verify `security.protocol`, `bootstrap.servers` for PLAINTEXT, `tns.alias` for SSL, and that `oracle.net.tns_admin` points to the directory containing `ojdbc.properties` and wallet files.
- Consumer appears idle: verify topic name case, `group.id`, `auto.offset.reset=earliest` for tests, and that records were produced after topic creation.
- Rows not committed: verify `enable.auto.commit=false`, commit happens after processing, and transactional code uses `producer.getDBConnection()` or `consumer.getDBConnection()` rather than a separate JDBC connection.
- Spring shutdown noise: ensure exactly one owner closes each raw client. For polling consumers, let the polling thread close the consumer and keep the Spring bean `destroyMethod` empty.
