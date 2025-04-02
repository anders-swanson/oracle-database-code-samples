# Using Oracle Database's MongoDB API with Spring Data MongoDB

MongoDB is a popular, open-source NoSQL database management program that stores data in a flexible, JSON-like format, making it ideal for handling large, unstructured, or semi-structured data. 

The [Oracle Database MongoDB API](https://docs.oracle.com/en/database/oracle/mongodb-api/) translates the MongoDB client requests into SQL statements that are run by Oracle Database, allowing developers to easily use MongoDB clients with Oracle Database. The Oracle Database MongoDB API works best with release 22.3 or later of [Oracle REST Data Services (ORDS)](https://www.oracle.com/database/technologies/appdev/rest.html)

### What's included in this module?

This module implements a basic MongoRepository for a Student document, and demonstrates Oracle-MongoDB wire compatibility in the [OracleMongoDBTest](./src/test/java/com/example/mongodb/OracleMongoDBTest.java) test class.

Example operations for CRUD, querying, and MongoTemplate are included - each being identical to if you were _actually_ using MongoDB. 

### How can I run the test?

The test requires an Oracle Database instance with ORDS installed. The easiest way to do this is to configure an Oracle Autonomous Database for JSON processing, as described in the [Using Oracle Database API for MongoDB document](https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/mongo-using-oracle-database-api-mongodb.html#GUID-8321D7A6-9DBD-44F8-8C16-1B1FBE66AC56)

Once you have your database configured, set the following environment variables:
- `DB_USERNAME` (the database username you'll use to connect)
- `DB_PASSWORD` (the database password you'll use to connect)
- `DB_URI` (the URI of your database instance, e.g., `my-db.adb.my-region.oraclecloudapps.com`)

Then, you can run the test using Maven:

```
mvn test
```

You should see output similar to the following. I recommend reading the [test class](./src/test/java/com/example/mongodb/OracleMongoDBTest.java) to get an idea of what it's doing. Note that _no_ Oracle-specific APIs are being invoked by the client!
```bash
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.715 s -- in com.example.mongodb.OracleMongoDBTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  7.045 s
[INFO] Finished at: 2025-04-02T11:00:10-07:00
[INFO] ------------------------------------------------------------------------
```