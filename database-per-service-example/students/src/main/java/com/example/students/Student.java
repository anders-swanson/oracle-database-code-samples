package com.example.students;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "students")
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "university_id", nullable = false, unique = true)
    private String universityId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String status;

    @ElementCollection
    @CollectionTable(name = "student_completed_courses", joinColumns = @JoinColumn(name = "student_id"))
    private List<CompletedCourse> completedCourses = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUniversityId() {
        return universityId;
    }

    public void setUniversityId(String universityId) {
        this.universityId = universityId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<CompletedCourse> getCompletedCourses() {
        return completedCourses;
    }

    public void setCompletedCourses(List<CompletedCourse> completedCourses) {
        this.completedCourses = completedCourses == null ? new ArrayList<>() : new ArrayList<>(completedCourses);
    }

    public void addCompletedCourse(String courseCode) {
        boolean alreadyCompleted = completedCourses.stream()
                .anyMatch(course -> course.getCourseCode().equals(courseCode));
        if (!alreadyCompleted) {
            completedCourses.add(new CompletedCourse(courseCode));
        }
    }

    @Embeddable
    public static class CompletedCourse {
        @Column(name = "course_code", nullable = false)
        private String courseCode;

        public CompletedCourse() {
        }

        public CompletedCourse(String courseCode) {
            this.courseCode = courseCode;
        }

        public String getCourseCode() {
            return courseCode;
        }

        public void setCourseCode(String courseCode) {
            this.courseCode = courseCode;
        }
    }
}
