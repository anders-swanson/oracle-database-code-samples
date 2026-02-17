package com.example.jdv.movie;

import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.*;

import java.util.Objects;

import static com.oracle.spring.json.duality.builder.Annotations._ID_FIELD;

@Entity
@Table(name = "director_bio")
public class DirectorBio {
    @JsonbProperty(_ID_FIELD)
    @Id
    @Column(name = "director_id")
    private String directorId;

    @OneToOne(fetch = FetchType.LAZY)
    // The primary key will be copied from the director entity
    @MapsId
    @JoinColumn(name = "director_id")
    @JsonbTransient
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

    public String getDirectorId() {
        return directorId;
    }

    public void setDirectorId(String directorId) {
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
