# Learn Spring JPA By Example with Oracle Database Free

Spring JPA (Java Persistence API) provides an abstraction layer over JPA using ORM (Object Relational Mapping). Spring JPA simplifies database interactions by abstracting common operations to simple Java objects and annotations.

This module includes idiomatic examples of [Spring JPA](https://spring.io/projects/spring-data-jpa) with [Oracle Database Free](https://medium.com/@anders.swanson.93/oracle-database-23ai-free-11abf827ab37).

### Basic JPA Example

The [com.example.relationships](./src/main/java/com/example) package defines a basic JPA entity and repository, using the [student](./src/test/resources/student.sql) schema.

The [SpringJPATest](./src/test/java/com/example/SpringJPATest.java) class provides examples of basic JPA repository usage.

### JPA Relationships

The [com.example.relationships](./src/main/java/com/example/relationships) package defines JPA entities for the [movie schema](./src/test/resources/movie.sql) with one-to-one, one-to-many, and many-to-many relationships.

The [JPARelationshipsTest](./src/test/java/com/example/JPARelationshipsTest.java) class provides examples on managing JPA relationships using repositories.
