package com.example.sample;

import java.io.IOException;

import com.example.students.Student;

final class StudentsClient {
    private final String baseUrl;

    StudentsClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    Student createStudent(DatabasePerServiceSampleRunner.Scenario scenario) throws IOException, InterruptedException {
        Student student = new Student();
        student.setUniversityId(scenario.universityId());
        student.setFirstName(scenario.firstName());
        student.setLastName(scenario.lastName());
        student.setStatus(scenario.status());
        scenario.completedCourseCodes().forEach(student::addCompletedCourse);
        return HttpUtil.post(
                baseUrl + "/students",
                student,
                Student.class
        );
    }

    Student getStudent(Long studentId) throws IOException, InterruptedException {
        return HttpUtil.get(baseUrl + "/students/" + studentId, Student.class);
    }
}
