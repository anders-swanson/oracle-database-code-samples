package com.example.relationships.model;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "director_bio")
public class DirectorBio {

    @Id
    @Column(name = "director_id")
    private Long directorId;

    @OneToOne(fetch = FetchType.LAZY)
    // The primary key will be copied from the director entity
    @MapsId
    @JoinColumn(name = "director_id")
    private Director director;

    @Column(name = "biography", columnDefinition = "CLOB")
    private String biography;

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof DirectorBio directorBio)) return false;
        return Objects.equals(getDirectorId(), directorBio.getDirectorId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getDirectorId());
    }

    public Long getDirectorId() {
        return directorId;
    }

    public void setDirectorId(Long directorId) {
        this.directorId = directorId;
    }

    public Director getDirector() {
        return director;
    }

    public void setDirector(Director director) {
        this.director = director;
    }

    public String getBiography() {
        return biography;
    }

    public void setBiography(String biography) {
        this.biography = biography;
    }
}
