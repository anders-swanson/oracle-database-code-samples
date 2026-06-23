---
name: spring-jpa
description: Spring JPA examples for Oracle AI Database entities, repositories, and relationships.
tags:
  - Database
  - JPA
  - Java
  - SpringBoot
  - Testcontainers
blog_post: "https://andersswanson.dev/2025/06/10/learn-spring-jpa-by-example-with-oracle-database-free/"
---

# Learn Spring JPA By Example with Oracle AI Database Free

Spring JPA (Java Persistence API) provides an abstraction layer over JPA using ORM (Object Relational Mapping). Spring JPA simplifies database interactions by abstracting common operations to simple Java objects and annotations.

This module includes idiomatic examples of [Spring JPA](https://spring.io/projects/spring-data-jpa) with [Oracle AI Database Free](https://andersswanson.dev/2025/05/22/oracle-database-for-free/).

## Related Blog Posts

- [Spring JPA By Example: One-to-One, Many-to-One, and Many-to-Many](https://andersswanson.dev/2025/06/11/spring-jpa-by-example-one-to-one-many-to-one-and-many-to-many/)
- [Spring JPA: Paging, Sorting, and Filtering](https://andersswanson.dev/2025/06/11/spring-jpa-paging-sorting-and-filtering/)

### Basic JPA Entity Example

The [com.example.relationships](./src/main/java/com/example) package defines a basic JPA entity and repository, using the [student](./src/test/resources/student.sql) schema.

The [SpringJPATest](./src/test/java/com/example/SpringJPATest.java) class provides examples of basic JPA repository usage.

### JPA Entity Relationships

The [com.example.relationships](./src/main/java/com/example/relationships) package defines JPA entities for the [movie schema](./src/test/resources/movie.sql) with one-to-one, one-to-many, and many-to-many relationships.

The [JPARelationshipsTest](./src/test/java/com/example/JPARelationshipsTest.java) class provides examples on managing JPA relationships using repositories.

### Paging, Sorting, and Filtering JPA Entities

The [com.example.paging](./src/main/java/com/example/paging) package defines JPA entities for the [author schema](./src/test/resources/paging.sql), including repositories with custom JPA methods that utilize paging, sorting, and filtering.

The [PagingSortingFilteringTest](./src/test/java/com/example/PagingSortingFilteringTest.java) class provides examples for paging, sorting, and filtering using the Author and Books repositories. Example of JPA @Query annotations, JPA query methods, and specification queries (introduced in JPA 2.0) are included.

### Handling ORA Errors from JPA Operations

JPA and Spring Data JPA keep checked `SQLException` handling out of application repository code. When Oracle AI Database rejects an operation, Spring translates the JDBC failure into an unchecked data access exception. The examples in this module show two centralized ways to handle those exceptions without adding `try/catch` blocks around every repository call.

The [com.example.errors](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/spring-jpa/src/main/java/com/example/errors) package contains shared helpers that unwrap Spring, JPA, and Hibernate exceptions until they find the underlying `SQLException`. The helper reads `SQLException#getErrorCode()` and formats the value as an `ORA-XXXXX` code. Matching the numeric Oracle error code is more reliable than parsing localized exception message text.

The [StudentExceptionHandlingService](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/spring-jpa/src/main/java/com/example/exceptionhandling/StudentExceptionHandlingService.java) demonstrates the JPA-only path. Application code calls `studentRepository.saveAndFlush(student)` normally. The [OracleExceptionAspect.java](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/spring-jpa/src/main/java/com/example/exceptionhandling/OracleExceptionAspect.java.java) aspect handles translated Spring data access exceptions at the sample boundary and converts Oracle errors into a sample-specific unchecked exception.

The [StudentsController](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/spring-jpa/src/main/java/com/example/web/StudentsController.java) and [StudentExceptionHandler](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/spring-jpa/src/main/java/com/example/web/StudentExceptionHandler.java) demonstrate the REST path. A `@RestController` can let JPA exceptions bubble out of the endpoint, and a `@ControllerAdvice` handler can map the same sample exception into an HTTP response. This is the preferred shape when the application already exposes REST controllers.

The [OracleJpaExceptionHandlingTest](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/spring-jpa/src/test/java/com/example/OracleJpaExceptionHandlingTest.java) uses the existing `student.gpa` check constraint to trigger `ORA-02290` from Oracle AI Database Free and verifies both handling approaches.

#### Run the exception handler tests:

```bash
mvn test -Dtest=OracleJpaExceptionHandlingTest
```

#### How to handle errors if you're not using JPA

These techniques can work if you're using JPA or not. 

- If you're using Spring Boot `JdbcClient` or `JdbcTemplate`, you can use AOP or `@ControllerAdvice`
- If you're using JDBC directly, you may also catch and handle the SQLException in-place for ORA-type errors.

