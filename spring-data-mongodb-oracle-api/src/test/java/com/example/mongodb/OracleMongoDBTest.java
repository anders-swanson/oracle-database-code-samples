package com.example.mongodb;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;

// The DB_USERNAME, DB_PASSWORD, and DB_URI environment variables must be set
// to run this test.
@EnabledIfEnvironmentVariables(value = {
        @EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+"),
        @EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+"),
        @EnabledIfEnvironmentVariable(named = "DB_URI", matches = ".+")
})
@SpringBootTest
public class OracleMongoDBTest {
    @Autowired
    StudentRepository studentRepository;

    @Autowired
    MongoTemplate mongoTemplate;

    Student aliceSmith = new Student(
            UUID.randomUUID().toString(),
            "Alice Smith",
            "alice.smith@example.edu",
            10,
            45,
            3.77
    );

    Student johnDoe = new Student(
            UUID.randomUUID().toString(),
            "John Doe",
            "john.doe@example.edu",
            6,
            24,
            3.23
    );

    @Test
    void basicCRUDOperations() {
        // Simple CRUD repository actions work with Oracle Database.
        // Create a new student
        Student saved = studentRepository.save(aliceSmith);

        // Verify student is present and matches client student object.
        Optional<Student> byId = studentRepository.findById(saved.get_id());
        assertThat(byId.isPresent()).isTrue();
        assertThat(byId.get()).isEqualTo(aliceSmith);

        // Update the student and verify updated values.
        aliceSmith.setCredits(50);
        aliceSmith.setGpa(3.79);
        saved = studentRepository.save(aliceSmith);
        assertThat(saved.getCredits()).isEqualTo(50);
        assertThat(saved.getGpa()).isEqualTo(3.79);

        // Delete student and verify no longer present.
        studentRepository.deleteById(saved.get_id());
        byId = studentRepository.findById(saved.get_id());
        assertThat(byId.isPresent()).isFalse();
    }

    @Test
    void queryOperations() {
        // Repository query options also work with Oracle Database.
        studentRepository.save(aliceSmith);
        studentRepository.save(johnDoe);

        // Query by email address.
        assertThat(studentRepository.findByEmail("john.doe@example.edu")
                .isPresent())
                .isTrue();

        // Query by course count.
        List<Student> byCourseCountInRange = studentRepository.findByCourseCountInRange(8, 12);
        assertThat(byCourseCountInRange.size()).isEqualTo(1);
        assertThat(byCourseCountInRange.getFirst()).isEqualTo(aliceSmith);

        // Query students, only returning the email.
        List<Student> studentEmails = studentRepository.findStudentEmailsWhereCreditsGreaterThan(0);
        assertThat(studentEmails).hasSize(2);
        studentEmails.forEach(student -> {
            assertThat(student.getName()).isNull();
            assertThat(student.getEmail()).isNotNull();
        });
    }

    @Test
    void mongoTemplateOperations() {
        // You may also use MongoTemplate to work with documents in Oracle Database.
        mongoTemplate.save(aliceSmith);
        mongoTemplate.save(johnDoe);

        assertThat(mongoTemplate.findAll(Student.class)).hasSize(2);
    }

    @AfterEach
    void cleanup() {
        studentRepository.deleteAll();
    }
}
