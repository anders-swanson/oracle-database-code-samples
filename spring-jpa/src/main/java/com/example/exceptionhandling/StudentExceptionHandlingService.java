package com.example.exceptionhandling;

import com.example.model.Student;
import com.example.repository.StudentRepository;
import org.springframework.stereotype.Service;

/**
 * Wrapped by {@link OracleExceptionAspect} to handle exceptions with ORA-type errors.
 */
@Service
public class StudentExceptionHandlingService {
    private final StudentRepository studentRepository;

    public StudentExceptionHandlingService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public Student createStudent(Student student) {
        return studentRepository.saveAndFlush(student);
    }
}
