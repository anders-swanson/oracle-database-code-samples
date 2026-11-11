package com.example.reference.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "authors")
public class Author {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 800)
    private String biography;

    protected Author() {
    }

    Author(String name, String biography) {
        this.name = name;
        this.biography = biography;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getBiography() { return biography; }
}
