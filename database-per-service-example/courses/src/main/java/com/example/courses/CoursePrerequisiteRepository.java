package com.example.courses;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CoursePrerequisiteRepository extends JpaRepository<CoursePrerequisite, Long> {
    List<CoursePrerequisite> findByCourseCodeOrderByRequiredCourseCodeAsc(String courseCode);
}
