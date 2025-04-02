package com.example.mongodb;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface StudentRepository extends MongoRepository<Student, String> {
    Optional<Student> findByEmail(String email);

    @Query("{ 'course_count': { $gte: ?0, $lte: ?1 } }")
    List<Student> findByCourseCountInRange(int minCourses, int maxCourses);

    @Query(value = "{ credits: { $gte:  ?0 } }", fields = "{ 'email': 1 }")
    List<Student> findStudentEmailsWhereCreditsGreaterThan(int minCredits);
}
