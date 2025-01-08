# Spring Resource Loader Example

This example demonstrates how to implement a custom [Spring Resource Loader](https://docs.spring.io/spring-framework/reference/core/resources.html#resources-resourceloader) to idiomatically load blob resources (such as images or files) at application startup from an arbitrary storage backend.

In this example our storage backend is an Oracle Database table using a BLOB column, but the design applies to any storage backend with a programmable API.

- DatabaseResource.java defines custom Resource type extending AbstractResource
- DatabaseResourceResolver.java implements the Spring ResourceLoaderAware and ProtocolResolver interfaces to provide a Spring Compnent capable of instantiating DatabaseResorce objects.
- DatabaseLocation.java is a data class that maps a Resource location to a BLOB in the database.

## Prerequisites

- Java 21+, Maven

## Run the sample

The sample provides an all-in-one test leveraging Testcontainers and Oracle Database to do the following: 

1. Start and configure a database server using Testcontainers
2. Load Spring Resources from the database
3. Verify the resources are loaded and accessible

You can run the test like so, from the project's root directory:

`mvn test`

