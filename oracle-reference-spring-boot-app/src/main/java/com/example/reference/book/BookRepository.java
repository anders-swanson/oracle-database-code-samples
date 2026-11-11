package com.example.reference.book;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {
    // A to-one fetch preserves database pagination; author collections load in bounded batches.
    @Override
    @EntityGraph(attributePaths = "publisher")
    Page<Book> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"publisher", "authors"})
    Optional<Book> findById(Long id);
}
