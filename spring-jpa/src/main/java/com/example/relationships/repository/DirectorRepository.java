package com.example.relationships.repository;

import java.util.Optional;

import com.example.relationships.model.Director;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectorRepository extends JpaRepository<Director, Long> {
    Optional<Director> findByFirstNameAndLastName(String firstName, String lastName);
}
