package com.example.sample;

import java.io.IOException;
import java.util.List;

import com.example.courses.Course;

final class CoursesClient {
    private final String baseUrl;

    CoursesClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    Course createCourse(String courseCode, String title) throws IOException, InterruptedException {
        Course course = new Course();
        course.setCourseCode(courseCode);
        course.setTitle(title);
        course.setPrerequisites(List.of());
        course.setOfferings(List.of());
        return HttpUtil.post(
                baseUrl + "/courses",
                course,
                Course.class
        );
    }

    Course createCourse(DatabasePerServiceSampleRunner.Scenario scenario) throws IOException, InterruptedException {
        Course course = new Course();
        course.setCourseCode(scenario.targetCourseCode());
        course.setTitle(scenario.targetCourseTitle());
        course.setPrerequisites(List.of(new Course.Prerequisite(scenario.prerequisiteCourseCode())));
        course.setOfferings(List.of(new Course.Offering(
                scenario.termCode(),
                scenario.capacity(),
                scenario.enrolledCount()
        )));
        return HttpUtil.post(
                baseUrl + "/courses",
                course,
                Course.class
        );
    }

    Course getCourse(String courseCode) throws IOException, InterruptedException {
        return HttpUtil.get(baseUrl + "/courses/" + courseCode, Course.class);
    }

}
