# Spring Dynamic Property Source Example

This example application demonstrates how to use the Spring Boot EnumerablePropertySource and EnvironmentPostProcessor classes to load properties dynamically at application startup from an external source (in this case an Oracle Database server).

The use of dynamic property sources allows applications to manage and rotate properties from external services, such as secure key vaults, file servers, or databases for increased application configurability and security. 

For an in-depth walkthough, see the following guide: [Dynamically load Spring properties from external repositories](https://andersswanson.dev/2025/05/29/dynamically-load-spring-properties-from-external-repositories/).
. See also [Spring Cloud Config](https://docs.spring.io/spring-cloud-config/docs/current/reference/html/) for an example of externalized configuration in distributed systems.

## Prerequisites

- Java 21+, Maven

## Run the sample

The sample provides an all-in-one test leveraging Testcontainers and Oracle Database to do the following: 

1. Start and configure a database server using Testcontainers
2. Load properties from the database and verify them at application startup
3. Modify a property, and reload a bean to verify the database property refresh is working.

You can run the test like so, from the project's root directory:

`mvn test`

You should see output similar to the following, indicating properties were successfully loaded from the database, updated, and reloaded into Spring Beans:

```
Starting Property Source Test
Value of 'property1': initial value
Updating Property 'property1'
Reloading PropertyService Bean
New value of 'property1': updated
```
