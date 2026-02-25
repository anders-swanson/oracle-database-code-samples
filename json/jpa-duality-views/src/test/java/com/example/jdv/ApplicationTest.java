package com.example.jdv;

import com.example.jdv.controller.JDVController;
import com.example.jdv.movie.Actor;
import com.example.jdv.movie.Director;
import com.example.jdv.movie.Movie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.oracle.OracleContainer;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class ApplicationTest {
    // Pre-pull this image to avoid testcontainers image pull timeouts:
    // docker pull gvenzl/oracle-free:23.26.1-slim-faststart
    @Container
    @ServiceConnection
    private static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.26.1-slim-faststart")
            .withUsername("testuser")
            .withPassword("testpwd");

    @Autowired
    private JDVController jdvController;

    @Test
    public void createMovieActorJDV() {
        Director director = new Director();
        director.setFirstName("John");
        director.setLastName("Doe");

        Movie movie = new Movie();
        movie.setTitle("my movie");
        movie.setGenre("action");
        movie.setReleaseYear(1993);
        movie.setDirector(director);

        Actor actor = new Actor();
        actor.setFirstName("John");
        actor.setLastName("Doe");
        actor.setMovies(Set.of(movie));

        Actor actorCreated = jdvController.createActor(actor);
        Set<Movie> movies = actorCreated.getMovies();
        assertThat(movies).hasSize(1);
        Movie actorMovie = movies.iterator().next();
        assertThat(actorMovie.getTitle()).isEqualTo(movie.getTitle());
        assertThat(actorMovie.getDirector().getFirstName()).isEqualTo(movie.getDirector().getFirstName());
    }
}
