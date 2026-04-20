package com.example.students;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentCompletedCourseRepository extends JpaRepository<StudentCompletedCourse, Long> {
    List<StudentCompletedCourse> findByStudentIdOrderByCourseCodeAscTermCodeAsc(Long studentId);
}
