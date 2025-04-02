package com.example.mongodb;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "students")
public class Student {
    @Id
    private String _id;

    @Field(name = "full_name")
    private String name;

    private String email;

    @Field(name = "course_count")
    private Integer courseCount;

    private Integer credits;

    private Double gpa;

    public Student() {}

    public Student(String id, String name, String email, Integer courseCount, Integer credits, Double gpa) {
        this._id = id;
        this.name = name;
        this.email = email;
        this.courseCount = courseCount;
        this.credits = credits;
        this.gpa = gpa;
    }

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getCourseCount() {
        return courseCount;
    }

    public void setCourseCount(Integer courseCount) {
        this.courseCount = courseCount;
    }

    public Integer getCredits() {
        return credits;
    }

    public void setCredits(Integer credits) {
        this.credits = credits;
    }

    public Double getGpa() {
        return gpa;
    }

    public void setGpa(Double gpa) {
        this.gpa = gpa;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof Student student)) return false;

        return get_id().equals(student.get_id()) && getEmail().equals(student.getEmail());
    }

    @Override
    public int hashCode() {
        int result = get_id().hashCode();
        result = 31 * result + getEmail().hashCode();
        return result;
    }
}
