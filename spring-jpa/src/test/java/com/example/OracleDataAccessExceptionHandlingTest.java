package com.example;

import java.time.Duration;

import com.example.errors.OracleDataAccessException;
import com.example.exceptionhandling.StudentExceptionHandlingService;
import com.example.model.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class OracleDataAccessExceptionHandlingTest {
    @Container
    @ServiceConnection
    static OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.26.2-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(2))
            .withUsername("testuser")
            .withPassword("testpwd")
            .withInitScript("student.sql");

    @Autowired
    StudentExceptionHandlingService studentService;

    @Autowired
    MockMvc mockMvc;

    @Test
    void jpaExceptionsHandled() {
        assertThatThrownBy(() -> studentService.createStudent(invalidGpaStudent()))
                .isInstanceOf(OracleDataAccessException.class)
                .extracting(exception -> ((OracleDataAccessException) exception).getOracleError())
                .satisfies(error -> {
                    assertThat(error.errorCode()).isEqualTo(2290); // check for your specific ORA error
                    assertThat(error.oraCode()).isEqualTo("ORA-02290");
                });
    }

    @Test
    void restControllerExceptionsHandled() throws Exception {
        mockMvc.perform(post("/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new JsonMapper().writeValueAsString(invalidGpaStudent())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("ORA-02290")))
                .andExpect(jsonPath("$.message", equalTo("Oracle AI Database rejected the JPA operation.")))
                .andExpect(jsonPath("$.detail", containsString("ORA-02290")));
    }

    private static Student invalidGpaStudent() {
        return new Student(
                "Grace",
                "Hopper",
                "grace.hopper@example.edu",
                "Computer Science",
                120,
                4.50
        );
    }
}
