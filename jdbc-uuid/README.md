---
name: jdbc-uuid
description: Plain JDBC sample that stores Java UUID primary keys as RAW(16) values in Oracle AI Database.
tags:
  - Database
  - Java
  - JDBC
  - Testcontainers
  - UUID
blog_post: ""
---

# JDBC UUID Primary Keys

This sample shows the smallest useful pattern for storing Java `UUID` primary keys in Oracle AI Database with plain JDBC.

The table uses `RAW(16)` for the primary key. The Java code converts each `UUID` to 16 bytes by writing `getMostSignificantBits()` followed by `getLeastSignificantBits()`, binds those bytes with `PreparedStatement#setBytes(...)`, and reconstructs the same `UUID` after reading the `RAW` value with `ResultSet#getBytes(...)`.

## What the sample proves

- Java `UUID` values can be stored compactly as `RAW(16)` primary keys.
- The conversion is explicit and bit-preserving.
- JDBC can bind and read the value as bytes without string formatting.
- A Testcontainers integration test verifies the stored `RAW` value is exactly 16 bytes and round-trips to the original `UUID`.

## Run the test

From the repository root:

```bash
mvn -pl jdbc-uuid -am test
```

The test starts Oracle AI Database Free with Testcontainers, runs the sample, checks lookup by UUID, and verifies the stored primary key bytes.

## Run the sample app

Against a local Oracle AI Database instance:

```bash
mvn -pl jdbc-uuid compile exec:java -Dexec.args="jdbc:oracle:thin:@localhost:1521/freepdb1 testuser testpwd"
```

You should see output similar to:

```text
Stored Java UUID primary keys as RAW(16):
2f4b6f9a-1d7e-4c6b-8d4a-2c8e5f9b0a11 | bytes=2F4B6F9A1D7E4C6B8D4A2C8E5F9B0A11 | order=ORD-1001 | customer=Avery Stone | total=42.50
6c2f4a91-b03d-469d-ae13-0c0d73513a4e | bytes=6C2F4A91B03D469DAE130C0D73513A4E | order=ORD-1002 | customer=Mina Rao | total=125.00
```

## Core conversion

```java
static byte[] uuidToBytes(UUID uuid) {
    return ByteBuffer.allocate(16)
            .putLong(uuid.getMostSignificantBits())
            .putLong(uuid.getLeastSignificantBits())
            .array();
}

static UUID bytesToUuid(byte[] bytes) {
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    return new UUID(buffer.getLong(), buffer.getLong());
}
```

Keep the byte order consistent in both directions. The sample uses the same order Java exposes from `UUID`: most-significant bits first, least-significant bits second.
