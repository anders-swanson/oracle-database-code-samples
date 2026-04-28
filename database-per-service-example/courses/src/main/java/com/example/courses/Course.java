package com.example.courses;

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
@Table(name = "course_catalog")
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_code", nullable = false, unique = true)
    private String courseCode;

    @Column(nullable = false)
    private String title;

    @ElementCollection
    @CollectionTable(name = "course_prerequisites", joinColumns = @JoinColumn(name = "course_id"))
    private List<Prerequisite> prerequisites = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "course_offerings", joinColumns = @JoinColumn(name = "course_id"))
    private List<Offering> offerings = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Prerequisite> getPrerequisites() {
        return prerequisites;
    }

    public void setPrerequisites(List<Prerequisite> prerequisites) {
        this.prerequisites = prerequisites == null ? new ArrayList<>() : new ArrayList<>(prerequisites);
    }

    public List<Offering> getOfferings() {
        return offerings;
    }

    public void setOfferings(List<Offering> offerings) {
        this.offerings = offerings == null ? new ArrayList<>() : new ArrayList<>(offerings);
    }

    public void addPrerequisite(String requiredCourseCode) {
        boolean alreadyPresent = prerequisites.stream()
                .anyMatch(prerequisite -> prerequisite.getRequiredCourseCode().equals(requiredCourseCode));
        if (!alreadyPresent) {
            prerequisites.add(new Prerequisite(requiredCourseCode));
        }
    }

    public void addOffering(String termCode, Integer capacity, Integer enrolledCount) {
        offerings.removeIf(offering -> offering.getTermCode().equals(termCode));
        offerings.add(new Offering(termCode, capacity, enrolledCount));
    }

    @Embeddable
    public static class Prerequisite {
        @Column(name = "required_course_code", nullable = false)
        private String requiredCourseCode;

        public Prerequisite() {
        }

        public Prerequisite(String requiredCourseCode) {
            this.requiredCourseCode = requiredCourseCode;
        }

        public String getRequiredCourseCode() {
            return requiredCourseCode;
        }

        public void setRequiredCourseCode(String requiredCourseCode) {
            this.requiredCourseCode = requiredCourseCode;
        }
    }

    @Embeddable
    public static class Offering {
        @Column(name = "term_code", nullable = false)
        private String termCode;

        @Column(nullable = false)
        private Integer capacity;

        @Column(name = "enrolled_count", nullable = false)
        private Integer enrolledCount;

        public Offering() {
        }

        public Offering(String termCode, Integer capacity, Integer enrolledCount) {
            this.termCode = termCode;
            this.capacity = capacity;
            this.enrolledCount = enrolledCount;
        }

        public String getTermCode() {
            return termCode;
        }

        public Integer getCapacity() {
            return capacity;
        }

        public Integer getEnrolledCount() {
            return enrolledCount;
        }

        public void setTermCode(String termCode) {
            this.termCode = termCode;
        }

        public void setCapacity(Integer capacity) {
            this.capacity = capacity;
        }

        public void setEnrolledCount(Integer enrolledCount) {
            this.enrolledCount = enrolledCount;
        }
    }
}
