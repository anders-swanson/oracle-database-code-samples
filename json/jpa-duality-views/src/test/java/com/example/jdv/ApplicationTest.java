package com.example.jdv;

import com.example.jdv.controller.JDVController;
import com.example.jdv.movie.Actor;
import com.example.jdv.movie.Director;
import com.example.jdv.movie.Movie;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import org.eclipse.yasson.YassonJsonb;
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
    // docker pull gvenzl/oracle-free:23.26.2-slim-faststart
    @Container
    @ServiceConnection
    private static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.26.2-slim-faststart")
            .withUsername("testuser")
            .withPassword("testpwd");

    @Autowired
    private JDVController jdvController;

    @Test
    public void createMovieActorJDV() {
        Director director = new Director();
        director.setDirectorId(1L); // so the same director is used
        director.setFirstName("Tim");
        director.setLastName("Smith");

        Movie movie1 = new Movie();
        movie1.setTitle("my movie");
        movie1.setGenre("action");
        movie1.setReleaseYear(1993);
        movie1.setDirector(director);

        Movie movie2 = new Movie();
        movie2.setTitle("my movie2");
        movie2.setGenre("thriller");
        movie2.setReleaseYear(1994);
        movie2.setDirector(director);

        Actor actor = new Actor();
        actor.setFirstName("John");
        actor.setLastName("Doe");
        actor.setMovies(Set.of(movie1, movie2));

        Actor actorCreated = jdvController.createActor(actor);
        Set<Movie> movies = actorCreated.getMovies();
        assertThat(movies).hasSize(2);
        Movie actorMovie = movies.stream().filter(m -> m.getTitle().equals(movie1.getTitle())).findFirst().get();
        assertThat(actorMovie.getTitle()).isEqualTo(movie1.getTitle());
        assertThat(actorMovie.getDirector().getFirstName()).isEqualTo(movie1.getDirector().getFirstName());

        YassonJsonb yassonJsonb = (YassonJsonb) JsonbBuilder.newBuilder()
                .withConfig(new JsonbConfig().withFormatting(true))
                .build();
        String actorString = yassonJsonb.toJson(actorCreated);
        System.out.println("created actor: \n" + actorString);
    }
}
