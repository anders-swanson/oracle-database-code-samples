package com.example;

import java.time.Duration;
import java.util.Optional;

import com.example.model.Student;
import com.example.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
public class SpringJPATest {
    @Container
    @ServiceConnection
    static OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.8-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(2))
            .withUsername("testuser")
            .withPassword("testpwd")
            .withInitScript("student.sql");

    // Autowire JPA repositories
    @Autowired
    StudentRepository studentRepository;

    @Test
    void crudExample() {
        Student s = new Student();
        s.setFirstName("John");
        s.setLastName("Doe");
        s.setCredits(60);
        s.setMajor("Computer Science");
        s.setEmail("john.doe@example.edu");
        s.setGpa(3.77);

        // Create a new student using the student repository.
        Student saved = studentRepository.save(s);
        assertThat(saved.getId()).isNotNull();

        // Update the student credits and GPA.
        saved.setCredits(64);
        saved.setGpa(3.79);
        studentRepository.save(saved);
        studentRepository.flush();

        // Verify the student was updated successfully.
        Optional<Student> byId = studentRepository.findById(saved.getId());
        assertTrue(byId.isPresent());
        assertThat(byId.get().getCredits()).isEqualTo(64);
        assertThat(byId.get().getGpa()).isEqualTo(3.79);

        studentRepository.deleteById(saved.getId());
        assertFalse(studentRepository.findById(saved.getId()).isPresent());
    }
}
