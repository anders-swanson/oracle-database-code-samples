# Authentication And Properties

## Base Properties

Use a shared base properties builder and copy it before adding admin, producer, or consumer options.

```java
Properties props = new Properties();
props.put("oracle.service.name", serviceName);
props.put("oracle.net.tns_admin", walletOrConfigDirectory);
props.put("security.protocol", securityProtocol); // PLAINTEXT or SSL

if ("SSL".equals(securityProtocol)) {
    props.put("tns.alias", tnsAlias);
} else {
    props.put("bootstrap.servers", hostAndPort);
}
```

The reference `AuthenticationExample.java` reads:

- `SECURITY_PROTOCOL`: `PLAINTEXT` or `SSL`
- `BOOTSTRAP_SERVERS`: `HOSTNAME:PORT` for PLAINTEXT
- `TNS_ADMIN`: service name/TNS alias in the sample
- `WALLET_DIR`: directory containing `ojdbc.properties` and, for SSL/mTLS, wallet files

Prefer clearer names in new code: `serviceName`, `tnsAlias`, `tnsAdminDirectory`, and `walletDirectory`.

## Local Testcontainers Properties

Using the default PDB service for Oracle AI Database Free:

```java
props.put("oracle.service.name", "freepdb1");
props.put("security.protocol", "PLAINTEXT");
props.put("bootstrap.servers", "localhost:" + port);
props.put("oracle.net.tns_admin", ojdbcPropertiesDirectory);
```

For local PLAINTEXT tests, the directory passed as `oracle.net.tns_admin` only needs an `ojdbc.properties` file with credentials:

```properties
user = testuser
password = Welcome123#
```

## Auth Notes

- Do not configure SASL; the reference sample calls out PLAINTEXT and SSL only.
- For SSL/mTLS, set `security.protocol=SSL`, pass the wallet directory as `oracle.net.tns_admin`, and set `tns.alias` to the TNS alias from `tnsnames.ora`.
- For local container tests, use `PLAINTEXT`, `bootstrap.servers=localhost:<mapped-oracle-port>`, and `oracle.service.name=freepdb1`.
- Keep secrets out of committed application config. For samples, `src/test/resources/ojdbc.properties` is acceptable only with disposable local Testcontainers credentials.
