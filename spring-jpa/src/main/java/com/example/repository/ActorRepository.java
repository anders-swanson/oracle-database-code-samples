package com.example.repository;

import java.util.Optional;

import com.example.model.Actor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActorRepository extends JpaRepository<Actor, Long> {
    Optional<Actor> findByFirstNameAndLastName(String firstName, String lastName);
}
