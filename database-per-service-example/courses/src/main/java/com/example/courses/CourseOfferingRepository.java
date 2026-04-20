package com.example.courses;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {
    List<CourseOffering> findByCourseCodeAndTermCodeOrderBySectionNumberAsc(String courseCode, String termCode);
}
