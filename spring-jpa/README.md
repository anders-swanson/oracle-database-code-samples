# Learn Spring JPA By Example with Oracle Database Free

Spring JPA (Java Persistence API) provides an abstraction layer over JPA using ORM (Object Relational Mapping). Spring JPA simplifies database interactions by abstracting common operations to simple Java objects and annotations.

This module includes idiomatic examples of [Spring JPA](https://spring.io/projects/spring-data-jpa) with [Oracle Database Free](https://medium.com/@anders.swanson.93/oracle-database-23ai-free-11abf827ab37).

### Basic JPA Entity Example

The [com.example.relationships](./src/main/java/com/example) package defines a basic JPA entity and repository, using the [student](./src/test/resources/student.sql) schema.

The [SpringJPATest](./src/test/java/com/example/SpringJPATest.java) class provides examples of basic JPA repository usage.

### JPA Entity Relationships

The [com.example.relationships](./src/main/java/com/example/relationships) package defines JPA entities for the [movie schema](./src/test/resources/movie.sql) with one-to-one, one-to-many, and many-to-many relationships.

The [JPARelationshipsTest](./src/test/java/com/example/JPARelationshipsTest.java) class provides examples on managing JPA relationships using repositories.

### Paging, Sorting, and Filtering JPA Entities

The [com.example.paging](./src/main/java/com/example/paging) package defines JPA entities for the [author schema](./src/test/resources/paging.sql), including repositories with custom JPA methods that utilize paging, sorting, and filtering.

The [PagingSortingFilteringTest](./src/test/java/com/example/PagingSortingFilteringTest.java) class provides examples for paging, sorting, and filtering using the Author and Books repositories. Example of JPA @Query annotations, JPA query methods, and specification queries (introduced in JPA 2.0) are included.
