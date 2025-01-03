## Reusable Oracle Database Container(s)

This package runs two database test classes against the same Oracle Database container by utilizing [Testcontainers reuse](https://java.testcontainers.org/features/reuse/). Reusuable containers are advantageous for faster test startup time, at the cost of potential cross-contamination of test data between disparate suites.

The ReusableDatabaseTest statically configures and starts a reusable database container, which is shared by an child test classes.

In your home directory, ensure the `.testcontainers.properties` file contains the following parameter:

```
testcontainers.reuse.enable=true
```

Or set the `TESTCONTAINERS_REUSE_ENABLE=true` environment variable.
