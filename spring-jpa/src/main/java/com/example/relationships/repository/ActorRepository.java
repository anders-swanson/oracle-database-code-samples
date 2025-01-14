package com.example.relationships.repository;

import java.util.Optional;

import com.example.relationships.model.Actor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActorRepository extends JpaRepository<Actor, Long> {
    Optional<Actor> findByFirstNameAndLastName(String firstName, String lastName);
}
