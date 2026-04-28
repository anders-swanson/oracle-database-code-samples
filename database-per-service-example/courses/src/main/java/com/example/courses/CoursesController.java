package com.example.courses;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CoursesController {
    private final CourseRepository courseRepository;

    public CoursesController(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public Course createCourse(@RequestBody Course course) {
        course.setId(null);
        return courseRepository.save(course);
    }

    @GetMapping("/courses/{courseCode}")
    public Course getCourse(@PathVariable String courseCode) {
        return requireCourse(courseCode);
    }

    private Course requireCourse(String courseCode) {
        return courseRepository.findByCourseCode(courseCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course %s not found".formatted(courseCode)));
    }
}
