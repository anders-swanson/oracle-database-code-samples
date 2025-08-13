package com.example;

import java.time.Duration;
import java.util.Optional;

import com.example.relationships.model.Actor;
import com.example.relationships.model.Director;
import com.example.relationships.model.DirectorBio;
import com.example.relationships.model.Movie;
import com.example.relationships.repository.ActorRepository;
import com.example.relationships.repository.DirectorBioRepository;
import com.example.relationships.repository.DirectorRepository;
import com.example.relationships.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
public class JPARelationshipsTest {
    @Container
    @ServiceConnection
    static OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.9-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(2))
            .withUsername("testuser")
            .withPassword("testpwd")
            .withInitScript("movie.sql");

    // Autowire JPA repositories
    @Autowired
    DirectorRepository directorRepository;
    @Autowired
    DirectorBioRepository directorBioRepository;
    @Autowired
    MovieRepository movieRepository;
    @Autowired
    ActorRepository actorRepository;

    @Test
    void oneToOneExample() {
        Optional<Director> director = directorRepository.findByFirstNameAndLastName("Christopher", "Nolan");
        assertTrue(director.isPresent());

        // Verify the biography was fetched.
        assertThat(director.get().getDirectorBio().getBiography()).isNotEmpty();

        // Create a new Director
        Director newDirector = new Director();
        newDirector.setFirstName("Steven");
        newDirector.setLastName("Spielberg");
        // Create a new DirectorBio
        DirectorBio directorBio = new DirectorBio();
        directorBio.setBiography("Steven Spielberg is an iconic American filmmaker known for blockbuster films like Jaws, E.T., and Jurassic Park.");
        // Set the relationships
        newDirector.setDirectorBio(directorBio);

        Director savedDirector = directorRepository.save(newDirector);

        // Verify the bio was added
        Optional<DirectorBio> fetchedBio = directorBioRepository.findById(savedDirector.getDirectorId());
        assertTrue(fetchedBio.isPresent());
        assertThat(fetchedBio.get().getBiography()).isEqualTo(directorBio.getBiography());

        // Delete the director, and the CASCADE effect deletes the bio
        directorRepository.delete(savedDirector);
        assertFalse(directorBioRepository.findById(savedDirector.getDirectorId()).isPresent());
    }

    @Test
    // The use of the Transactional annotation here will keep the database session open for relational queries, e.g., Movie-Actor.
    // This is useful for lazy initialization of JPA fields, so related data is fetched when-needed.
    @Transactional
    void oneToManyExample() {
        Optional<Movie> pulpFiction = movieRepository.findByTitle("Pulp Fiction");
        assertThat(pulpFiction.isPresent()).isTrue();

        // Verify the Movie-Actor many-to-many relationship
        Optional<Actor> samuelJackson = actorRepository.findByFirstNameAndLastName("Samuel", "Jackson");
        assertThat(samuelJackson.isPresent()).isTrue();
        // Samuel Jackson is credited to Pulp Fiction (Actor -> Movie view)
        assertThat(samuelJackson.get().getMovies()).contains(pulpFiction.get());
        // Pulp Fiction credits Samuel Jackson (Movie -> Actor view)
        assertThat(pulpFiction.get().getActors()).contains(samuelJackson.get());

        // Verify the Director-Movie many-to-one relationship
        Director pulpFictionDirector = pulpFiction.get().getDirector();
        // Pulp Fiction credits Quentin Tarantino as the director.
        assertThat(pulpFictionDirector).isNotNull();
        assertThat(pulpFictionDirector.getFirstName()).isEqualTo("Quentin");
        // Quentin Tarantino lists Pulp Fiction as a directed movie.
        assertThat(pulpFictionDirector.getMovies()).contains(pulpFiction.get());
    }

    @Test
    // For lazy loading sessions, as in oneToManyExample
    @Transactional
    void manyToManyExample() {
        Optional<Movie> pulpFiction = movieRepository.findByTitle("Pulp Fiction");
        assertThat(pulpFiction.isPresent()).isTrue();

        // Create a new actor.
        Actor newActor = new Actor();
        newActor.setFirstName("Uma");
        newActor.setLastName("Thurman");
        newActor.addMovie(pulpFiction.get());
        // The Movie-Actor relationship is implicitly created by Spring JPA
        actorRepository.save(newActor);

        // Verify Pulp Fiction is in Uma Thurman's credits.
        Optional<Actor> umaThurman = actorRepository.findByFirstNameAndLastName("Uma", "Thurman");
        assertTrue(umaThurman.isPresent());
        assertThat(umaThurman.get().getMovies()).contains(pulpFiction.get());

        // What about update? The Movie-Actor relationship is implicitly inserted on update.
        Optional<Movie> killBillVol1 = movieRepository.findByTitle("Kill Bill: Vol. 1");
        assertThat(killBillVol1.isPresent()).isTrue();
        umaThurman.get().addMovie(killBillVol1.get());
        Actor updated = actorRepository.save(umaThurman.get());
        assertThat(updated.getMovies()).contains(killBillVol1.get());
    }
}
