# Dependencies

## OKafka

Add or verify the Oracle Kafka API client dependency:

```xml
<dependency>
    <groupId>com.oracle.database.messaging</groupId>
    <artifactId>okafka</artifactId>
    <version>${okafka.version}</version>
</dependency>
```

If the project parent or BOM already manages the version, omit the explicit `<version>` and follow the local convention.

## OSON JSON Support

For OSON JSON event payloads, add the Oracle JSON Collections starter used by the reference samples:

```xml
<dependency>
    <groupId>com.oracle.database.spring</groupId>
    <artifactId>oracle-spring-boot-starter-json-collections</artifactId>
    <version>${oracle.starters.version}</version>
</dependency>
```

In non-Spring Boot sample modules, the reference repo excludes `spring-boot-starter` from this dependency when it only needs the JSONB/OSON classes. Preserve existing dependency management and exclusions in the target project.
